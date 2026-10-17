package com.newtube.mobile.library;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** DreamPipe's documented, versioned JSON archive and portable extended-M3U interchange. */
public final class PlaylistArchive {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_PLAYLISTS = 1000;
    public static final int MAX_VIDEOS = 50_000;
    public static final String FORMAT = "dreampipe.playlists";

    private PlaylistArchive() {}

    public static String read(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("Playlist file exceeds 8 MiB");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String encode(List<LocalPlaylist> playlists) throws JSONException {
        JSONArray array = new JSONArray();
        for (LocalPlaylist playlist : playlists) {
            JSONObject object = new JSONObject();
            object.put("id", playlist.id);
            object.put("name", playlist.name);
            object.put("createdAt", playlist.createdAt);
            object.put("updatedAt", playlist.updatedAt);
            if (!playlist.thumbnailVideoId.isEmpty()) object.put("thumbnailVideoId", playlist.thumbnailVideoId);
            JSONArray videos = new JSONArray();
            for (LocalVideo video : playlist.videos) videos.put(encodeVideo(video));
            object.put("videos", videos);
            array.put(object);
        }
        return new JSONObject().put("format", FORMAT).put("version", 1).put("playlists", array).toString(2) + "\n";
    }

    public static List<LocalPlaylist> decode(String text, String fallbackName, long now)
            throws IOException, JSONException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("Invalid or oversized playlist file");
        }
        String value = text.startsWith("\uFEFF") ? text.substring(1).trim() : text.trim();
        if (value.startsWith("#EXTM3U") || value.startsWith("#EXTINF:")
                || value.startsWith("https://") || value.startsWith("http://")) {
            return Collections.singletonList(decodeM3u(value, fallbackName, now));
        }
        // Reject deeply nested hostile JSON before the platform parser can recurse into it.
        checkNesting(value);
        JSONObject root = new JSONObject(value);
        if (!FORMAT.equals(root.optString("format")) || root.getInt("version") != 1) {
            throw new IOException("Unsupported playlist format or version");
        }
        JSONArray array = root.getJSONArray("playlists");
        if (array.length() > MAX_PLAYLISTS) throw new IOException("Too many playlists");
        List<LocalPlaylist> result = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        int total = 0;
        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                JSONArray entries = object.getJSONArray("videos");
                total += entries.length();
                if (total > MAX_VIDEOS) throw new IOException("Too many video entries");
                List<LocalVideo> videos = new ArrayList<>();
                for (int j = 0; j < entries.length(); j++) videos.add(decodeVideo(entries.getJSONObject(j)));
                LocalPlaylist playlist = new LocalPlaylist(object.getString("id"), object.getString("name"),
                        object.optLong("createdAt", now), object.optLong("updatedAt", now), videos,
                        object.optString("thumbnailVideoId", ""));
                if (!ids.add(playlist.id)) throw new IOException("Duplicate playlist identity in archive");
                result.add(playlist);
            }
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid playlist metadata", error);
        }
        return Collections.unmodifiableList(result);
    }

    public static JSONObject encodeVideo(LocalVideo video) throws JSONException {
        return new JSONObject().put("id", video.id).put("title", video.title).put("author", video.author)
                .put("thumbnail", video.thumbnail).put("durationMs", video.durationMs);
    }

    public static LocalVideo decodeVideo(JSONObject object) throws JSONException {
        return new LocalVideo(object.getString("id"), object.optString("title"), object.optString("author"),
                object.optString("thumbnail"), object.optLong("durationMs", 0));
    }

    public static String encodeM3u(LocalPlaylist playlist) {
        StringBuilder result = new StringBuilder("#EXTM3U\n#PLAYLIST:").append(playlist.name).append('\n');
        for (LocalVideo video : playlist.videos) {
            result.append("#EXTINF:").append(video.durationMs > 0 ? video.durationMs / 1000 : -1)
                    .append(',').append(video.title).append('\n').append(video.url()).append('\n');
        }
        return result.toString();
    }

    private static LocalPlaylist decodeM3u(String text, String fallbackName, long now) throws IOException {
        String name = LocalVideo.clean(fallbackName, 200);
        if (name.isEmpty()) name = "Imported playlist";
        List<LocalVideo> videos = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String line;
        String title = "";
        long duration = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#PLAYLIST:")) {
                String supplied = LocalVideo.clean(line.substring(10), 200);
                if (!supplied.isEmpty()) name = supplied;
            } else if (line.startsWith("#EXTINF:")) {
                int comma = line.indexOf(',');
                if (comma < 0) throw new IOException("Invalid M3U metadata");
                title = line.substring(comma + 1);
                try {
                    long seconds = Long.parseLong(line.substring(8, comma));
                    duration = seconds > 0 && seconds <= Long.MAX_VALUE / 1000 ? seconds * 1000 : 0;
                } catch (NumberFormatException ignored) { duration = 0; }
            } else if (!line.startsWith("#")) {
                String id = LocalVideo.idFromUrl(line);
                if (id == null) throw new IOException("M3U contains an unsupported video link");
                videos.add(new LocalVideo(id, title, "", "", duration));
                if (videos.size() > MAX_VIDEOS) throw new IOException("Too many video entries");
                title = "";
                duration = 0;
            }
        }
        if (videos.isEmpty() && !text.startsWith("#EXTM3U")) throw new IOException("No YouTube videos found in M3U");
        return new LocalPlaylist(UUID.randomUUID().toString(), name, now, now, videos);
    }

    private static void checkNesting(String value) throws IOException {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 16) throw new IOException("Playlist JSON is nested too deeply");
            } else if (c == '}' || c == ']') depth--;
        }
    }
}
