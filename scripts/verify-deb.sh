#!/usr/bin/env bash
# Checks a LogicForge .deb without installing it:
#   dpkg-deb --info / --contents, the files a desktop installation needs, no Java package
#   dependency, lintian (if installed), desktop-file-validate and appstreamcli validate on the
#   packaged metadata, and a shared-mime-info build of the packaged MIME type.
#
#   scripts/verify-deb.sh build/packages/logicforge_<version>_<arch>.deb
#
# Exit status 0 means every available check passed. Missing optional tools (lintian) are
# reported as skipped; missing required tools fail the check.
set -euo pipefail

if [[ $# -ne 1 || ! -f "$1" ]]; then
    echo "usage: $0 <package.deb>" >&2
    exit 2
fi
deb="$1"
app_id=dev.logicforge.LogicForge
failures=0

pass() { printf '  PASS  %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
skip() { printf '  SKIP  %s\n' "$1"; }
require_tool() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "verify-deb: '$1' is required (package: $2)" >&2
        exit 2
    fi
}

require_tool dpkg-deb dpkg
require_tool desktop-file-validate desktop-file-utils
require_tool appstreamcli appstream
require_tool update-mime-database shared-mime-info

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "== dpkg-deb --info"
dpkg-deb --info "$deb"
echo "== dpkg-deb --contents (outside the private runtime)"
dpkg-deb --contents "$deb" | awk '{print $6, $7, $8}' | grep -v '/lib/runtime/' || true

echo "== checks"
package="$(dpkg-deb --field "$deb" Package)"
version="$(dpkg-deb --field "$deb" Version)"
if [[ "$package" == logicforge ]]; then
    pass "package name is logicforge"
else
    fail "package name is '$package'"
fi

depends="$(dpkg-deb --field "$deb" Depends)"
if grep -Eiq '(openjdk|default-j(re|dk)|java[0-9]*-runtime|openjfx|libopenjfx)' <<< "$depends"; then
    fail "the package must not depend on a system Java or JavaFX: $depends"
else
    pass "no Java or JavaFX package dependency"
fi

dpkg-deb --extract "$deb" "$work/root"
root="$work/root"
required=(
    "usr/lib/logicforge/bin/LogicForge"
    "usr/lib/logicforge/lib/app/LogicForge.cfg"
    "usr/lib/logicforge/lib/runtime/lib/server/libjvm.so"
    "usr/lib/logicforge/lib/runtime/lib/libglassgtk3.so"
    "usr/lib/logicforge/lib/runtime/lib/libprism_es2.so"
    "usr/share/applications/$app_id.desktop"
    "usr/share/metainfo/$app_id.metainfo.xml"
    "usr/share/mime/packages/$app_id.xml"
    "usr/share/icons/hicolor/48x48/apps/$app_id.png"
    "usr/share/icons/hicolor/128x128/apps/$app_id.png"
    "usr/share/icons/hicolor/256x256/apps/$app_id.png"
    "usr/share/icons/hicolor/512x512/apps/$app_id.png"
    "usr/share/doc/logicforge/copyright"
    "usr/share/doc/logicforge/changelog.gz"
    "usr/share/man/man1/logicforge.1.gz"
)
for file in "${required[@]}"; do
    if [[ -f "$root/$file" ]]; then
        pass "$file"
    else
        fail "missing $file"
    fi
done

if [[ -L "$root/usr/bin/logicforge" && "$(readlink "$root/usr/bin/logicforge")" == ../lib/logicforge/bin/LogicForge ]]; then
    pass "usr/bin/logicforge links to the launcher"
else
    fail "usr/bin/logicforge is not a link to ../lib/logicforge/bin/LogicForge"
fi
if [[ -x "$root/usr/lib/logicforge/bin/LogicForge" ]]; then
    pass "launcher is executable"
else
    fail "launcher is not executable"
fi

if grep -q -- '--enable-native-access=javafx.graphics' "$root/usr/lib/logicforge/lib/app/LogicForge.cfg"; then
    pass "launcher grants JavaFX native access"
else
    fail "LogicForge.cfg lacks --enable-native-access=javafx.graphics"
fi
if grep -q 'javafx.controls' "$root/usr/lib/logicforge/lib/runtime/release"; then
    pass "private runtime contains JavaFX"
else
    fail "private runtime lacks the JavaFX modules"
fi

if output="$(desktop-file-validate "$root/usr/share/applications/$app_id.desktop" 2>&1)" && [[ -z "$output" ]]; then
    pass "desktop-file-validate"
else
    fail "desktop-file-validate: $output"
fi

if appstreamcli validate --no-net "$root/usr/share/metainfo/$app_id.metainfo.xml"; then
    pass "appstreamcli validate"
else
    fail "appstreamcli validate"
fi
upstream_version="${version//\~/-}"
if grep -q "<release version=\"$upstream_version\"" "$root/usr/share/metainfo/$app_id.metainfo.xml"; then
    pass "AppStream release entry matches version $upstream_version"
else
    fail "AppStream metadata has no release entry for $upstream_version"
fi

mkdir -p "$work/mime/packages"
cp "$root/usr/share/mime/packages/$app_id.xml" "$work/mime/packages/"
if update-mime-database "$work/mime" >/dev/null 2>&1 \
        && grep -q 'application/x-logicforge-project:\*\.logic' "$work/mime/globs2"; then
    pass "MIME type application/x-logicforge-project claims *.logic"
else
    fail "the MIME definition does not build or does not claim *.logic"
fi

if command -v lintian >/dev/null 2>&1; then
    if lintian --fail-on error,warning --info "$deb"; then
        pass "lintian (no errors or warnings)"
    else
        fail "lintian reported errors or warnings"
    fi
else
    skip "lintian is not installed"
fi

echo
if [[ $failures -gt 0 ]]; then
    echo "verify-deb: $failures check(s) failed for $deb"
    exit 1
fi
echo "verify-deb: all checks passed for $deb"
