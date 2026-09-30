#!/usr/bin/env bash
# Builds the LogicForge .deb from the staged install tree (./gradlew stageLinuxRoot).
#
#   packaging/linux/build-deb.sh <staged-root> <output-dir>
#
# Environment:
#   PACKAGE_VERSION     upstream version (required; from gradle.properties via Gradle)
#   PACKAGE_MAINTAINER  "Name <email>" for the control file (optional)
#   SOURCE_DATE_EPOCH   clamps file times and dates the changelog (optional)
#
# The package is self-contained (private Java runtime + JavaFX under /usr/lib/logicforge),
# so its dependencies are only the system libraries those native files link against, as
# computed by dpkg-shlibdeps. Desktop, MIME and icon caches are refreshed by the dpkg
# triggers of desktop-file-utils, shared-mime-info and the icon theme; no maintainer
# scripts are needed and removing the package removes every file it installed.
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <staged-root> <output-dir>" >&2
    exit 2
fi
staged_root="$(cd "$1" && pwd)"
output_dir="$2"
: "${PACKAGE_VERSION:?PACKAGE_VERSION must be set}"

package=logicforge
maintainer="${PACKAGE_MAINTAINER:-LogicForge contributors <150333378+MichaelWeissDEV@users.noreply.github.com>}"
architecture="$(dpkg --print-architecture)"
# Debian orders "~" before anything, so 1.0.0-rc1 becomes the pre-release 1.0.0~rc1.
deb_version="${PACKAGE_VERSION//-/\~}"
epoch="${SOURCE_DATE_EPOCH:-$(date +%s)}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
tree="$work/$package"
mkdir -p "$tree"
cp -a "$staged_root/." "$tree/"

# ---------------------------------------------------------------- documentation
doc="$tree/usr/share/doc/$package"
mkdir -p "$doc" "$tree/usr/share/lintian/overrides"
cp "$(dirname "${BASH_SOURCE[0]}")/lintian-overrides" "$tree/usr/share/lintian/overrides/$package"
if [[ -f "$tree/usr/share/man/man1/$package.1" ]]; then
    gzip -9n "$tree/usr/share/man/man1/$package.1"
fi
{
    printf '%s (%s) unstable; urgency=medium\n\n' "$package" "$deb_version"
    printf '  * LogicForge %s. The full history is in CHANGELOG.md of the project:\n' "$PACKAGE_VERSION"
    printf '    https://github.com/MichaelWeissDEV/LogicForge/blob/master/CHANGELOG.md\n\n'
    printf ' -- %s  %s\n' "$maintainer" "$(LC_ALL=C date -u -R -d "@$epoch")"
} | gzip -9n > "$doc/changelog.gz"

# ---------------------------------------------------------------- permissions
find "$tree" -type d -exec chmod 0755 {} +
find "$tree" -type f -exec chmod 0644 {} +
chmod 0755 "$tree/usr/lib/$package/bin/LogicForge"
while IFS= read -r -d '' helper; do
    chmod 0755 "$helper"
done < <(find "$tree/usr/lib/$package/lib/runtime/lib" -maxdepth 1 -type f \
    \( -name jspawnhelper -o -name jexec \) -print0)

# ---------------------------------------------------------------- native files
elf_files=()
while IFS= read -r -d '' file; do
    if [[ "$(head -c 4 "$file" | od -An -c | tr -d ' ')" == '177ELF' ]]; then
        elf_files+=("$file")
    fi
done < <(find "$tree/usr/lib/$package" -type f -print0)

# Strip symbol tables the way dh_strip does; the JDK and JavaFX ship them unstripped.
for file in "${elf_files[@]}"; do
    if [[ "$file" == *.so ]]; then
        strip --remove-section=.comment --remove-section=.note --strip-unneeded "$file"
    else
        strip --remove-section=.comment --remove-section=.note "$file"
    fi
done

# ---------------------------------------------------------------- dependencies

mkdir -p "$work/shlibs/debian"
printf 'Source: %s\n\nPackage: %s\nArchitecture: any\n' "$package" "$package" > "$work/shlibs/debian/control"
runtime_lib="$tree/usr/lib/$package/lib/runtime/lib"
shlibs="$(cd "$work/shlibs" && dpkg-shlibdeps -O --ignore-missing-info \
    -l"$runtime_lib" -l"$runtime_lib/server" "${elf_files[@]}" 2>"$work/shlibdeps.log")" || {
    cat "$work/shlibdeps.log" >&2
    exit 1
}
depends="${shlibs#shlibs:Depends=}"
if [[ -z "$depends" || "$depends" == "$shlibs" ]]; then
    echo "build-deb: dpkg-shlibdeps found no dependencies" >&2
    exit 1
fi

# ---------------------------------------------------------------- control
installed_size="$(du -sk --apparent-size "$tree" | cut -f1)"
mkdir -p "$tree/DEBIAN"
cat > "$tree/DEBIAN/control" <<EOF
Package: $package
Version: $deb_version
Architecture: $architecture
Maintainer: $maintainer
Installed-Size: $installed_size
Depends: $depends
Recommends: desktop-file-utils, hicolor-icon-theme, shared-mime-info
Section: electronics
Priority: optional
Homepage: https://github.com/MichaelWeissDEV/LogicForge
Description: digital logic simulator
 LogicForge designs and simulates digital logic circuits with four-state
 signals (0, 1, X and Z), reusable subcircuits, physical 7400-series chip
 packages, a logic analyzer with triggers and the programmable LF-8
 educational computer.
 .
 The package is self-contained: it brings its own private Java runtime and
 JavaFX, so no separate Java installation is needed.
EOF

(cd "$tree" && find . -path ./DEBIAN -prune -o -type f -print0 | sort -z \
    | xargs -0 md5sum | sed 's#  \./#  #' > DEBIAN/md5sums)
chmod 0644 "$tree/DEBIAN/control" "$tree/DEBIAN/md5sums"

# ---------------------------------------------------------------- build
mkdir -p "$output_dir"
deb="$output_dir/${package}_${deb_version}_${architecture}.deb"
rm -f "$output_dir/${package}_"*.deb
SOURCE_DATE_EPOCH="$epoch" dpkg-deb --root-owner-group -Zxz --build "$tree" "$deb" >/dev/null
echo "Built $deb"
