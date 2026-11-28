#!/usr/bin/env python3
"""Leaf's local, resumable App Store build helper. Python standard library only."""
import argparse
import datetime as dt
import hashlib
import fcntl
from contextlib import contextmanager
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP_ID = "6830333334"
BUNDLE_ID = "net.digitalbc.leafreader"
OUTPUT = ROOT / "iosApp/build/releases"


def fail(message):
    raise RuntimeError(message)


def run(args, cwd=None, log=None, timeout=None):
    cwd = cwd or ROOT
    print("+ " + " ".join(map(str, args)), flush=True)
    if log:
        with Path(log).open("w") as stream:
            result = subprocess.run(args, cwd=cwd, stdout=stream, stderr=subprocess.STDOUT, timeout=timeout)
        if result.returncode:
            fail(f"Command failed ({result.returncode}). See {log}")
        return ""
    result = subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE, check=True, timeout=timeout)
    return result.stdout


def asc(*args):
    # This adapter is read-only. The explicitly authorized upload uses a separate command.
    return json.loads(run(["asc", "--read-only", *args, "--output", "json"], timeout=120))


def rows(response):
    if not isinstance(response.get("data"), list):
        fail("Unexpected ASC response; refusing to treat it as an empty result.")
    if response.get("links", {}).get("next"):
        fail("ASC response still has a next page; cannot safely select a build number.")
    return response["data"]


def builds(version=None):
    args = ["builds", "list", "--app", APP_ID, "--platform", "IOS", "--paginate",
            "--processing-state", "all"]
    if version:
        args += ["--version", version]
    return rows(asc(*args))


def next_number(existing, today=None):
    today = today or dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d")
    values = [str(item["attributes"]["version"]) for item in existing]
    if any(not x.isdigit() for x in values):
        fail("Found a non-integer build number. Choose an explicit number after reviewing ASC.")
    return str(max([int(today + "01"), *[int(x) + 1 for x in values]]))


def validate_version(value):
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?", value):
        fail("Version must be an Apple version such as 1.0.1.")
    return value


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def save(path, value):
    path = Path(path)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n")
    temporary.replace(path)


def load_release(path):
    directory = Path(path).resolve()
    manifest = json.loads((directory / "release.json").read_text())
    if manifest.get("app_id") != APP_ID or manifest.get("bundle_id") != BUNDLE_ID:
        fail("This is not a Leaf release manifest.")
    return directory, manifest


def input_files():
    names = run(["git", "ls-files", "-z"]).split("\0")
    result = []
    for name in filter(None, names):
        path = ROOT / name
        if path.is_symlink():
            fail(f"Tracked symlink needs explicit review before snapshotting: {name}")
        if not path.is_file():  # Tracked deletions are intentional and recorded in the patch.
            continue
        if re.search(r"(^|/)(\.env(?:\..*)?|Local\.xcconfig)$|\.(p8|p12|jks|keystore)$", name):
            fail(f"Unexpected private material is tracked: {name}. Do not snapshot it.")
        result.append(name)
    return result


def prepare(args):
    version = validate_version(args.version)
    dirty = run(["git", "status", "--porcelain", "--untracked-files=no"]).strip()
    if dirty and not args.allow_dirty:
        fail("Tracked changes exist. Review their scope, then use --allow-dirty to snapshot the entire working tree.")
    # Untracked source would silently disappear from a Git-based snapshot.
    extra = run(["git", "ls-files", "--others", "--exclude-standard", "-z", "--",
                 "iosApp", "shared", "gradle", "build.gradle.kts", "settings.gradle.kts"]).strip("\0")
    if extra:
        fail("Untracked build inputs must be reviewed and added to Git first:\n" + extra.replace("\0", "\n"))
    baseline = run(["git", "rev-parse", "HEAD"]).strip()
    changes = (run(["git", "log", "--format=%h %s", f"{args.since}..HEAD", "--", "iosApp", "shared"])
               if args.since else "Set a verified previous iOS source baseline before drafting release notes.\n")
    remote = builds()
    next_number(remote)  # Reject an unexpected remote build-number format even for explicit numbers.
    local = [{"attributes": {"version": path.name.rsplit("-", 1)[-1]}}
             for path in OUTPUT.glob("*-*") if path.is_dir() and path.name.rsplit("-", 1)[-1].isdigit()]
    number = args.build_number or next_number(remote + local)
    if not number.isdigit() or int(number) < 1:
        fail("Build number must be a positive integer.")
    if any(int(number) <= int(x["attributes"]["version"]) for x in remote):
        fail("Choose a build number greater than every uploaded iOS build.")
    directory = OUTPUT / f"{version}-{number}"
    if directory.exists():
        fail(f"Candidate already exists; resume it instead: {directory}")
    lock = ROOT / "iosApp/Leaf.xcworkspace/xcshareddata/swiftpm/Package.resolved"
    if not lock.is_file():
        fail("Resolve Swift packages in the existing Leaf workspace first; no lockfile found.")
    names = input_files()  # Fail before creating a partial candidate for invalid inputs.
    expected_hashes = {name: sha(ROOT / name) for name in names}
    source = directory / "source"
    source.mkdir(parents=True)
    hashes = {}
    for name in names:
        target = source / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / name, target)
        hashes[name] = sha(target)
    # Keep existing signing settings private. They are never printed or added to source evidence.
    private_hashes = {}
    for name in ("iosApp/Local.xcconfig", "local.properties"):
        if (ROOT / name).is_file():
            shutil.copy2(ROOT / name, source / name)
            private_hashes[name] = sha(source / name)
    shutil.copy2(lock, directory / "Package.resolved")
    if hashes != expected_hashes or any(sha(ROOT / name) != digest for name, digest in hashes.items()):
        fail("Checkout changed during snapshotting. No usable candidate created; prepare again after edits settle.")
    if run(["git", "rev-parse", "HEAD"]).strip() != baseline:
        fail("HEAD changed during preparation. Prepare again after source changes settle.")
    manifest = {"app_id": APP_ID, "bundle_id": BUNDLE_ID, "version": version, "build": number,
                "created_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                "base_commit": baseline,
                "source_hashes": hashes, "private_config_hashes": private_hashes,
                "package_lock_sha256": sha(lock), "status": "prepared"}
    save(directory / "release.json", manifest)
    (directory / "working-tree.patch").write_text(run(["git", "diff", "HEAD", "--binary"]))
    (directory / "source-status.txt").write_text(dirty + "\n")
    (directory / "changes.txt").write_text(changes)
    notes = directory / "notes"
    notes.mkdir()
    (notes / "en-US.txt").write_text("DRAFT: Replace with verified reader-facing changes before staging.\n")
    print(f"\nPrepared {directory}\nNo build number was changed in the checkout or reserved remotely.")
    print("Review source-status.txt, source/ and changes.txt. Write notes/en-US.txt (and other active locales).")


@contextmanager
def candidate_lock(directory):
    with (Path(directory) / ".operation.lock").open("a") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            fail("Another build/upload is using this candidate. Wait for it; do not duplicate the operation.")
        try:
            yield
        finally:
            fcntl.flock(stream, fcntl.LOCK_UN)


def check_source(directory, manifest):
    expected = {**manifest["source_hashes"], **manifest.get("private_config_hashes", {})}
    source = directory / "source"
    generated = {"build", ".gradle", ".kotlin", "app/build", "shared/build", "iosApp/build",
                 "iosApp/Pods", "iosApp/Leaf.xcodeproj", "iosApp/Leaf.xcworkspace"}
    for parent, directories, files in os.walk(source):
        for name in list(directories) + files:
            path = Path(parent) / name
            relative = path.relative_to(source).as_posix()
            if path.is_symlink():
                fail(f"Unexpected source symlink: {relative}.")
            if relative in generated:
                if name in directories:
                    directories.remove(name)
                continue
            if path.is_file() and name != ".DS_Store" and relative not in expected:
                fail(f"Unrecorded source input: {relative}. Prepare a fresh candidate.")
    for name, expected_hash in expected.items():
        path = source / name
        if path.is_symlink() or not path.is_file() or sha(path) != expected_hash:
            fail(f"Snapshot changed: {name}. Prepare a fresh candidate.")
    if sha(directory / "Package.resolved") != manifest["package_lock_sha256"]:
        fail("Swift package lock changed. Prepare a fresh candidate.")


def inspect_ipa(path, manifest):
    with zipfile.ZipFile(path) as archive:
        plists = [x for x in archive.namelist() if re.fullmatch(r"Payload/[^/]+\.app/Info.plist", x)]
        if len(plists) != 1:
            fail("IPA must contain exactly one main app.")
        info = plistlib.loads(archive.read(plists[0]))
        expected = {"CFBundleIdentifier": BUNDLE_ID, "CFBundleShortVersionString": manifest["version"],
                    "CFBundleVersion": manifest["build"]}
        for key, value in expected.items():
            if info.get(key) != value:
                fail(f"IPA {key} does not match this release.")
        forbidden = [x for x in archive.namelist() if re.search(r"\.(tflite|storekit|xctest|cbz|cbr|pdf)(/|$)", x, re.I)]
        if forbidden:
            fail("Unexpected model/test/comic resources: " + ", ".join(forbidden))
        return {"sha256": sha(path), "bytes": path.stat().st_size,
                "uses_non_exempt_encryption": info.get("ITSAppUsesNonExemptEncryption")}


def build(args):
    directory, manifest = load_release(args.directory)
    if manifest["status"] != "prepared":
        fail("This candidate already started building. Inspect its logs; prepare a new candidate rather than overwriting evidence.")
    check_source(directory, manifest)
    remote = builds()
    next_number(remote)
    if any(int(x["attributes"]["version"]) >= int(manifest["build"]) for x in remote):
        fail("Apple build numbers advanced since preparation. Prepare a new candidate.")
    source = directory / "source"
    manifest["status"] = "building"
    save(directory / "release.json", manifest)
    # Required order: shared tests/framework -> XcodeGen -> pods -> workspace.
    run(["./gradlew", ":shared:jvmTest", "--rerun-tasks", "--stacktrace"], source, directory / "shared-tests.log")
    run(["./gradlew", ":shared:linkReleaseFrameworkIosArm64", "--stacktrace"], source, directory / "shared-framework.log")
    run(["xcodegen", "generate", "--spec", "iosApp/project.yml", "--project", "iosApp"], source, directory / "xcodegen.log")
    # project.yml is the approved source of truth. XcodeGen may legitimately
    # refresh its tracked generated plist. Record that one derived output before
    # tests/archive; all other source/configuration must still match preparation.
    plist_name = "iosApp/Sources/Info.plist"
    generated_hash = sha(source / plist_name)
    manifest["generated_plist"] = {"prepared_sha256": manifest["source_hashes"].get(plist_name),
                                   "generated_sha256": generated_hash}
    manifest["source_hashes"][plist_name] = generated_hash
    save(directory / "release.json", manifest)
    check_source(directory, manifest)
    run(["pod", "install", "--deployment"], source / "iosApp", directory / "pods.log")
    lock = source / "iosApp/Leaf.xcworkspace/xcshareddata/swiftpm/Package.resolved"
    lock.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(directory / "Package.resolved", lock)
    archive = directory / "Leaf.xcarchive"
    common = ["xcodebuild", "-workspace", "iosApp/Leaf.xcworkspace", "-scheme", "Leaf",
              "-derivedDataPath", str(directory / "DerivedData"),
              "-disableAutomaticPackageResolution",
              f"MARKETING_VERSION={manifest['version']}", f"CURRENT_PROJECT_VERSION={manifest['build']}"]
    # A full native suite is mandatory; a known failure is not silently excluded.
    run(common + ["-configuration", "Debug", "-destination", args.destination,
                  "-parallel-testing-enabled", "NO", "-resultBundlePath", str(directory / "native-tests.xcresult"),
                  "test"], source, directory / "native-tests.log")
    run(common + ["-configuration", "Release", "-destination", "generic/platform=iOS",
                  "-archivePath", str(archive), "archive"], source, directory / "archive.log")
    archive_info = plistlib.loads((archive / "Info.plist").read_bytes())
    team = archive_info["ApplicationProperties"].get("Team")
    if not team:
        fail("Archive contains no signing team. Keep existing signing setup; inspect archive.log.")
    options = {"method": "app-store-connect", "destination": "export", "signingStyle": "automatic",
               "teamID": team, "manageAppVersionAndBuildNumber": False, "uploadSymbols": True}
    options_path = directory / "ExportOptions.plist"
    options_path.write_bytes(plistlib.dumps(options))
    run(["xcodebuild", "-exportArchive", "-archivePath", str(archive), "-exportOptionsPlist",
         str(options_path), "-exportPath", str(directory / "export")], source, directory / "export.log")
    ipa = directory / "export/Leaf.ipa"
    inspection = inspect_ipa(ipa, manifest)
    # Inspect the actual exported signature and distribution entitlements.
    extracted = directory / "inspection"
    run(["ditto", "-x", "-k", str(ipa), str(extracted)], source, directory / "unzip.log")
    app = next((extracted / "Payload").glob("*.app"))
    run(["codesign", "--verify", "--deep", "--strict", str(app)], source, directory / "signature.log")
    entitlements = plistlib.loads(run(["codesign", "-d", "--entitlements", ":-", str(app)]).encode())
    if entitlements.get("get-task-allow") is not False:
        fail("Export is not a distribution-signed app (get-task-allow must be false).")
    check_source(directory, manifest)
    if sha(lock) != manifest["package_lock_sha256"]:
        fail("Resolved packages changed during the build.")
    manifest.update(status="built", ipa=str(ipa.relative_to(directory)), inspection=inspection)
    save(directory / "release.json", manifest)
    print(f"\nBuilt and verified {manifest['version']} ({manifest['build']}). Nothing uploaded.")
    print(f"IPA: {ipa}\nSHA-256: {inspection['sha256']}")


def upload(args):
    directory, manifest = load_release(args.directory)
    if not args.confirm:
        fail("Upload requires --confirm after authorization for this exact candidate. Nothing uploaded.")
    if manifest["status"] not in ("built", "upload-started", "uploaded"):
        fail("Candidate has not passed its local build/inspection gates.")
    found = [x for x in builds(manifest["version"]) if x["attributes"]["version"] == manifest["build"]]
    if found:
        print(json.dumps(found, indent=2))
        print("Build already exists in ASC. No upload repeated; inspect processing/export status.")
        return
    if manifest["status"] != "built":
        fail("Prior upload outcome is uncertain. Check ASC and upload.log; do not blindly upload again.")
    remote = builds()
    next_number(remote)
    if any(int(x["attributes"]["version"]) >= int(manifest["build"]) for x in remote):
        fail("Apple build numbers advanced. Inspect live state and prepare a newer candidate.")
    check_source(directory, manifest)
    ipa = directory / manifest["ipa"]
    if inspect_ipa(ipa, manifest) != manifest["inspection"]:
        fail("IPA changed since verification.")
    manifest["status"] = "upload-started"
    save(directory / "release.json", manifest)
    # Upload the exact inspected IPA with existing ASC credentials. No build reservation dry-run.
    run(["asc", "builds", "upload", "--app", APP_ID, "--ipa", str(ipa), "--wait"],
        ROOT, directory / "upload.log")
    manifest["status"] = "uploaded"
    save(directory / "release.json", manifest)
    print("Upload command completed. Check status for Apple processing/export details; not submitted or released.")


def status(args):
    if args.directory:
        directory, manifest = load_release(args.directory)
        found = [x for x in builds(manifest["version"]) if x["attributes"]["version"] == manifest["build"]]
        print(json.dumps({"local_status": manifest["status"], "version": manifest["version"],
                          "build": manifest["build"], "apple": found}, indent=2))
    else:
        response = asc("versions", "list", "--app", APP_ID, "--platform", "IOS", "--include", "build", "--paginate")
        print(json.dumps(response, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("prepare", help="Read ASC and snapshot the approved local source; no build/upload")
    p.add_argument("--version", required=True)
    p.add_argument("--build-number", help="Default: next unused UTC-date build, checked live")
    p.add_argument("--since", help="Verified previous iOS source commit; never assume an Android tag")
    p.add_argument("--allow-dirty", action="store_true", help="Snapshot all reviewed tracked edits")
    p.set_defaults(func=prepare)
    p = sub.add_parser("build", help="Test, archive, export and inspect a prepared snapshot; no upload")
    p.add_argument("directory")
    p.add_argument("--destination", required=True, help="Explicit Xcode test destination, usually an iOS simulator")
    p.set_defaults(func=build)
    p = sub.add_parser("upload", help="Upload the exact verified IPA once; never submit/release")
    p.add_argument("directory")
    p.add_argument("--confirm", action="store_true")
    p.set_defaults(func=upload)
    p = sub.add_parser("status", help="Read-only local/Apple status")
    p.add_argument("directory", nargs="?")
    p.set_defaults(func=status)
    args = parser.parse_args()
    if args.command in ("build", "upload"):
        with candidate_lock(args.directory):
            args.func(args)
    else:
        args.func(args)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError, ValueError, KeyError) as error:
        print(f"STOP: {error}", file=sys.stderr)
        sys.exit(1)
