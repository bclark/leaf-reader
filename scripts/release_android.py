#!/usr/bin/env python3
"""Prepare, test and inspect Leaf Android candidates. Never uploads or changes Play."""
import argparse
from contextlib import contextmanager
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app/build/play-releases'
PACKAGE = 'net.digitalbc.leafreader'
INPUTS = ('app', 'shared', 'gradle', 'buildSrc', 'build.gradle.kts', 'settings.gradle.kts',
          'gradle.properties', 'gradlew', 'gradlew.bat')
SIGNING = ('KEYSTORE_FILE', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD')
GATES = ('upgrade_shipped_v4', 'upgrade_pre_revision_v5', 'backup_export_restore',
         'signed_minified_device', 'offline_reading', 'jellyfin_sync_and_cancellation',
         'privacy_and_declarations', 'native_16kb_alignment', 'native_symbols_review', 'notes_review')
POST_PLAY_GATES = ('play_delivered_upgrade', 'testing_feedback', 'production_eligibility')


def fail(message):
    raise RuntimeError(message)


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save(path, value):
    path = Path(path)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(value, indent=2) + '\n')
    temp.replace(path)


def run(args, cwd=None, log=None, env=None):
    # Callers never put passwords/tokens in arguments or print the environment.
    result = subprocess.run(list(map(str, args)), cwd=cwd or ROOT, env=env,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if log:
        Path(log).write_text(result.stdout)
    if result.returncode:
        fail(f'Command failed ({result.returncode}): {args[0]}. See {log or "tool output"}.')
    return result.stdout


def git(*args):
    return run(['git', *args])


def fingerprint(value):
    normalized = value.replace(':', '').upper()
    if not re.fullmatch('[0-9A-F]{64}', normalized):
        fail('Expected a public upload-certificate SHA-256 fingerprint, not a key/password.')
    return normalized


def play_state(path):
    state = json.loads(Path(path).read_text())
    if state.get('package') != PACKAGE or state.get('all_tracks_and_drafts_checked') is not True:
        fail('Play evidence must cover this package, all tracks, drafts and uploaded bundle history.')
    checked = dt.datetime.fromisoformat(state['checked_at'].replace('Z', '+00:00'))
    if checked.tzinfo is None:
        fail('Play checked_at must include its time zone.')
    age = (dt.datetime.now(dt.timezone.utc) - checked).total_seconds()
    if not 0 <= age <= 86400:
        fail('Play evidence is stale or in the future. Recheck Console (within 24 hours).')
    maximum = state.get('max_version_code')
    if type(maximum) is not int or not 0 <= maximum <= 2100000000:
        fail('Play max_version_code must be the highest consumed code, not just production.')
    if not state.get('track') or state.get('track_kind') not in ('internal', 'closed', 'open', 'production'):
        fail('Record the exact Play track identifier/name and track_kind.')
    if not str(state.get('evidence', '')).strip():
        fail('Record where the live Console evidence was verified.')
    state['upload_certificate_sha256'] = fingerprint(state['upload_certificate_sha256'])
    return state


def validate_version(version, code):
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version):
        fail('Use a plain three-part Android version such as 0.3.8.')
    if type(code) is not int or not 1 <= code <= 2100000000:
        fail('versionCode must be a positive Play integer no greater than 2100000000.')


def configured_version(text):
    versions = re.findall(r'^\s*versionName = "([^"]+)"\s*$', text, re.M)
    codes = re.findall(r'^\s*versionCode = (\d+)\s*$', text, re.M)
    if len(versions) != 1 or len(codes) != 1:
        fail('Version configuration changed; review the helper instead of guessing an override.')
    return versions[0], int(codes[0])


def check_number(version, code, current, state):
    validate_version(version, code)
    if tuple(map(int, version.split('.'))) <= tuple(map(int, current[0].split('.'))):
        fail('Candidate version must be newer than the source version.')
    if code <= max(current[1], state['max_version_code']):
        fail('Choose a versionCode above source and every consumed Play code. Never reuse 30700.')


def source_files():
    names = list(filter(None, git('ls-files', '-z', '--', *INPUTS).split('\0')))
    for name in names:
        if re.search(r'(^|/)(\.env(?:\..*)?|local\.properties|.*credentials(?:[._-]|$).*|.*service.account(?:[._-]|$).*)|\.(jks|keystore|p8|p12|pem)$', name, re.I):
            fail(f'Private configuration is tracked; do not snapshot: {name}')
        if (ROOT / name).is_symlink():
            fail(f'Review tracked symlink before snapshotting: {name}')
    return [name for name in names if (ROOT / name).is_file()]


def check_source(directory, manifest):
    source = directory / 'source'
    for name, expected in manifest['source_hashes'].items():
        p = source / name
        if p.is_symlink() or not p.is_file() or sha(p) != expected:
            fail(f'Candidate source changed: {name}. Prepare a fresh candidate.')
    # Reject unexpected code/config inputs, including ignored injected sources.
    actual = {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()
              and not any(part in ('build', '.gradle', '.kotlin') for part in p.relative_to(source).parts)}
    if actual != set(manifest['source_hashes']):
        fail('Candidate has unexpected source files. Prepare a fresh candidate.')


def migration_guard(source):
    path = source / 'app/src/main/java/net/digitalbc/leafreader/data/db/AppDatabase.kt'
    text = path.read_text()
    version = re.search(r'\bversion\s*=\s*(\d+)', text)
    if not version:
        fail('Cannot identify Room schema version; inspect migrations.')
    if int(version[1]) <= 5 and 'revision INTEGER' in text:
        fail('Known schema-5 upgrade blocker: revision was added without a new DB version. '
             'Fix and test a preserving migration from pre-revision v5 before building a release.')
    # This narrow regression guard is not proof that later migrations are correct.


def prepare(args):
    state = play_state(args.play_state)
    current = configured_version((ROOT / 'app/build.gradle.kts').read_text())
    check_number(args.version, args.version_code, current, state)
    dirty = git('status', '--porcelain', '--untracked-files=no', '--', *INPUTS).strip()
    if dirty and not args.allow_dirty:
        fail('Android/shared build inputs have tracked changes. Review them before --allow-dirty.')
    extras = git('ls-files', '--others', '--exclude-standard', '-z', '--', *INPUTS).strip('\0')
    if extras:
        fail('Review and add untracked build inputs first:\n' + extras.replace('\0', '\n'))
    baseline = git('rev-parse', '--verify', args.since + '^{commit}').strip()
    head = git('rev-parse', 'HEAD').strip()
    names = source_files()
    attempt = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    directory = OUTPUT / f'{args.version}-{args.version_code}-{attempt}'
    OUTPUT.mkdir(parents=True, exist_ok=True)
    try:
        directory.mkdir()  # Atomic refusal to overwrite a candidate.
    except FileExistsError:
        fail('Candidate already exists; inspect it, never overwrite release evidence.')
    source = directory / 'source'
    original = {name: sha(ROOT / name) for name in names}
    for name in names:
        target = source / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / name, target)
    if any(sha(source / name) != digest or sha(ROOT / name) != digest for name, digest in original.items()):
        fail('Source changed while snapshotting. Keep this failed snapshot and prepare a new candidate.')
    build_file = source / 'app/build.gradle.kts'
    text = build_file.read_text()
    text = re.sub(r'(?m)^(\s*versionName = )"[^"]+"\s*$', rf'\g<1>"{args.version}"', text)
    text = re.sub(r'(?m)^(\s*versionCode = )\d+\s*$', rf'\g<1>{args.version_code}', text)
    build_file.write_text(text)
    (directory / 'source.patch').write_text(git('diff', '--binary', baseline, '--', *INPUTS))
    (directory / 'changes.txt').write_text(git('log', '--format=%h %s', baseline + '..HEAD', '--', 'app', 'shared'))
    (directory / 'notes').mkdir()
    (directory / 'notes/en-US.txt').write_text('DRAFT: Replace with reviewed reader-friendly notes (500 characters per locale).\n')
    manifest = {'format': 1, 'package': PACKAGE, 'version': args.version, 'version_code': args.version_code,
                'status': 'prepared', 'head': head, 'since': baseline, 'source_version': current,
                'original_source_hashes': original,
                'source_hashes': {name: sha(source / name) for name in names}, 'play_at_prepare': state}
    save(directory / 'release.json', manifest)
    save(directory / 'acceptance.json', {'aab_sha256': None,
        'gates': {gate: {'status': 'pending', 'evidence': ''} for gate in GATES},
        'post_play': {gate: {'status': 'pending', 'evidence': ''} for gate in POST_PLAY_GATES}})
    print(f'Prepared {directory}\nNo app version in the checkout changed. Nothing built or uploaded.')
    return directory


def load_release(path):
    directory = Path(path).resolve()
    manifest = json.loads((directory / 'release.json').read_text())
    if manifest.get('format') != 1 or manifest.get('package') != PACKAGE:
        fail('Not a supported Leaf Android candidate.')
    validate_version(manifest['version'], manifest['version_code'])
    return directory, manifest


@contextmanager
def candidate_lock(directory):
    with (Path(directory) / '.lock').open('a') as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            fail('Another operation is using this candidate.')
        yield


def test_summary(folder, required=()):
    files = sorted(Path(folder).glob('TEST-*.xml'))
    if not files:
        fail(f'No fresh test results in {folder}')
    result = {'passed': 0, 'skipped': [], 'suites': []}
    for path in files:
        suite = ET.parse(path).getroot()
        result['suites'].append(suite.attrib['name'])
        cases = suite.findall('testcase')
        if not cases or int(suite.get('failures', 0)) or int(suite.get('errors', 0)):
            fail(f'Failed or empty test suite: {path.name}')
        for case in cases:
            if case.find('failure') is not None or case.find('error') is not None:
                fail(f'Failed test: {path.name}')
            if case.find('skipped') is not None:
                name = case.get('classname', suite.attrib['name']) + '.' + case.attrib['name']
                if not name.endswith('SmbLiveTest.configuredShareListsDownloadsAndCancelsWithoutPublishingPartialFiles'):
                    fail(f'Unexpected skipped test: {name}')
                result['skipped'].append(name)
            else:
                result['passed'] += 1
    for required_name in required:
        if not any(name.endswith('.' + required_name) for name in result['suites']):
            fail(f'Missing mandatory suite: {required_name}')
    return result


def validate_manifest(text, manifest):
    root = ET.fromstring(text)
    ns = '{http://schemas.android.com/apk/res/android}'
    expected = {'package': PACKAGE, ns + 'versionName': manifest['version'], ns + 'versionCode': str(manifest['version_code'])}
    if any(root.get(key) != value for key, value in expected.items()):
        fail('Bundle package/version does not match the candidate.')
    application = root.find('application')
    if application is None or application.get(ns + 'debuggable', 'false') != 'false':
        fail('Bundle must contain a non-debuggable application.')


def inspect_bundle(path, manifest, bundletool, directory, signed):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names) or archive.testzip():
            fail('AAB contains duplicate or corrupt ZIP entries.')
        forbidden = [n for n in names if re.search(r'\.(tflite|cbz|cbr|pdf)$|tensorflow|litert', n, re.I)]
        if forbidden:
            fail('Unexpected model/runtime/comic assets in AAB: ' + ', '.join(forbidden))
        symbols = [n for n in names if 'debugsymbols/' in n]
    run(['java', '-jar', bundletool, 'validate', '--bundle=' + str(path)], log=directory / 'bundle-validate.log')
    xml = run(['java', '-jar', bundletool, 'dump', 'manifest', '--bundle=' + str(path), '--module=base'],
              log=directory / 'bundle-manifest.xml')
    validate_manifest(xml, manifest)
    inspection = {'sha256': sha(path), 'bytes': path.stat().st_size, 'native_symbol_entries': symbols,
                  'signed': signed}
    if signed:
        verified = run(['jarsigner', '-verify', '-verbose', '-certs', path], log=directory / 'aab-signature.log')
        if 'jar verified.' not in verified or 'unsigned entries' in verified.lower():
            fail('AAB signature is missing or has unsigned entries.')
        certificate = run(['keytool', '-printcert', '-jarfile', path], log=directory / 'aab-certificate.log')
        hashes = re.findall(r'SHA256:\s*([A-Fa-f0-9:]+)', certificate)
        if len(hashes) != 1 or fingerprint(hashes[0]) != manifest['play_at_prepare']['upload_certificate_sha256']:
            fail('AAB signer does not match the verified Play upload certificate.')
        inspection['upload_certificate_sha256'] = fingerprint(hashes[0])
    return inspection


def build(args):
    directory, manifest = load_release(args.directory)
    if manifest['status'] != 'prepared':
        fail('Build already started. Preserve logs and prepare a fresh candidate, never overwrite evidence.')
    check_source(directory, manifest)
    source = directory / 'source'
    migration_guard(source)
    sdk = Path(args.android_sdk).resolve()
    tool_dir = sdk / 'build-tools/36.1.0'
    bundletool = Path(args.bundletool).resolve()
    if not bundletool.is_file() or not all((tool_dir / name).is_file() for name in ('aapt2', 'apksigner')):
        fail('Supply the Android SDK with build-tools 36.1.0 and an official bundletool JAR.')
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk), LEAF_SMB_LIVE_TEST='0', LC_ALL='C', LANG='C')
    if args.signed:
        if any(not env.get(key) for key in SIGNING):
            fail('Existing signing must supply all four KEYSTORE_FILE/PASSWORD and KEY_ALIAS/PASSWORD variables securely.')
        key = Path(env['KEYSTORE_FILE'])
        if not key.is_absolute() or not key.is_file():
            fail('KEYSTORE_FILE must name the existing keystore by absolute path; no new key is created.')
    else:
        for key in SIGNING:
            env.pop(key, None)
    manifest['status'] = 'building'
    save(directory / 'release.json', manifest)
    flags = ['--no-daemon', '--no-build-cache', '--rerun-tasks', '--stacktrace']
    if args.offline:
        flags.append('--offline')
    run(['./gradlew', ':shared:jvmTest', *flags], source, directory / 'shared-tests.log', env)
    manifest['tests'] = {'shared': test_summary(source / 'shared/build/test-results/jvmTest')}
    run(['./gradlew', ':app:testFullDebugUnitTest', ':app:testFdroidDebugUnitTest',
         ':app:assembleFullDebug', ':app:assembleFdroidDebug', ':app:lintFullDebug', ':app:lintFdroidDebug', *flags],
        source, directory / 'android-tests-build-lint.log', env)
    for flavor in ('Full', 'Fdroid'):
        manifest['tests'][flavor] = test_summary(source / f'app/build/test-results/test{flavor}DebugUnitTest',
            ('AppDatabaseMigrationTest', 'BackupManagerTest', 'LibraryRepositoryJellyfinTest'))
    save(directory / 'release.json', manifest)
    run(['./gradlew', ':app:bundleFullRelease', ':app:assembleFullRelease', *flags],
        source, directory / 'full-release.log', env)
    unsigned_env = {key: value for key, value in env.items() if key not in SIGNING}
    run(['./gradlew', ':app:assembleFdroidRelease', *flags], source, directory / 'fdroid-release.log', unsigned_env)
    output = source / 'app/build/outputs'
    aab = output / 'bundle/fullRelease/app-full-release.aab'
    manifest['inspection'] = inspect_bundle(aab, manifest, bundletool, directory, args.signed)
    artifacts = {'aab': aab}
    for flavor in ('full', 'fdroid'):
        apks = list((output / f'apk/{flavor}/release').glob('*.apk'))
        if len(apks) != 1:
            fail(f'Expected one {flavor} release APK, found {len(apks)}.')
        apk = apks[0]
        badging = run([tool_dir / 'aapt2', 'dump', 'badging', apk], log=directory / f'{flavor}-badging.log')
        for token in (f"name='{PACKAGE}'", f"versionCode='{manifest['version_code']}'", f"versionName='{manifest['version']}'"):
            if token not in badging.splitlines()[0]:
                fail(f'{flavor} APK identity mismatch.')
        if 'application-debuggable' in badging:
            fail('Release APK is debuggable.')
        if args.signed and flavor == 'full':
            signature = run([tool_dir / 'apksigner', 'verify', '--verbose', '--print-certs', apk],
                            log=directory / 'apk-signature.log')
            hashes = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)', signature)
            if len(hashes) != 1 or fingerprint(hashes[0]) != manifest['play_at_prepare']['upload_certificate_sha256']:
                fail('Full APK upload certificate mismatch.')
        mapping = output / f'mapping/{flavor}Release/mapping.txt'
        if not mapping.is_file() or mapping.stat().st_size == 0:
            fail(f'Missing {flavor} R8 mapping.')
        artifacts[flavor + '_apk'] = apk
        artifacts[flavor + '_mapping'] = mapping
    check_source(directory, manifest)
    manifest['artifacts'] = {name: {'path': str(path.relative_to(directory)), 'sha256': sha(path),
                                   'bytes': path.stat().st_size} for name, path in artifacts.items()}
    manifest['status'] = 'built-signed' if args.signed else 'built-unsigned'
    save(directory / 'release.json', manifest)
    print(f"{manifest['status']}: {directory}\nTests and local artifact checks passed. Manual acceptance remains open. Nothing uploaded.")


def notes(directory):
    files = sorted((directory / 'notes').glob('*.txt'))
    if not files:
        fail('No release notes found.')
    result = {}
    for path in files:
        text = path.read_text().strip()
        if not text or len(text) > 500 or re.search(r'\b(DRAFT|TODO|TBD|PLACEHOLDER)\b', text, re.I):
            fail(f'Release notes need review (nonempty, no placeholders, <=500 characters): {path.name}')
        result[path.stem] = text
    return result


def handoff(args):
    directory, manifest = load_release(args.directory)
    if manifest['status'] != 'built-signed':
        fail('Only a signed, inspected candidate can be handed to Play.')
    check_source(directory, manifest)
    migration_guard(directory / 'source')
    state = play_state(args.play_state)
    if state['track_kind'] not in ('internal', 'closed'):
        fail('Initial upload preview requires an internal or closed test track. Promote the tested Play artifact separately; do not rebuild or reupload for production.')
    if manifest['version_code'] <= state['max_version_code']:
        fail('Code is already consumed in Play. Reconcile existing upload; never upload it again.')
    if state['upload_certificate_sha256'] != manifest['inspection']['upload_certificate_sha256']:
        fail('Play upload certificate changed; reconcile before upload.')
    for artifact in manifest['artifacts'].values():
        path = directory / artifact['path']
        if not path.is_file() or path.is_symlink() or sha(path) != artifact['sha256']:
            fail('Built artifact changed. Do not upload.')
    acceptance = json.loads((directory / 'acceptance.json').read_text())
    if acceptance.get('aab_sha256') != manifest['artifacts']['aab']['sha256']:
        fail('Acceptance must identify the exact AAB SHA-256.')
    for gate in GATES:
        item = acceptance.get('gates', {}).get(gate, {})
        if item.get('status') != 'pass' or not str(item.get('evidence', '')).strip():
            fail(f'Acceptance gate remains open: {gate}. Record real evidence; never infer a pass.')
    preview = {'package': PACKAGE, 'version': manifest['version'], 'version_code': manifest['version_code'],
               'track': state['track'], 'track_kind': state['track_kind'], 'notes': notes(directory),
               'artifacts': manifest['artifacts'], 'state': 'local-ready-for-authorized-testing-upload',
               'post_play_acceptance': acceptance.get('post_play', {}),
               'warning': 'No upload performed. Local migration fixtures do not prove a Play-signed in-place upgrade. Verify Play-delivered upgrade and test feedback before production promotion. Review current Console immediately before upload; draft, review and rollout are separate actions.'}
    save(directory / 'upload-preview.json', preview)
    print(json.dumps(preview, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('prepare', help='Snapshot approved Android source; no build or upload')
    p.add_argument('--version', required=True)
    p.add_argument('--version-code', type=int, required=True)
    p.add_argument('--since', required=True, help='Verified previous Android source commit/tag')
    p.add_argument('--play-state', required=True, help='Fresh read-only Console evidence JSON')
    p.add_argument('--allow-dirty', action='store_true', help='Include reviewed Android/shared tracked changes')
    p.set_defaults(func=prepare)
    p = sub.add_parser('build', help='Fresh full tests, minified artifacts and inspection; no upload')
    p.add_argument('directory')
    p.add_argument('--android-sdk', required=True)
    p.add_argument('--bundletool', required=True, help='Path to official bundletool JAR')
    p.add_argument('--signed', action='store_true', help='Use existing securely injected signing environment')
    p.add_argument('--offline', action='store_true', help='Use cached Gradle dependencies only')
    p.set_defaults(func=build)
    p = sub.add_parser('handoff', help='Verify candidate and acceptance; write local upload preview only')
    p.add_argument('directory')
    p.add_argument('--play-state', required=True)
    p.set_defaults(func=handoff)
    p = sub.add_parser('status', help='Read local status; not a live Play status query')
    p.add_argument('directory')
    p.set_defaults(func=lambda args: print(json.dumps(load_release(args.directory)[1], indent=2)))
    args = parser.parse_args()
    if args.command in ('build', 'handoff'):
        with candidate_lock(args.directory):
            args.func(args)
    else:
        args.func(args)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile) as error:
        print(f'STOP: {error}', file=sys.stderr)
        sys.exit(1)
