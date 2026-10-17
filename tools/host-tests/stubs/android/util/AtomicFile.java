package android.util;
import java.io.*;
import java.nio.file.*;
/** File-system fixture for exercising the real store's transaction/error behavior. */
public final class AtomicFile {
    public static volatile boolean failNextWrite;
    private final File base;
    private final File pending;
    public AtomicFile(File base) { this.base = base; pending = new File(base + ".new"); }
    public File getBaseFile() { return base; }
    public FileInputStream openRead() throws IOException { return new FileInputStream(base); }
    public FileOutputStream startWrite() throws IOException {
        if (failNextWrite) { failNextWrite = false; throw new IOException("Injected write failure"); }
        base.getParentFile().mkdirs();
        return new FileOutputStream(pending);
    }
    public void finishWrite(FileOutputStream output) throws IOException {
        output.getFD().sync(); output.close();
        Files.move(pending.toPath(), base.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    public void failWrite(FileOutputStream output) { try { output.close(); } catch (IOException ignored) {} pending.delete(); }
}
