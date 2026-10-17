package com.newtube.mobile.library;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** Portable metadata only. Expiring media URLs, cookies and account state never enter an archive. */
public final class LocalVideo {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    public final String id;
    public final String title;
    public final String author;
    public final String thumbnail;
    public final long durationMs;

    public LocalVideo(String id, String title, String author, String thumbnail, long durationMs) {
        if (id == null || !VIDEO_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid YouTube video ID");
        }
        this.id = id;
        this.title = clean(title, 1000).isEmpty() ? id : clean(title, 1000);
        this.author = clean(author, 500);
        String image = clean(thumbnail, 4096);
        this.thumbnail = safeThumbnail(image) ? image : "";
        this.durationMs = Math.max(0, durationMs);
    }

    public String url() {
        return "https://www.youtube.com/watch?v=" + id;
    }

    private static boolean safeThumbnail(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) return false;
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("ytimg.com") || host.endsWith(".ytimg.com")
                    || host.equals("ggpht.com") || host.endsWith(".ggpht.com")
                    || host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com");
        } catch (IllegalArgumentException ignored) { return false; }
    }

    /** Recognises public YouTube links; never follows a URL from an imported file. */
    public static String idFromUrl(String value) {
        if (value == null) return null;
        String input = value.trim();
        if (VIDEO_ID.matcher(input).matches()) return input;
        try {
            URI uri = URI.create(input);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            String id = null;
            if ("youtu.be".equals(host) || "www.youtu.be".equals(host)) {
                id = uri.getPath().substring(1).split("/", 2)[0];
            } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")
                    || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com")) {
                String path = uri.getPath();
                if ("/watch".equals(path) && uri.getRawQuery() != null) {
                    for (String pair : uri.getRawQuery().split("&")) {
                        String[] parts = pair.split("=", 2);
                        if (parts.length == 2 && "v".equals(parts[0])) {
                            id = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());
                            break;
                        }
                    }
                } else if (path != null && (path.startsWith("/shorts/") || path.startsWith("/live/")
                        || path.startsWith("/embed/"))) {
                    id = path.split("/")[2];
                }
            }
            return id != null && VIDEO_ID.matcher(id).matches() ? id : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static String clean(String value, int maximum) {
        if (value == null) return "";
        String result = value.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").trim();
        return result.length() > maximum ? result.substring(0, maximum) : result;
    }

    @Override public boolean equals(Object other) {
        return other instanceof LocalVideo && id.equals(((LocalVideo) other).id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
