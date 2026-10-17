package android.content;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
/** Minimal host fixture. Android lifecycle/notification tests use Robolectric instead. */
public class Context {
    public static final int MODE_PRIVATE = 0;
    private final File files;
    private final Map<String, Boolean> preferences = new HashMap<>();
    public Context(File files) { this.files = files; }
    public Context getApplicationContext() { return this; }
    public File getFilesDir() { return files; }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return new SharedPreferences() {
            public boolean getBoolean(String key, boolean fallback) { return preferences.getOrDefault(name + key, fallback); }
            public Editor edit() { return new Editor() {
                public Editor putBoolean(String key, boolean value) { preferences.put(name + key, value); return this; }
                public void apply() {}
            }; }
        };
    }
}
