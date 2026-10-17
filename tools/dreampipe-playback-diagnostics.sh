#!/usr/bin/env bash
# Read-only playback/task-removal evidence. Run after reproducing a playback stop.
set -Eeuo pipefail
serial="${DREAMPIPE_ADB_SERIAL:-$(adb -d get-serialno)}"
output="$HOME/.down/DreamPipe-1.0.4-playback.txt"
mkdir -p "$HOME/.down"
{
    printf 'DREAMPIPE PLAYBACK LIFECYCLE\n'
    adb -s "$serial" logcat -d -v threadtime 'DreamPipePlayback:I' '*:S'
    printf '\nPLAYBACK SERVICES\n'
    adb -s "$serial" shell dumpsys activity services app.dreampipe.debug
    printf '\nPROCESS EXIT REASONS\n'
    adb -s "$serial" shell dumpsys activity exit-info app.dreampipe.debug
} > "$output" 2>&1
printf 'Playback diagnostics saved: %s\n' "$output"
