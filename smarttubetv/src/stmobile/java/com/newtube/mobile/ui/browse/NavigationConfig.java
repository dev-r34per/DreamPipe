package com.newtube.mobile.ui.browse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stable destination IDs and ordered navigation policy, independent of Android view state. */
public final class NavigationConfig {
    public static final int LOCAL_PLAYLISTS = 32_001;
    public static final int LOCAL_HISTORY = 32_002;
    public static final int YOU = 32_003;
    public static final int MAX_TABS = 4;
    // MediaGroup.TYPE_HOME is 0. Keep the pure policy usable in host-side regression tests.
    public static final List<Integer> DEFAULTS = Collections.unmodifiableList(Arrays.asList(0, LOCAL_PLAYLISTS, LOCAL_HISTORY));

    private NavigationConfig() {}

    public static List<Integer> parse(String saved) {
        List<Integer> result = new ArrayList<>();
        if (saved != null) {
            for (String token : saved.split(",")) {
                try {
                    int id = Integer.parseInt(token);
                    if (id >= 0 && !result.contains(id) && result.size() < MAX_TABS) result.add(id);
                } catch (NumberFormatException ignored) { }
            }
        }
        return result.isEmpty() ? new ArrayList<>(DEFAULTS) : result;
    }

    public static List<Integer> available(List<Integer> saved, Set<Integer> available) {
        List<Integer> result = new ArrayList<>();
        for (int id : saved) if (available.contains(id) && !result.contains(id) && result.size() < MAX_TABS) result.add(id);
        if (result.isEmpty()) {
            for (int id : DEFAULTS) if (available.contains(id)) result.add(id);
        }
        return result;
    }

    public static String encode(List<Integer> ids) {
        if (ids.isEmpty() || ids.size() > MAX_TABS || new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Choose between one and four different destinations");
        }
        StringBuilder saved = new StringBuilder();
        for (int id : ids) {
            if (id < 0) throw new IllegalArgumentException("Invalid destination");
            if (saved.length() > 0) saved.append(',');
            saved.append(id);
        }
        return saved.toString();
    }
}
