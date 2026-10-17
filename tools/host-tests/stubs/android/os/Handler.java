package android.os;
/** Executes callbacks synchronously in host tests; does not simulate Android thread/lifecycle rules. */
public final class Handler {
    public Handler(Looper looper) {}
    public boolean post(Runnable task) { task.run(); return true; }
}
