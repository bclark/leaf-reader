"""Offline checks: no Apple writes, compilation, signing or real uploads."""
import argparse
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import plistlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import release_ios as release


def record(number):
    return {"id": "build-id", "attributes": {"version": number, "processingState": "VALID"}}


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.output = self.root / "iosApp/build/releases"
        self.stack = [patch.object(release, "ROOT", self.root), patch.object(release, "OUTPUT", self.output)]
        for p in self.stack:
            p.start()
        self.stdout = redirect_stdout(io.StringIO())
        self.stdout.__enter__()

    def tearDown(self):
        self.stdout.__exit__(None, None, None)
        for p in reversed(self.stack):
            p.stop()
        self.tmp.cleanup()

    def file(self, name, content="source"):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)
        return p

    def repo(self):
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        self.file("iosApp/Sources/Main.swift")
        self.file("iosApp/Sources/Info.plist", "tracked plist")
        self.file("iosApp/project.yml")
        self.file(".gitignore", "iosApp/build/\niosApp/Leaf.xcworkspace/\n*Local.xcconfig\n")
        self.file("iosApp/Leaf.xcworkspace/xcshareddata/swiftpm/Package.resolved", '{"pins": []}')
        subprocess.run(["git", "add", "."], cwd=self.root, check=True)
        subprocess.run(["git", "-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                        "commit", "-qm", "source"], cwd=self.root, check=True)

    def prepared(self, number="2026111101"):
        args = argparse.Namespace(version="1.0.1", build_number=number, since=None, allow_dirty=False)
        with patch.object(release, "builds", return_value=[record("2026110902")]):
            release.prepare(args)
        return self.output / f"1.0.1-{number}"

    def ipa(self, directory, manifest, **changes):
        info = {"CFBundleIdentifier": release.BUNDLE_ID, "CFBundleShortVersionString": manifest["version"],
                "CFBundleVersion": manifest["build"]}
        info.update(changes)
        p = directory / "export/Leaf.ipa"
        p.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(p, "w") as z:
            z.writestr("Payload/Leaf.app/Info.plist", plistlib.dumps(info))
        return p

    def built(self):
        self.repo()
        d = self.prepared()
        _, m = release.load_release(d)
        ipa = self.ipa(d, m)
        m.update(status="built", ipa="export/Leaf.ipa", inspection=release.inspect_ipa(ipa, m))
        release.save(d / "release.json", m)
        return d, m

    def test_number_advances_and_handles_future_counter(self):
        self.assertEqual(release.next_number([], "20261110"), "2026111001")
        self.assertEqual(release.next_number([record("2026111009")], "20261110"), "2026111010")
        self.assertEqual(release.next_number([record("2026112001")], "20261110"), "2026112002")

    def test_non_integer_build_is_not_assumed_unused(self):
        with self.assertRaises(RuntimeError):
            release.next_number([record("1.2.3")])

    def test_version_validation(self):
        self.assertEqual(release.validate_version("1.0.1"), "1.0.1")
        for invalid in ("../bad", "1.0-beta", "1", "1.0.1.1"):
            with self.assertRaises(RuntimeError):
                release.validate_version(invalid)

    def test_api_malformed_and_unconsumed_pagination_fail(self):
        for response in ({}, {"data": {}}, {"data": [], "links": {"next": "more"}}):
            with self.assertRaises(RuntimeError):
                release.rows(response)
        self.assertEqual(release.rows({"data": [], "links": {"next": None}}), [])

    def test_asc_reads_force_read_only(self):
        with patch.object(release, "run", return_value='{"data": []}') as run:
            release.asc("builds", "list")
        self.assertEqual(run.call_args.args[0][:2], ["asc", "--read-only"])

    def test_prepare_isolated_source_no_version_edits(self):
        self.repo()
        before = (self.root / "iosApp/project.yml").read_bytes()
        d = self.prepared()
        _, m = release.load_release(d)
        self.assertEqual(m["status"], "prepared")
        self.assertEqual((self.root / "iosApp/project.yml").read_bytes(), before)
        self.assertEqual((d / "source/iosApp/Sources/Main.swift").read_text(), "source")
        self.assertTrue((d / "notes/en-US.txt").read_text().startswith("DRAFT:"))
        release.check_source(d, m)

    def test_dirty_source_requires_scope_opt_in(self):
        self.repo()
        self.file("iosApp/Sources/Main.swift", "changed")
        with patch.object(release, "builds") as remote:
            with self.assertRaisesRegex(RuntimeError, "Tracked changes"):
                self.prepared()
            remote.assert_not_called()

    def test_untracked_app_input_rejected(self):
        self.repo()
        self.file("iosApp/Sources/New.swift")
        with self.assertRaisesRegex(RuntimeError, "Untracked build inputs"):
            self.prepared()

    def test_existing_candidate_not_overwritten(self):
        self.repo()
        self.prepared()
        with self.assertRaisesRegex(RuntimeError, "already exists"):
            self.prepared()

    def test_duplicate_or_lower_build_rejected(self):
        self.repo()
        with self.assertRaisesRegex(RuntimeError, "greater than"):
            self.prepared("2026110902")

    def test_auth_failure_creates_no_candidate(self):
        self.repo()
        args = argparse.Namespace(version="1.0.1", build_number=None, since=None, allow_dirty=False)
        with patch.object(release, "builds", side_effect=RuntimeError("auth failed")):
            with self.assertRaises(RuntimeError):
                release.prepare(args)
        self.assertFalse(self.output.exists())

    def test_changed_source_and_added_source_fail(self):
        self.repo()
        d = self.prepared()
        _, m = release.load_release(d)
        new = d / "source/iosApp/Sources/New.swift"
        new.write_text("new")
        with self.assertRaisesRegex(RuntimeError, "Unrecorded"):
            release.check_source(d, m)
        new.unlink()
        (d / "source/iosApp/Sources/Main.swift").write_text("changed")
        with self.assertRaisesRegex(RuntimeError, "Snapshot changed"):
            release.check_source(d, m)

    def test_ipa_identity_checked_and_export_not_assumed(self):
        self.repo()
        d = self.prepared()
        _, m = release.load_release(d)
        ipa = self.ipa(d, m)
        self.assertIsNone(release.inspect_ipa(ipa, m)["uses_non_exempt_encryption"])
        ipa = self.ipa(d, m, CFBundleVersion="wrong")
        with self.assertRaises(RuntimeError):
            release.inspect_ipa(ipa, m)

    def test_ipa_excludes_storekit_and_model(self):
        self.repo()
        d = self.prepared()
        _, m = release.load_release(d)
        ipa = self.ipa(d, m)
        with zipfile.ZipFile(ipa, "a") as z:
            z.writestr("Payload/Leaf.app/test.storekit", "test")
        with self.assertRaisesRegex(RuntimeError, "Unexpected"):
            release.inspect_ipa(ipa, m)

    def test_upload_requires_confirm_before_apple_read(self):
        d, _ = self.built()
        with patch.object(release, "builds") as remote:
            with self.assertRaisesRegex(RuntimeError, "requires --confirm"):
                release.upload(argparse.Namespace(directory=d, confirm=False))
            remote.assert_not_called()

    def test_existing_remote_build_is_never_reuploaded(self):
        d, m = self.built()
        with patch.object(release, "builds", return_value=[record(m["build"])]), patch.object(release, "run") as run:
            release.upload(argparse.Namespace(directory=d, confirm=True))
            run.assert_not_called()

    def test_upload_sends_exact_ipa_no_submit(self):
        d, m = self.built()
        with patch.object(release, "builds", return_value=[]), patch.object(release, "run") as run:
            release.upload(argparse.Namespace(directory=d, confirm=True))
            command = run.call_args.args[0]
            self.assertEqual(command[:3], ["asc", "builds", "upload"])
            self.assertIn(str((d / m["ipa"]).resolve()), command)
            self.assertNotIn("--submit", command)
            self.assertNotIn("--dry-run", command)
        self.assertEqual(release.load_release(d)[1]["status"], "uploaded")

    def test_failed_upload_is_uncertain_and_cannot_retry(self):
        d, _ = self.built()
        with patch.object(release, "builds", return_value=[]), patch.object(release, "run", side_effect=RuntimeError("timeout")):
            with self.assertRaisesRegex(RuntimeError, "timeout"):
                release.upload(argparse.Namespace(directory=d, confirm=True))
        self.assertEqual(release.load_release(d)[1]["status"], "upload-started")
        with patch.object(release, "builds", return_value=[]), patch.object(release, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "uncertain"):
                release.upload(argparse.Namespace(directory=d, confirm=True))
            run.assert_not_called()

    def test_tampered_ipa_cannot_upload(self):
        d, m = self.built()
        with zipfile.ZipFile(d / m["ipa"], "a") as z:
            z.writestr("Payload/Leaf.app/new.txt", "changed")
        with patch.object(release, "builds", return_value=[]), patch.object(release, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "changed"):
                release.upload(argparse.Namespace(directory=d, confirm=True))
            run.assert_not_called()

    def test_next_candidate_skips_local_failed_numbers(self):
        self.repo()
        with patch.object(release, "builds", return_value=[]):
            args = argparse.Namespace(version="1.0.1", build_number=None, since=None, allow_dirty=False)
            release.prepare(args)
            first = sorted(self.output.iterdir())[0]
            release.prepare(args)
            self.assertEqual(len(list(self.output.iterdir())), 2)
            numbers = sorted(int(x.name.rsplit("-", 1)[-1]) for x in self.output.iterdir())
            self.assertEqual(numbers[1], numbers[0] + 1)

    def test_private_config_change_is_detected_without_storing_content(self):
        self.repo()
        self.file("iosApp/Local.xcconfig", "DEVELOPMENT_TEAM = TEST")
        d = self.prepared()
        _, m = release.load_release(d)
        self.assertIn("iosApp/Local.xcconfig", m["private_config_hashes"])
        self.assertNotIn("DEVELOPMENT_TEAM", json.dumps(m))
        (d / "source/iosApp/Local.xcconfig").write_text("changed")
        with self.assertRaisesRegex(RuntimeError, "Snapshot changed"):
            release.check_source(d, m)

    def test_new_gradle_build_input_rejected_but_cache_allowed(self):
        self.repo()
        d = self.prepared()
        _, m = release.load_release(d)
        cache = d / "source/.gradle/cache.bin"
        cache.parent.mkdir(); cache.write_text("generated")
        release.check_source(d, m)
        (d / "source/gradle.properties").write_text("changed=true")
        with self.assertRaisesRegex(RuntimeError, "Unrecorded"):
            release.check_source(d, m)

    def test_generated_directory_names_inside_sources_are_not_ignored(self):
        self.repo()
        d = self.prepared()
        _, m = release.load_release(d)
        new = d / "source/iosApp/Sources/build/Extra.swift"
        new.parent.mkdir(); new.write_text("extra")
        with self.assertRaisesRegex(RuntimeError, "Unrecorded"):
            release.check_source(d, m)

    def test_generated_root_symlink_is_rejected(self):
        self.repo()
        d = self.prepared()
        _, m = release.load_release(d)
        (d / "source/.gradle").symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, "symlink"):
            release.check_source(d, m)

    def test_candidate_lock_blocks_concurrent_upload(self):
        d = self.root
        with release.candidate_lock(d):
            with self.assertRaisesRegex(RuntimeError, "Another build/upload"):
                with release.candidate_lock(d):
                    pass

    def test_build_pipeline_normalizes_generated_plist_and_never_uploads(self):
        self.repo()
        d = self.prepared()
        calls = []
        def fake_run(command, cwd=None, log=None, timeout=None):
            calls.append(command)
            if command[0] == "xcodegen":
                (d / "source/iosApp/Sources/Info.plist").write_bytes(plistlib.dumps({"NewSpecValue": True}))
            if "archive" in command:
                archive = d / "Leaf.xcarchive"
                archive.mkdir()
                (archive / "Info.plist").write_bytes(plistlib.dumps({"ApplicationProperties": {"Team": "TESTTEAM"}}))
            if "-exportArchive" in command:
                self.ipa(d, release.load_release(d)[1])
            if command[0] == "ditto":
                (d / "inspection/Payload/Leaf.app").mkdir(parents=True)
            if "--entitlements" in command:
                return plistlib.dumps({"get-task-allow": False}).decode()
            return ""
        with patch.object(release, "builds", return_value=[]), patch.object(release, "run", side_effect=fake_run):
            release.build(argparse.Namespace(directory=d, destination="platform=iOS Simulator,id=fixture"))
        _, m = release.load_release(d)
        self.assertEqual(m["status"], "built")
        self.assertNotEqual(m["generated_plist"]["prepared_sha256"], m["generated_plist"]["generated_sha256"])
        self.assertIn(":shared:jvmTest", calls[0])
        native = next(c for c in calls if "test" in c)
        self.assertFalse(any(x.startswith("-skip-testing") or x.startswith("-only-testing") for x in native))
        self.assertFalse(any(c[0] == "asc" for c in calls))
        options = plistlib.loads((d / "ExportOptions.plist").read_bytes())
        self.assertFalse(options["manageAppVersionAndBuildNumber"])
        self.assertEqual(options["destination"], "export")
        release.check_source(d, m)

    def test_build_failure_keeps_evidence_and_blocks_upload(self):
        self.repo()
        d = self.prepared()
        with patch.object(release, "builds", return_value=[]), patch.object(release, "run", side_effect=RuntimeError("test failure")):
            with self.assertRaisesRegex(RuntimeError, "test failure"):
                release.build(argparse.Namespace(directory=d, destination="platform=iOS Simulator,id=fixture"))
        self.assertEqual(release.load_release(d)[1]["status"], "building")
        with self.assertRaisesRegex(RuntimeError, "not passed"):
            release.upload(argparse.Namespace(directory=d, confirm=True))


if __name__ == "__main__":
    unittest.main()
