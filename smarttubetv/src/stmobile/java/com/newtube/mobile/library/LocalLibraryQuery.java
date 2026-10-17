package com.newtube.mobile.library;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure projections over immutable snapshots. Called on a worker, never changes stored order. */
public final class LocalLibraryQuery {
    private LocalLibraryQuery() {}

    public static final class Result<T> {
        public final int total;
        public final List<T> items;
        private Result(List<T> matches, int limit) {
            total = matches.size();
            items = Collections.unmodifiableList(new ArrayList<>(matches.subList(0, Math.min(total, Math.max(0, limit)))));
        }
    }

    public static Result<LocalPlaylist> playlists(List<LocalPlaylist> source, String query, int sort, int limit) {
        String needle = normalise(query);
        List<LocalPlaylist> matches = new ArrayList<>();
        for (LocalPlaylist playlist : source) {
            if (Thread.currentThread().isInterrupted()) return new Result<>(Collections.emptyList(), 0);
            if (normalise(playlist.name).contains(needle)) matches.add(playlist);
        }
        Collator collator = Collator.getInstance();
        Comparator<LocalPlaylist> order = sort == 1 ? Comparator.comparingLong((LocalPlaylist p) -> p.createdAt).reversed()
                : sort == 2 ? Comparator.comparingLong((LocalPlaylist p) -> p.updatedAt).reversed()
                : (a, b) -> collator.compare(a.name, b.name);
        if (sort != 3) matches.sort(order.thenComparing(p -> p.id));
        return new Result<>(matches, limit);
    }

    public static Result<LocalVideo> videos(List<LocalVideo> source, String query, int sort, int limit) {
        String needle = normalise(query);
        List<LocalVideo> matches = new ArrayList<>();
        for (LocalVideo video : source) {
            if (Thread.currentThread().isInterrupted()) return new Result<>(Collections.emptyList(), 0);
            if (normalise(video.title + " " + video.author).contains(needle)) matches.add(video);
        }
        Collator collator = Collator.getInstance();
        if (sort == 1) matches.sort((a, b) -> collator.compare(a.title, b.title));
        if (sort == 2) matches.sort(Comparator.comparingLong((LocalVideo v) -> v.durationMs).reversed());
        return new Result<>(matches, limit);
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
