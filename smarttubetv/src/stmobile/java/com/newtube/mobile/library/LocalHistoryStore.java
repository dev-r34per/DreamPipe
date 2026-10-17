package com.newtube.mobile.library;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Device-local recent videos, independent of sign-in and YouTube's watch-history setting. */
public final class LocalHistoryStore {
    public interface Listener { void onHistoryChanged(); }
    private static volatile LocalHistoryStore sInstance;
    private static final int MAX_ENTRIES = 2000;
    private final Context mContext;
    private final AtomicFile mFile;
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "DreamPipe-history"));
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private volatile List<LocalVideo> mSnapshot = Collections.emptyList();
    private volatile Exception mError;

    public static LocalHistoryStore instance(Context context) {
        if (sInstance == null) synchronized (LocalHistoryStore.class) {
            if (sInstance == null) sInstance = new LocalHistoryStore(context.getApplicationContext());
        }
        return sInstance;
    }

    private LocalHistoryStore(Context context) {
        mContext = context;
        mFile = new AtomicFile(new File(context.getFilesDir(), "local_history/recent.json"));
        mWorker.execute(() -> {
            try {
                if (mFile.getBaseFile().exists() || new File(mFile.getBaseFile() + ".bak").exists()) {
                    try (FileInputStream input = mFile.openRead()) {
                        JSONArray array = new JSONArray(PlaylistArchive.read(input));
                        List<LocalVideo> entries = new ArrayList<>();
                        for (int i = 0; i < Math.min(array.length(), MAX_ENTRIES); i++) {
                            entries.add(PlaylistArchive.decodeVideo(array.getJSONObject(i)));
                        }
                        mSnapshot = Collections.unmodifiableList(entries);
                    }
                }
            } catch (Exception error) { mError = error; }
            mMain.post(this::notifyListeners);
        });
    }

    public List<LocalVideo> snapshot() { return mSnapshot; }
    public Exception error() { return mError; }
    public void addListener(Listener listener) { mListeners.add(listener); }
    public void removeListener(Listener listener) { mListeners.remove(listener); }
    public boolean isEnabled() {
        return mContext.getSharedPreferences("dreampipe_library", Context.MODE_PRIVATE).getBoolean("record_history", true);
    }
    public void setEnabled(boolean enabled) {
        mContext.getSharedPreferences("dreampipe_library", Context.MODE_PRIVATE).edit().putBoolean("record_history", enabled).apply();
    }

    public void record(LocalVideo video) {
        if (!isEnabled()) return;
        change(video, null, false);
    }
    public void remove(String id) { change(null, id, false); }
    public void clear() { change(null, null, true); }

    private void change(LocalVideo video, String remove, boolean clear) {
        mWorker.execute(() -> {
            // An explicit Clear can recover a damaged history; a watch must never silently overwrite it.
            if (mError != null && !clear) return;
            List<LocalVideo> next = clear ? new ArrayList<>() : new ArrayList<>(mSnapshot);
            if (video != null) {
                // Same-video metadata refreshes update its entry without changing recency.
                int previous = next.indexOf(video);
                if (previous == 0) next.set(0, video);
                else { next.remove(video); next.add(0, video); }
            }
            if (remove != null) next.removeIf(entry -> entry.id.equals(remove));
            if (next.size() > MAX_ENTRIES) next.subList(MAX_ENTRIES, next.size()).clear();
            FileOutputStream output = null;
            try {
                JSONArray array = new JSONArray();
                for (LocalVideo entry : next) array.put(PlaylistArchive.encodeVideo(entry));
                output = mFile.startWrite();
                output.write(array.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
                mFile.finishWrite(output);
                mSnapshot = Collections.unmodifiableList(next);
                mError = null;
            } catch (Exception error) {
                if (output != null) mFile.failWrite(output);
                // A transient failed write must not permanently disable future history saves.
                // Keep an initial read error until the user explicitly clears/recoveries it.
                android.util.Log.w("DreamPipeHistory", "Could not persist local watch history", error);
            }
            mMain.post(this::notifyListeners);
        });
    }

    private void notifyListeners() { for (Listener listener : mListeners) listener.onHistoryChanged(); }
}
