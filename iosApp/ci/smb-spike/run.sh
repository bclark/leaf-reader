#!/bin/bash
set -euo pipefail
# Usage: bash iosApp/ci/smb-spike/run.sh /absolute/path/to/private.env
# The file is parsed as data, never sourced as shell code. No secrets are logged.
spike_source="$(cd "$(dirname "$0")" && pwd)"
ios_source="$(cd "$spike_source/../.." && pwd)"
spike_work="$(mktemp -d "${TMPDIR:-/tmp}/leaf-smb-spike.XXXXXX")"
trap 'rm -rf "$spike_work"' EXIT
mkdir -p "$spike_work/Sources/Spike"
cp "$spike_source/Package.swift" "$spike_work/Package.swift"
cp "$spike_source/LiveSpike.swift" "$spike_work/Sources/Spike/"
for source in SmbTransport SmbCredentials OperationCancellation NativeSmbSession; do
    cp "$ios_source/Sources/$source.swift" "$spike_work/Sources/Spike/"
done
swift run --package-path "$spike_work" Spike "$1"
