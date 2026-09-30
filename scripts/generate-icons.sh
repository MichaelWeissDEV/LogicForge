#!/usr/bin/env bash
# Regenerates the application icon set in packaging/linux/icons/hicolor from the original
# logo. The generated PNGs are committed, so this only needs to run when the logo changes.
#
# Requires ImageMagick 6 (convert) or 7 (magick). Optionally uses optipng if installed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_logo="$root/packaging/linux/icons/source/logicforge-logo.png"
target="$root/packaging/linux/icons/hicolor"
sizes=(16 24 32 48 64 128 256 512)

if command -v magick >/dev/null 2>&1; then
    im=(magick)
elif command -v convert >/dev/null 2>&1; then
    im=(convert)
else
    echo "generate-icons: ImageMagick (magick or convert) is required" >&2
    exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Crop to the visible logo, make it square on a transparent canvas and leave the small
# margin the freedesktop icon guidelines recommend (the artwork fills ~94 %).
"${im[@]}" "$source_logo" -trim +repage "$work/trimmed.png"
dimensions="$("${im[@]}" "$work/trimmed.png" -format '%w %h' info:)"
read -r width height <<< "$dimensions"
side=$(( width > height ? width : height ))
"${im[@]}" "$work/trimmed.png" -background none -gravity center -extent "${side}x${side}" \
    -resize 962x962 -extent 1024x1024 "$work/master.png"

for size in "${sizes[@]}"; do
    dir="$target/${size}x${size}/apps"
    mkdir -p "$dir"
    "${im[@]}" "$work/master.png" -filter Lanczos -resize "${size}x${size}" \
        -strip -define png:color-type=6 "$dir/dev.logicforge.LogicForge.png"
    if command -v optipng >/dev/null 2>&1; then
        optipng -quiet -o5 "$dir/dev.logicforge.LogicForge.png"
    fi
done

echo "Generated ${#sizes[@]} icon sizes in $target"
