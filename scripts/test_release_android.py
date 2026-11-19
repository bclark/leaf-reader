"""Offline workflow tests. No real Gradle, signing, Play, upload or rollout calls."""
import argparse
from contextlib import redirect_stdout
import datetime as dt
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import release_android as release


CERT = 'AB' * 32
DB = 'app/src/main/java/net/digitalbc/leafreader/data/db/AppDatabase.kt'


class AndroidReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.output = self.root / 'app/build/play-releases'
        self.patches = [patch.object(release, 'ROOT', self.root), patch.object(release, 'OUTPUT', self.output)]
        for item in self.patches:
            item.start()
        self.capture = redirect_stdout(io.StringIO())
        self.capture.__enter__()
        self.state = {'package': release.PACKAGE, 'checked_at': dt.datetime.now(dt.timezone.utc).isoformat(),
                      'all_tracks_and_drafts_checked': True, 'max_version_code': 30700,
                      'track': 'Alpha', 'track_kind': 'closed', 'upload_certificate_sha256': CERT,
                      'evidence': 'Offline fixture, not actual Console evidence'}
        self.state_path = self.file('play-state.json', json.dumps(self.state))

    def tearDown(self):
        self.capture.__exit__(None, None, None)
        for item in reversed(self.patches):
            item.stop()
        self.temp.cleanup()

    def file(self, name, text='source'):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def repo(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.file('app/build.gradle.kts', 'android {\n versionName = "0.3.7"\n versionCode = 30700\n}\n')
        self.file(DB, '@Database(version = 6)\nrevision INTEGER\n')
        self.file('shared/src/Core.kt')
        self.file('gradlew')
        self.file('.gitignore', 'app/build/\n.env\nlocal.properties\n')
        subprocess.run(['git', 'add', 'app', 'shared', 'gradlew', '.gitignore'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-qm', 'source'], cwd=self.root, check=True)

    def prepared(self, **changes):
        args = dict(version='0.3.8', version_code=30800, since='HEAD', play_state=str(self.state_path), allow_dirty=False)
        args.update(changes)
        return release.prepare(argparse.Namespace(**args))

    def xml(self, name, skipped=False):
        skip = '<skipped/>' if skipped else ''
        method = 'configuredShareListsDownloadsAndCancelsWithoutPublishingPartialFiles' if name == 'SmbLiveTest' else 'works'
        return f'<testsuite name="net.example.{name}" tests="1" failures="0" errors="0"><testcase classname="net.example.{name}" name="{method}">{skip}</testcase></testsuite>'

    def manifest_xml(self, **changes):
        attrs = {'package': release.PACKAGE, 'versionName': '0.3.8', 'versionCode': '30800'}
        attrs.update(changes)
        return f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{attrs["package"]}" android:versionName="{attrs["versionName"]}" android:versionCode="{attrs["versionCode"]}"><application android:debuggable="false"/></manifest>'

    def setup_build(self, directory, signed=True):
        self.file('sdk/build-tools/36.1.0/aapt2')
        self.file('sdk/build-tools/36.1.0/apksigner')
        self.file('bundletool.jar')
        self.file('upload.jks')
        return argparse.Namespace(directory=str(directory), android_sdk=str(self.root / 'sdk'),
                                  bundletool=str(self.root / 'bundletool.jar'), signed=signed, offline=True)

    def build_fake(self, args, cwd=None, log=None, env=None):
        args = list(map(str, args))
        if args[0] == './gradlew':
            self.assertIn('--rerun-tasks', args)
            self.assertIn('--no-build-cache', args)
            self.assertEqual(env['LEAF_SMB_LIVE_TEST'], '0')
            if ':shared:jvmTest' in args:
                path = Path(cwd) / 'shared/build/test-results/jvmTest/TEST-Core.xml'
                path.parent.mkdir(parents=True)
                path.write_text(self.xml('CoreTest'))
            elif ':app:testFullDebugUnitTest' in args:
                for flavor in ('Full', 'Fdroid'):
                    for suite in ('AppDatabaseMigrationTest', 'BackupManagerTest', 'LibraryRepositoryJellyfinTest', 'SmbLiveTest'):
                        path = Path(cwd) / f'app/build/test-results/test{flavor}DebugUnitTest/TEST-{suite}.xml'
                        path.parent.mkdir(parents=True, exist_ok=True)
                        path.write_text(self.xml(suite, suite == 'SmbLiveTest'))
            else:
                flavors = ['full'] if ':app:bundleFullRelease' in args else ['fdroid']
                if flavors == ['fdroid']:
                    self.assertFalse(any(key in env for key in release.SIGNING))
                output = Path(cwd) / 'app/build/outputs'
                for flavor in flavors:
                    apk = output / f'apk/{flavor}/release/app.apk'
                    apk.parent.mkdir(parents=True, exist_ok=True)
                    apk.write_bytes(b'apk')
                    mapping = output / f'mapping/{flavor}Release/mapping.txt'
                    mapping.parent.mkdir(parents=True, exist_ok=True)
                    mapping.write_text('R8 mapping')
                if flavors == ['full']:
                    aab = output / 'bundle/fullRelease/app-full-release.aab'
                    aab.parent.mkdir(parents=True)
                    with zipfile.ZipFile(aab, 'w') as archive:
                        archive.writestr('base/manifest/AndroidManifest.xml', b'protobuf fixture')
        if 'manifest' in args:
            return self.manifest_xml()
        if args[0] == 'jarsigner':
            return 'jar verified.'
        if args[0] == 'keytool':
            return 'SHA256: ' + CERT
        if 'badging' in args:
            return f"package: name='{release.PACKAGE}' versionCode='30800' versionName='0.3.8'\n"
        if args[0].endswith('apksigner'):
            return 'Signer #1 certificate SHA-256 digest: ' + CERT
        return ''

    def built(self, signed=True):
        self.repo()
        directory = self.prepared()
        args = self.setup_build(directory, signed)
        env = {'KEYSTORE_FILE': str(self.root / 'upload.jks'), 'KEYSTORE_PASSWORD': 'test',
               'KEY_ALIAS': 'test', 'KEY_PASSWORD': 'test'}
        with patch.dict('os.environ', env), patch.object(release, 'run', side_effect=self.build_fake):
            release.build(args)
        return directory, release.load_release(directory)[1]

    def ready(self):
        directory, manifest = self.built()
        (directory / 'notes/en-US.txt').write_text('A little polish for your next chapter.\n- Keep your reading list close at hand.')
        release.save(directory / 'acceptance.json', {'aab_sha256': manifest['artifacts']['aab']['sha256'],
            'gates': {gate: {'status': 'pass', 'evidence': 'Offline test fixture only'} for gate in release.GATES}})
        return directory, manifest

    def handoff_args(self, directory):
        return argparse.Namespace(directory=str(directory), play_state=str(self.state_path))

    def test_reject_invalid_versions_and_codes(self):
        for version, code in (('../x', 30800), ('0.3', 30800), ('0.3.8-beta', 30800), ('0.3.8', 0), ('0.3.8', 2100000001)):
            with self.assertRaises(RuntimeError):
                release.validate_version(version, code)

    def test_reject_source_or_play_code_collision(self):
        for code in (307, 30700, 30600):
            with self.assertRaises(RuntimeError):
                release.check_number('0.3.8', code, ('0.3.7', 30700), self.state)
        self.state['max_version_code'] = 30800
        with self.assertRaises(RuntimeError):
            release.check_number('0.3.8', 30800, ('0.3.7', 30700), self.state)

    def test_reject_non_increasing_version(self):
        with self.assertRaises(RuntimeError):
            release.check_number('0.3.7', 30800, ('0.3.7', 30700), self.state)

    def test_play_state_fails_closed(self):
        for key, value in (('package', 'other.app'), ('all_tracks_and_drafts_checked', False),
                           ('max_version_code', '30700'), ('track_kind', 'beta'), ('evidence', ''),
                           ('upload_certificate_sha256', 'password'), ('checked_at', '2020-01-01T00:00:00+00:00'),
                           ('checked_at', '2100-01-01T00:00:00+00:00'), ('checked_at', '2026-11-10T01:00:00')):
            self.state_path.write_text(json.dumps(dict(self.state, **{key: value})))
            with self.assertRaises(RuntimeError, msg=key):
                release.play_state(self.state_path)

    def test_snapshot_includes_required_android_tooling_modules(self):
        self.repo()
        self.file('settings.gradle.kts', 'include(":app", ":shared", ":r8smoke", ":macrobenchmark")\n')
        for module in ('r8smoke', 'macrobenchmark'):
            self.file(f'{module}/build.gradle.kts', 'plugins {}\n')
            self.file(f'{module}/src/main/AndroidManifest.xml', '<manifest/>\n')
        subprocess.run(['git', 'add', 'settings.gradle.kts', 'r8smoke', 'macrobenchmark'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-qm', 'Android tooling modules'], cwd=self.root, check=True)
        directory = self.prepared()
        manifest = release.load_release(directory)[1]
        for module in ('r8smoke', 'macrobenchmark'):
            for name in ('build.gradle.kts', 'src/main/AndroidManifest.xml'):
                relative = f'{module}/{name}'
                self.assertEqual((directory / 'source' / relative).read_bytes(), (self.root / relative).read_bytes())
                self.assertIn(relative, manifest['source_hashes'])

    def test_snapshot_versions_only_change_in_candidate(self):
        self.repo()
        before = (self.root / 'app/build.gradle.kts').read_bytes()
        directory = self.prepared()
        self.assertEqual((self.root / 'app/build.gradle.kts').read_bytes(), before)
        _, manifest = release.load_release(directory)
        self.assertEqual(release.configured_version((directory / 'source/app/build.gradle.kts').read_text()), ('0.3.8', 30800))
        release.check_source(directory, manifest)

    def test_no_private_configuration_copied(self):
        self.repo()
        self.file('.env', 'do not read')
        self.file('local.properties', 'sdk.dir=private')
        directory = self.prepared()
        self.assertFalse((directory / 'source/.env').exists())
        self.assertFalse((directory / 'source/local.properties').exists())

    def test_source_credential_store_is_copied_but_secret_files_are_rejected(self):
        self.repo()
        name = 'app/src/main/java/net/digitalbc/leafreader/data/jellyfin/JellyfinCredentialStore.kt'
        self.file(name, 'class JellyfinCredentialStore')
        subprocess.run(['git', 'add', name], cwd=self.root, check=True)
        directory = self.prepared(allow_dirty=True)
        self.assertEqual((directory / 'source' / name).read_text(), 'class JellyfinCredentialStore')
        for secret in ('app/credentials.json', 'app/google-credentials.json', 'app/service-account.json', 'app/upload.jks'):
            self.file(secret, 'private fixture')
            subprocess.run(['git', 'add', secret], cwd=self.root, check=True)
            with self.assertRaisesRegex(RuntimeError, 'Private configuration'):
                self.prepared(allow_dirty=True)
            subprocess.run(['git', 'rm', '-f', secret], cwd=self.root, check=True, stdout=subprocess.DEVNULL)

    def test_dirty_requires_opt_in_and_preserves_edits(self):
        self.repo()
        self.file('shared/src/Core.kt', 'approved change')
        with self.assertRaisesRegex(RuntimeError, 'tracked changes'):
            self.prepared()
        directory = self.prepared(allow_dirty=True)
        self.assertEqual((directory / 'source/shared/src/Core.kt').read_text(), 'approved change')

    def test_untracked_build_input_rejected(self):
        self.repo()
        self.file('app/src/New.kt')
        with self.assertRaisesRegex(RuntimeError, 'untracked build inputs'):
            self.prepared()

    def test_fresh_attempt_preserves_previous_candidate(self):
        self.repo()
        first = self.prepared()
        before = (first / 'release.json').read_bytes()
        second = self.prepared()
        self.assertNotEqual(first, second)
        self.assertEqual((first / 'release.json').read_bytes(), before)

    def test_source_change_and_injected_source_rejected(self):
        self.repo()
        directory = self.prepared()
        _, manifest = release.load_release(directory)
        extra = directory / 'source/shared/Injected.kt'
        extra.write_text('bad')
        with self.assertRaisesRegex(RuntimeError, 'unexpected source'):
            release.check_source(directory, manifest)
        extra.unlink()
        (directory / 'source/shared/src/Core.kt').write_text('changed')
        with self.assertRaisesRegex(RuntimeError, 'source changed'):
            release.check_source(directory, manifest)

    def test_known_v5_migration_blocks_build_before_commands(self):
        self.repo()
        self.file(DB, '@Database(version = 5)\nrevision INTEGER\n')
        directory = self.prepared(allow_dirty=True)
        args = self.setup_build(directory)
        with patch.object(release, 'run') as runner:
            with self.assertRaisesRegex(RuntimeError, 'schema-5'):
                release.build(args)
            runner.assert_not_called()

    def test_tests_require_reports_and_key_suites(self):
        with self.assertRaises(RuntimeError):
            release.test_summary(self.root / 'absent')
        self.file('results/TEST-Core.xml', self.xml('CoreTest'))
        with self.assertRaisesRegex(RuntimeError, 'Missing mandatory'):
            release.test_summary(self.root / 'results', ('BackupManagerTest',))

    def test_unexpected_skips_and_failures_block(self):
        path = self.file('results/TEST-Core.xml', self.xml('CoreTest', True))
        with self.assertRaisesRegex(RuntimeError, 'Unexpected skipped'):
            release.test_summary(path.parent)
        path.write_text(self.xml('CoreTest').replace('failures="0"', 'failures="1"'))
        with self.assertRaisesRegex(RuntimeError, 'Failed'):
            release.test_summary(path.parent)

    def test_expected_smb_skip_is_explicit(self):
        path = self.file('results/TEST-Smb.xml', self.xml('SmbLiveTest', True))
        self.assertEqual(len(release.test_summary(path.parent)['skipped']), 1)

    def test_bundle_manifest_rejects_wrong_identity(self):
        manifest = {'version': '0.3.8', 'version_code': 30800}
        release.validate_manifest(self.manifest_xml(), manifest)
        for changes in ({'package': 'bad.app'}, {'versionCode': '30700'}, {'versionName': '0.3.7'}):
            with self.assertRaises(RuntimeError):
                release.validate_manifest(self.manifest_xml(**changes), manifest)
        with self.assertRaises(RuntimeError):
            release.validate_manifest(self.manifest_xml().replace('debuggable="false"', 'debuggable="true"'), manifest)

    def test_mocked_full_signed_build_retains_evidence(self):
        directory, manifest = self.built()
        self.assertEqual(manifest['status'], 'built-signed')
        self.assertEqual(set(manifest['tests']), {'shared', 'Full', 'Fdroid'})
        self.assertEqual(len(manifest['artifacts']), 5)
        self.assertEqual(manifest['inspection']['upload_certificate_sha256'], CERT)
        self.assertFalse((directory / 'upload-preview.json').exists())

    def test_unsigned_build_cannot_handoff(self):
        directory, manifest = self.built(False)
        self.assertEqual(manifest['status'], 'built-unsigned')
        with self.assertRaisesRegex(RuntimeError, 'Only a signed'):
            release.handoff(self.handoff_args(directory))

    def test_failed_build_remains_non_retryable(self):
        self.repo()
        directory = self.prepared()
        args = self.setup_build(directory, False)
        with patch.object(release, 'run', side_effect=RuntimeError('build failed')):
            with self.assertRaisesRegex(RuntimeError, 'build failed'):
                release.build(args)
        self.assertEqual(release.load_release(directory)[1]['status'], 'building')
        with self.assertRaisesRegex(RuntimeError, 'Build already started'):
            release.build(args)

    def test_incomplete_signing_environment_blocks(self):
        self.repo()
        directory = self.prepared()
        args = self.setup_build(directory)
        with patch.dict('os.environ', {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, 'Existing signing'):
                release.build(args)

    def test_notes_length_placeholder_and_empty_block(self):
        self.repo()
        directory = self.prepared()
        path = directory / 'notes/en-US.txt'
        for text in ('', 'DRAFT: review', 'x' * 501, 'TODO later'):
            path.write_text(text)
            with self.assertRaises(RuntimeError):
                release.notes(directory)
        path.write_text('x' * 500)
        self.assertEqual(len(release.notes(directory)['en-US']), 500)

    def test_handoff_requires_exact_candidate_acceptance(self):
        directory, _ = self.built()
        with self.assertRaisesRegex(RuntimeError, 'exact AAB'):
            release.handoff(self.handoff_args(directory))

    def test_handoff_all_gates_mandatory(self):
        directory, _ = self.ready()
        acceptance = json.loads((directory / 'acceptance.json').read_text())
        acceptance['gates']['upgrade_pre_revision_v5']['status'] = 'pending'
        release.save(directory / 'acceptance.json', acceptance)
        with self.assertRaisesRegex(RuntimeError, 'upgrade_pre_revision_v5'):
            release.handoff(self.handoff_args(directory))

    def test_handoff_rechecks_live_code_floor(self):
        directory, _ = self.ready()
        self.state['max_version_code'] = 30800
        self.state_path.write_text(json.dumps(self.state))
        with self.assertRaisesRegex(RuntimeError, 'already consumed'):
            release.handoff(self.handoff_args(directory))

    def test_handoff_rejects_certificate_change(self):
        directory, _ = self.ready()
        self.state['upload_certificate_sha256'] = 'CD' * 32
        self.state_path.write_text(json.dumps(self.state))
        with self.assertRaisesRegex(RuntimeError, 'certificate changed'):
            release.handoff(self.handoff_args(directory))

    def test_handoff_rejects_changed_artifact(self):
        directory, manifest = self.ready()
        (directory / manifest['artifacts']['aab']['path']).write_bytes(b'changed')
        with self.assertRaisesRegex(RuntimeError, 'artifact changed'):
            release.handoff(self.handoff_args(directory))

    def test_handoff_creates_preview_but_no_remote_commands(self):
        directory, _ = self.ready()
        with patch.object(release, 'run') as runner:
            release.handoff(self.handoff_args(directory))
            runner.assert_not_called()
        preview = json.loads((directory / 'upload-preview.json').read_text())
        self.assertEqual(preview['track_kind'], 'closed')
        self.assertEqual(preview['state'], 'local-ready-for-authorized-testing-upload')

    def test_initial_handoff_never_targets_production_or_open(self):
        directory, _ = self.ready()
        for kind in ('production', 'open'):
            self.state['track_kind'] = kind
            self.state_path.write_text(json.dumps(self.state))
            with self.assertRaisesRegex(RuntimeError, 'internal or closed'):
                release.handoff(self.handoff_args(directory))

    def test_play_delivery_checks_remain_pending_after_preparation(self):
        self.repo()
        directory = self.prepared()
        acceptance = json.loads((directory / 'acceptance.json').read_text())
        self.assertEqual(set(acceptance['post_play']), set(release.POST_PLAY_GATES))
        self.assertEqual(acceptance['post_play']['play_delivered_upgrade']['status'], 'pending')

    def test_wrong_aab_signer_and_unsigned_entries_rejected(self):
        directory, manifest = self.ready()
        aab = directory / manifest['artifacts']['aab']['path']
        for bad in ('wrong-cert', 'unsigned'):
            def fake(args, **kwargs):
                if args[0] == 'java' and 'manifest' in args:
                    return self.manifest_xml()
                if args[0] == 'jarsigner':
                    return 'jar verified. unsigned entries' if bad == 'unsigned' else 'jar verified.'
                if args[0] == 'keytool':
                    return 'SHA256: ' + 'CD' * 32
                return ''
            with patch.object(release, 'run', side_effect=fake):
                with self.assertRaises(RuntimeError):
                    release.inspect_bundle(aab, manifest, self.root / 'bundletool.jar', directory, True)

    def test_forbidden_bundle_assets_rejected(self):
        directory, manifest = self.ready()
        aab = directory / manifest['artifacts']['aab']['path']
        with zipfile.ZipFile(aab, 'a') as archive:
            archive.writestr('base/assets/demo.cbz', b'comic')
        with self.assertRaisesRegex(RuntimeError, 'Unexpected model'):
            release.inspect_bundle(aab, manifest, self.root / 'bundletool.jar', directory, True)


if __name__ == '__main__':
    unittest.main()
