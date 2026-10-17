#!/usr/bin/env bash
# Portable policy/codec/store tests; Android lifecycle tests remain in src/test for Robolectric.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d "${TMPDIR:-/tmp}/dreampipe-tests.XXXXXX")"
trap 'rm -rf -- "$test_dir"' EXIT
json_dir="${1:-}"
if [[ -z "$json_dir" ]]; then
    json_dir="$test_dir/json-java"
    git clone --quiet https://github.com/stleary/JSON-java.git "$json_dir"
    git -C "$json_dir" checkout --quiet 69383f746d971fd26bb4fe21524295dc385d233f
fi
test_sources=()
while IFS= read -r source; do
    test_sources+=("$source")
done < <(find "$json_dir/src/main/java" "$project_dir/tools/host-tests" -type f -name '*.java')
phone_src="$project_dir/smarttubetv/src/stmobile/java/com/newtube/mobile"
common_src="$project_dir/common/src/main/java/com/liskovsoft/smartyoutubetv2/common"
test_sources+=(
    "$phone_src/library/LocalVideo.java" "$phone_src/library/LocalPlaylist.java"
    "$phone_src/library/LocalLibraryQuery.java" "$phone_src/library/LocalPlaybackLauncher.java"
    "$phone_src/library/LocalVideos.java"
    "$phone_src/library/PlaylistArchive.java" "$phone_src/library/LocalPlaylistStore.java"
    "$phone_src/library/LocalHistoryStore.java" "$phone_src/ui/browse/NavigationConfig.java"
    "$phone_src/ui/playback/WatchSwipeGesture.java" "$phone_src/ui/common/RowLimitPolicy.java" "$common_src/app/models/playback/DevicePlaylistQueue.java"
    "$common_src/app/models/playback/manager/PlayerConstants.java"
)
java com.sun.tools.javac.Main -d "$test_dir/classes" "${test_sources[@]}"
java -cp "$test_dir/classes" DreamPipeRegression
