#!/usr/bin/env bash
# Smoke tests for the LogicForge Linux packages: a package only counts as working once it has
# been installed, started, and has opened and saved a project.
#
#   scripts/smoke-test-linux.sh image <app-image-dir>   e.g. app/build/jpackage/LogicForge
#   scripts/smoke-test-linux.sh deb   <package.deb>     installs, tests and removes (needs root)
#   scripts/smoke-test-linux.sh snap  <package.snap>    installs, tests and removes (needs root)
#
# Options (before the mode):
#   --example FILE   project used for the open/save checks (default: examples/logic/gates.logic)
#   --keep           do not remove the installed package afterwards
#
# Without a DISPLAY the graphical checks run under xvfb-run. Every check prints PASS or FAIL;
# the exit status is non-zero if any check failed.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
example="$script_dir/../examples/logic/gates.logic"
keep=false
mime_type=application/x-logicforge-project
app_id=dev.logicforge.LogicForge
failures=0

while [[ $# -gt 0 && "$1" == --* ]]; do
    case "$1" in
        --example) example="$2"; shift 2 ;;
        --keep) keep=true; shift ;;
        *) echo "unknown option $1" >&2; exit 2 ;;
    esac
done
if [[ $# -ne 2 ]]; then
    sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
    exit 2
fi
mode="$1"
target="$2"
if [[ ! -f "$example" ]]; then
    echo "smoke-test: example project $example not found (use --example)" >&2
    exit 2
fi

pass() { printf '  PASS  %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
note() { printf '  ....  %s\n' "$1"; }

work="$(mktemp -d)"
cleanup() { rm -rf "$work"; }
trap cleanup EXIT

as_root() {
    if [[ $EUID -eq 0 ]]; then "$@"; else sudo "$@"; fi
}

# Runs a command with a display: the current one, or a private Xvfb server.
with_display() {
    if [[ -n "${DISPLAY:-}" ]]; then
        "$@"
    else
        xvfb-run --auto-servernum --server-args='-screen 0 1600x1000x24' "$@"
    fi
}

# Starts `command...` with a display, waits, and checks the process is still running (a crash
# on start-up or a missing library ends it at once). If a document name is given, also checks
# that a window titled "<document> — LogicForge" is open.
check_starts() {
    local description="$1" document="$2"
    shift 2
    local log="$work/start-$RANDOM.log" started
    # The inner script is expanded by the inner shell, which receives the command as "$@".
    # shellcheck disable=SC2016
    with_display bash -c '
        "$@" &
        app=$!
        sleep 12
        if ! kill -0 "$app" 2>/dev/null; then
            wait "$app"; echo "exited early with status $?"; exit 1
        fi
        if command -v xprop >/dev/null 2>&1 && command -v xwininfo >/dev/null 2>&1; then
            for window in $(xwininfo -root -children 2>/dev/null | awk "/^ +0x/ {print \$1}"); do
                xprop -id "$window" _NET_WM_NAME 2>/dev/null | grep -F LogicForge || true
            done
        fi
        kill "$app"; wait "$app" 2>/dev/null || true
    ' _ "$@" > "$log" 2>&1 && started=true || started=false
    if [[ $started == true ]]; then
        if [[ -n "$document" ]] && command -v xprop >/dev/null 2>&1 \
                && command -v xwininfo >/dev/null 2>&1; then
            # xprop escapes the dash of the title outside UTF-8 locales, so match around it.
            if grep -qE "= \"$document .* LogicForge\"" "$log"; then
                pass "$description (window \"$document — LogicForge\")"
            else
                fail "$description: no window titled \"$document — LogicForge\""
                sed 's/^/        /' "$log"
            fi
        else
            pass "$description"
        fi
    else
        fail "$description"
        sed 's/^/        /' "$log"
    fi
}

# Opens, saves and reopens a copy of the example through the application itself.
check_open_save_reload() {
    local description="$1" project="$2"
    shift 2
    local log="$work/smoke-$RANDOM.log"
    if with_display "$@" --smoke-test "$project" > "$log" 2>&1 \
            && grep -q 'smoke test passed' "$log"; then
        pass "$description"
    else
        fail "$description"
        sed 's/^/        /' "$log"
    fi
    if [[ -f "$project.bak" ]]; then
        pass "saving kept the previous version as $(basename "$project").bak"
    else
        fail "no $(basename "$project").bak next to the saved project"
    fi
}

check_version() {
    local command="$1" expected="$2" actual
    actual="$("$command" --version 2>&1 || true)"
    if [[ "$actual" == "LogicForge $expected" ]]; then
        pass "$command --version prints \"$actual\""
    else
        fail "$command --version printed \"$actual\", expected \"LogicForge $expected\""
    fi
}

check_file() {
    if [[ -e "$1" ]]; then pass "$1"; else fail "missing $1"; fi
}

check_absent() {
    if [[ ! -e "$1" && ! -L "$1" ]]; then pass "removed $1"; else fail "left behind $1"; fi
}

check_mime() {
    local file="$1" detected default
    detected="$(xdg-mime query filetype "$file" 2>/dev/null || true)"
    if [[ "$detected" == "$mime_type" ]]; then
        pass "xdg-mime query filetype $(basename "$file") = $detected"
    else
        fail "xdg-mime query filetype $(basename "$file") = '$detected', expected $mime_type"
    fi
    default="$(xdg-mime query default "$mime_type" 2>/dev/null || true)"
    if [[ "$default" == *"$app_id.desktop"* || "$default" == logicforge_*.desktop ]]; then
        pass "xdg-mime query default $mime_type = $default"
    else
        fail "xdg-mime query default $mime_type = '$default'"
    fi
}

# ---------------------------------------------------------------------------------------
test_image() {
    local image
    image="$(cd "$target" && pwd)"
    echo "== LogicForge app image $image"
    check_file "$image/bin/LogicForge"
    if [[ -x "$image/bin/LogicForge" ]]; then pass "launcher is executable"; else fail "launcher is not executable"; fi
    check_file "$image/lib/runtime/lib/server/libjvm.so"
    if grep -q javafx.controls "$image/lib/runtime/release" 2>/dev/null; then
        pass "private runtime contains JavaFX"
    else
        fail "private runtime lacks JavaFX"
    fi
    local expected
    expected="$(grep -o 'app-version=[^[:space:]]*' "$image/lib/app/LogicForge.cfg" | cut -d= -f2)"
    # No JAVA_HOME and no JDK on the PATH: the image must bring everything itself.
    local clean=(env -u JAVA_HOME -u JDK_HOME -u CLASSPATH -u JAVA_TOOL_OPTIONS
                 "HOME=$work/home" "PATH=/usr/bin:/bin")
    mkdir -p "$work/home/Documents"
    local version
    version="$("${clean[@]}" "$image/bin/LogicForge" --version 2>&1 || true)"
    if [[ "$version" == "LogicForge $expected" ]]; then
        pass "launcher starts without a system Java: \"$version\""
    else
        fail "launcher --version printed \"$version\""
    fi
    check_starts "LogicForge starts" "" "${clean[@]}" "$image/bin/LogicForge"
    cp "$example" "$work/home/Documents/example.logic"
    check_starts "LogicForge opens a project given on the command line" example \
        "${clean[@]}" "$image/bin/LogicForge" "$work/home/Documents/example.logic"
    check_open_save_reload "open, save and reload a project" "$work/home/Documents/example.logic" \
        "${clean[@]}" "$image/bin/LogicForge"
}

test_deb() {
    local deb version
    deb="$(cd "$(dirname "$target")" && pwd)/$(basename "$target")"
    version="$(dpkg-deb --field "$deb" Version)"
    echo "== LogicForge .deb $deb ($version)"
    if DEBIAN_FRONTEND=noninteractive as_root apt-get install -y "$deb" > "$work/install.log" 2>&1; then
        pass "apt install ./$(basename "$deb")"
    else
        fail "apt install ./$(basename "$deb")"
        sed 's/^/        /' "$work/install.log"
        return
    fi
    if [[ "$(command -v logicforge)" == /usr/bin/logicforge ]]; then
        pass "logicforge is on the PATH"
    else
        fail "logicforge is not on the PATH"
    fi
    check_version logicforge "${version//\~/-}"
    check_file "/usr/share/applications/$app_id.desktop"
    check_file "/usr/share/metainfo/$app_id.metainfo.xml"
    check_file "/usr/share/mime/packages/$app_id.xml"
    check_file "/usr/share/icons/hicolor/256x256/apps/$app_id.png"
    if grep -q "^$mime_type:\*\.logic$" /usr/share/mime/globs2 2>/dev/null \
            || grep -q "$mime_type:\*\.logic" /usr/share/mime/globs2 2>/dev/null; then
        pass "the shared MIME database knows *.logic"
    else
        fail "the shared MIME database was not updated (is shared-mime-info installed?)"
    fi

    mkdir -p "$HOME/Documents"
    local project="$HOME/Documents/example.logic"
    cp "$example" "$project"
    rm -f "$project.bak"
    check_mime "$project"
    check_starts "logicforge starts" "" logicforge
    check_starts "logicforge example.logic opens the project" example logicforge "$project"
    check_open_save_reload "open, save and reload ~/Documents/example.logic" "$project" logicforge

    if [[ $keep == true ]]; then
        note "package kept installed (--keep)"
        return
    fi
    if DEBIAN_FRONTEND=noninteractive as_root apt-get remove -y logicforge > "$work/remove.log" 2>&1; then
        pass "apt remove logicforge"
    else
        fail "apt remove logicforge"
        sed 's/^/        /' "$work/remove.log"
    fi
    check_absent /usr/bin/logicforge
    check_absent /usr/lib/logicforge
    check_absent "/usr/share/applications/$app_id.desktop"
    check_absent "/usr/share/mime/packages/$app_id.xml"
    if ! grep -q "$mime_type" /usr/share/mime/globs2 2>/dev/null; then
        pass "the MIME type is gone from the shared MIME database"
    else
        fail "the MIME type is still registered after removal"
    fi
}

test_snap() {
    local snap
    snap="$(cd "$(dirname "$target")" && pwd)/$(basename "$target")"
    echo "== LogicForge snap $snap"
    if as_root snap install --dangerous "$snap" > "$work/install.log" 2>&1; then
        pass "snap install --dangerous $(basename "$snap")"
    else
        fail "snap install --dangerous $(basename "$snap")"
        sed 's/^/        /' "$work/install.log"
        return
    fi
    local version
    version="$(snap info --verbose logicforge 2>/dev/null | awk '/^installed:/ {print $2}')"
    check_version /snap/bin/logicforge "$version"
    snap connections logicforge | sed 's/^/        /'
    if snap connections logicforge | grep -q '^home .*:home'; then
        pass "the home interface is connected"
    else
        fail "the home interface is not connected"
    fi
    mkdir -p "$HOME/Documents"
    local project="$HOME/Documents/example.logic"
    cp "$example" "$project"
    rm -f "$project.bak"
    check_starts "logicforge (snap) starts" "" /snap/bin/logicforge
    check_starts "logicforge (snap) opens ~/Documents/example.logic" example \
        /snap/bin/logicforge "$project"
    check_open_save_reload "open, save and reload ~/Documents/example.logic under strict confinement" \
        "$project" /snap/bin/logicforge
    if [[ $keep == true ]]; then
        note "snap kept installed (--keep)"
        return
    fi
    if as_root snap remove --purge logicforge > "$work/remove.log" 2>&1; then
        pass "snap remove logicforge"
    else
        fail "snap remove logicforge"
    fi
}

case "$mode" in
    image) test_image ;;
    deb) test_deb ;;
    snap) test_snap ;;
    *) echo "unknown mode '$mode' (image, deb or snap)" >&2; exit 2 ;;
esac

echo
if [[ $failures -gt 0 ]]; then
    echo "smoke-test: $failures check(s) failed"
    exit 1
fi
echo "smoke-test: all checks passed"
