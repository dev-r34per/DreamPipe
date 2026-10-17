import android.content.Context;
import android.util.AtomicFile;
import com.newtube.mobile.library.*;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Exercises actual serial store transactions and error handling using host Android I/O fixtures. */
public final class LocalStoreRegression {
    private static int checks;
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static <T> T await(Consumer<LocalPlaylistStore.Result<T>> operation) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        operation.accept(new LocalPlaylistStore.Result<T>() {
            public void onSuccess(T value) { result.complete(value); }
            public void onError(Exception error) { result.completeExceptionally(error); }
        });
        return result.get(5, TimeUnit.SECONDS);
    }
    private static ExecutorService worker(Object store) throws Exception {
        Field field = store.getClass().getDeclaredField("mWorker"); field.setAccessible(true);
        return (ExecutorService) field.get(store);
    }
    private static LocalPlaylistStore reopen(Context context) throws Exception {
        var constructor = LocalPlaylistStore.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true); return constructor.newInstance(context);
    }
    private static LocalVideo video(char id) { return new LocalVideo(String.valueOf(id).repeat(11), "Track " + id, "Artist", "", 10_000); }
    public static void run() throws Exception {
        File directory = Files.createTempDirectory("dreampipe-store-regression-").toFile();
        Context context = new Context(directory);
        LocalPlaylistStore store = reopen(context);
        LocalPlaylistStore recovered = null;
        LocalHistoryStore history = null;
        try {
            check(LocalStoreRegression.<List<LocalPlaylist>>await(store::read).isEmpty(), "new store is empty");
            LocalPlaylist first = LocalStoreRegression.<LocalPlaylist>await(r -> store.createWithVideos("First", Collections.singletonList(video('a')), r));
            check(store.find(first.id).videos.size() == 1, "create-and-save commits together");
            check(LocalStoreRegression.<Boolean>await(r -> store.containsVideo(video('a').id, r)), "saved indicator finds membership across the library");
            check(!LocalStoreRegression.<Boolean>await(r -> store.containsVideo(video('z').id, r)), "saved indicator is false for an absent video");
            CompletableFuture<Void> firstAdd = new CompletableFuture<>();
            store.addVideos(first.id, Collections.singletonList(video('b')), new LocalPlaylistStore.Result<Void>() {
                public void onSuccess(Void value) { firstAdd.complete(null); }
                public void onError(Exception error) { firstAdd.completeExceptionally(error); }
            });
            LocalStoreRegression.<Void>await(r -> store.addVideos(first.id, Arrays.asList(video('c'), video('a')), r));
            firstAdd.get(5, TimeUnit.SECONDS);
            check(store.find(first.id).videos.size() == 3, "queued mutations do not overwrite each other");
            File file = new File(directory, "local_playlists/library.json");
            byte[] committed = Files.readAllBytes(file.toPath());
            List<LocalPlaylist> snapshot = store.snapshot();
            AtomicFile.failNextWrite = true;
            try {
                LocalStoreRegression.<Void>await(r -> store.rename(first.id, "Must not commit", r));
                throw new AssertionError("write failure accepted");
            } catch (ExecutionException expected) { checks++; }
            check(store.snapshot() == snapshot, "failed write does not publish a new snapshot");
            check(Arrays.equals(committed, Files.readAllBytes(file.toPath())), "failed write keeps committed bytes");
            LocalStoreRegression.<Void>await(r -> store.rename(first.id, "First renamed", r));
            check(store.find(first.id).name.equals("First renamed"), "write can be retried");
            LocalPlaylist second = LocalStoreRegression.<LocalPlaylist>await(r -> store.createWithVideos("Second", Arrays.asList(video('b'), video('d')), r));
            LocalStoreRegression.<LocalPlaylist>await(r -> store.merge(first.id, Collections.singletonList(second.id), r));
            check(store.find(first.id).videos.size() == 4 && store.find(second.id).videos.size() == 2, "merge keeps sources and skips overlap");
            LocalPlaylist external = store.find(first.id).withVideos(Collections.singletonList(video('e')), 200);
            LocalStoreRegression.<Integer>await(r -> store.importPlaylists(Collections.singletonList(external), true, r));
            check(store.find(first.id).videos.size() == 5, "same-identity archive adds new entries");
            LocalStoreRegression.<Integer>await(r -> store.importPlaylists(Collections.singletonList(external), false, r));
            check(store.snapshot().size() == 3, "add-copies import retains the original playlist");
            check(new HashSet<>(store.snapshot().stream().map(p -> p.id).toList()).size() == 3, "copied archive identities are fresh");
            LocalPlaylist same1 = LocalStoreRegression.<LocalPlaylist>await(r -> store.create("Same", r));
            LocalPlaylist same2 = LocalStoreRegression.<LocalPlaylist>await(r -> store.create("Same", r));
            LocalPlaylist ambiguous = LocalPlaylist.create("Same", 300).withVideos(Collections.singletonList(video('f')), 301);
            LocalStoreRegression.<Integer>await(r -> store.importPlaylists(Collections.singletonList(ambiguous), true, r));
            check(store.find(same1.id).videos.isEmpty() && store.find(same2.id).videos.isEmpty(), "ambiguous names never select a guessed merge target");
            check(store.find(ambiguous.id) != null, "ambiguous import becomes its own playlist");
            LocalStoreRegression.<Void>await(r -> store.setMembership(video('a'), Arrays.asList(first.id, second.id), Collections.singletonList(second.id), r));
            check(!store.find(first.id).videos.contains(video('a')) && store.find(second.id).videos.contains(video('a')), "membership changes commit consistently");
            LocalStoreRegression.<Void>await(r -> store.moveVideo(second.id, video('a').id, -1, r));
            check(store.find(second.id).videos.get(1).id.equals(video('a').id), "manual order changes by stable identity");
            LocalStoreRegression.<Void>await(r -> store.setThumbnail(second.id, video('d').id, r));
            LocalStoreRegression.<Void>await(r -> store.reorderVideo(second.id, video('d').id, video('b').id, false, r));
            check(store.find(second.id).videos.get(0).id.equals(video('d').id), "drag reorders video by stable anchor");
            check(store.find(second.id).thumbnailVideoId.equals(video('d').id), "drag preserves chosen thumbnail");
            LocalStoreRegression.<Void>await(r -> store.reorderPlaylist(second.id, first.id, false, r));
            check(store.snapshot().get(0).id.equals(second.id), "drag reorders playlists by identity");
            LocalPlaylistStore reopenedOrder = reopen(context);
            LocalStoreRegression.<List<LocalPlaylist>>await(reopenedOrder::read);
            check(reopenedOrder.snapshot().get(0).id.equals(second.id), "manual playlist order survives reopening storage");
            check(reopenedOrder.find(second.id).thumbnailVideoId.equals(video('d').id), "chosen thumbnail survives reopening storage");
            worker(reopenedOrder).shutdown(); worker(reopenedOrder).awaitTermination(5, TimeUnit.SECONDS);
            List<LocalPlaylist> beforeMove = store.snapshot();
            byte[] beforeMoveBytes = Files.readAllBytes(file.toPath());
            AtomicFile.failNextWrite = true;
            try {
                LocalStoreRegression.<Void>await(r -> store.moveToPlaylist(second.id, first.id, video('d').id, r));
                throw new AssertionError("failed two-sided move committed");
            } catch (ExecutionException expected) { checks++; }
            check(store.snapshot() == beforeMove, "failed move preserves both source and destination snapshots");
            check(Arrays.equals(beforeMoveBytes, Files.readAllBytes(file.toPath())), "failed move preserves exact committed storage");
            LocalStoreRegression.<Void>await(r -> store.moveToPlaylist(second.id, first.id, video('a').id, r));
            check(!store.find(second.id).videos.contains(video('a')) && store.find(first.id).videos.contains(video('a')), "move commits removal and insertion together");
            int targetCount = store.find(first.id).videos.size();
            LocalStoreRegression.<Void>await(r -> store.moveToPlaylist(second.id, first.id, video('d').id, r));
            check(store.find(first.id).videos.size() == targetCount, "moving an already present video does not duplicate it");
            check(store.find(second.id).thumbnailVideoId.isEmpty(), "moving a cover entry resets its source cover safely");
            LocalStoreRegression.<Void>await(r -> store.remove(second.id, r));
            check(store.find(second.id) == null && store.find(first.id) != null, "deleting one playlist preserves others");
            LocalPlaylist mergeTarget = LocalStoreRegression.<LocalPlaylist>await(r -> store.createWithVideos("Keep this name", Arrays.asList(video('k'), video('l')), r));
            LocalPlaylist mergeA = LocalStoreRegression.<LocalPlaylist>await(r -> store.createWithVideos("Source A", Arrays.asList(video('l'), video('m')), r));
            LocalPlaylist mergeB = LocalStoreRegression.<LocalPlaylist>await(r -> store.createWithVideos("Source B", Arrays.asList(video('n'), video('k')), r));
            LocalStoreRegression.<Void>await(r -> store.setThumbnail(mergeTarget.id, video('l').id, r));
            byte[] beforeMerge = Files.readAllBytes(file.toPath());
            List<LocalPlaylist> beforeMergeSnapshot = store.snapshot();
            AtomicFile.failNextWrite = true;
            try {
                LocalStoreRegression.<LocalPlaylist>await(r -> store.merge(mergeTarget.id, Arrays.asList(mergeA.id, mergeB.id), true, r));
                throw new AssertionError("failed merge removed sources");
            } catch (ExecutionException expected) { checks++; }
            check(store.snapshot() == beforeMergeSnapshot && Arrays.equals(beforeMerge, Files.readAllBytes(file.toPath())), "merge-and-remove failure preserves every source and exact storage");
            LocalStoreRegression.<LocalPlaylist>await(r -> store.merge(mergeTarget.id, Collections.singletonList(mergeA.id), false, r));
            check(store.find(mergeA.id) != null, "explicit keep-sources option retains source playlist");
            LocalStoreRegression.<LocalPlaylist>await(r -> store.merge(mergeTarget.id, Arrays.asList(mergeTarget.id, mergeA.id, mergeB.id, mergeB.id), true, r));
            check(store.find(mergeA.id) == null && store.find(mergeB.id) == null && store.find(mergeTarget.id) != null, "remove-sources option removes only successful merge inputs, never destination");
            LocalPlaylist mergedTarget = store.find(mergeTarget.id);
            check(mergedTarget.name.equals("Keep this name") && mergedTarget.thumbnailVideoId.equals(video('l').id), "merge retains chosen target name and cover");
            check(mergedTarget.videos.equals(Arrays.asList(video('k'), video('l'), video('m'), video('n'))), "merge appends unique videos in source order");
            byte[] beforeDelete = Files.readAllBytes(file.toPath());
            String missingId = LocalPlaylist.create("Missing", 1).id;
            try {
                LocalStoreRegression.<Void>await(r -> store.removePlaylists(Arrays.asList(mergeTarget.id, missingId), r));
                throw new AssertionError("partial batch deletion committed");
            } catch (ExecutionException expected) { checks++; }
            check(store.find(mergeTarget.id) != null && Arrays.equals(beforeDelete, Files.readAllBytes(file.toPath())), "missing bulk-delete input cannot partially delete other selections");
            LocalPlaylist moveSource = LocalStoreRegression.<LocalPlaylist>await(r -> store.createWithVideos("Move from", Arrays.asList(video('q'), video('r'), video('s')), r));
            LocalPlaylist moveTarget = LocalStoreRegression.<LocalPlaylist>await(r -> store.createWithVideos("Move to", Collections.singletonList(video('r')), r));
            LocalStoreRegression.<Void>await(r -> store.setThumbnail(moveSource.id, video('q').id, r));
            byte[] beforeBulkMove = Files.readAllBytes(file.toPath());
            AtomicFile.failNextWrite = true;
            try {
                LocalStoreRegression.<Void>await(r -> store.moveVideos(moveSource.id, moveTarget.id, Arrays.asList(video('q').id, video('r').id), r));
                throw new AssertionError("failed bulk move committed");
            } catch (ExecutionException expected) { checks++; }
            check(Arrays.equals(beforeBulkMove, Files.readAllBytes(file.toPath())) && store.find(moveSource.id).videos.size() == 3, "bulk move rollback preserves both sides");
            LocalStoreRegression.<Void>await(r -> store.moveVideos(moveSource.id, moveTarget.id, Arrays.asList(video('r').id, video('q').id), r));
            check(store.find(moveSource.id).videos.equals(Collections.singletonList(video('s'))) && store.find(moveSource.id).thumbnailVideoId.isEmpty(), "bulk move removes chosen entries and repairs source cover");
            check(store.find(moveTarget.id).videos.equals(Arrays.asList(video('r'), video('q'))), "bulk move retains source order and deduplicates destination");
            AtomicFile.failNextWrite = true;
            try {
                LocalStoreRegression.<Void>await(r -> store.removePlaylists(Arrays.asList(moveSource.id, moveTarget.id), r));
                throw new AssertionError("failed bulk deletion committed");
            } catch (ExecutionException expected) { checks++; }
            check(store.find(moveSource.id) != null && store.find(moveTarget.id) != null, "failed bulk delete leaves every selected playlist");
            LocalStoreRegression.<Void>await(r -> store.removePlaylists(Arrays.asList(moveSource.id, moveTarget.id, moveTarget.id), r));
            check(store.find(moveSource.id) == null && store.find(moveTarget.id) == null && store.find(first.id) != null, "bulk delete removes only chosen identities once");
            List<LocalPlaylist> backup = store.snapshot();
            worker(store).shutdown(); worker(store).awaitTermination(5, TimeUnit.SECONDS);
            byte[] damaged = "not a playlist archive".getBytes(StandardCharsets.UTF_8);
            Files.write(file.toPath(), damaged);
            recovered = reopen(context);
            LocalPlaylistStore damagedStore = recovered;
            try { LocalStoreRegression.<List<LocalPlaylist>>await(damagedStore::read); throw new AssertionError("corrupt store loaded"); }
            catch (ExecutionException expected) { checks++; }
            check(Arrays.equals(damaged, Files.readAllBytes(file.toPath())), "damaged input is not silently overwritten");
            LocalStoreRegression.<Integer>await(r -> damagedStore.restoreBackup(backup, r));
            check(damagedStore.error() == null && damagedStore.snapshot().size() == backup.size(), "explicit backup recovery restores library");
            File[] preserved = file.getParentFile().listFiles((dir, name) -> name.startsWith("library.unreadable-"));
            check(preserved != null && preserved.length == 1 && Arrays.equals(damaged, Files.readAllBytes(preserved[0].toPath())), "recovery preserves exact unreadable bytes");

            history = LocalHistoryStore.instance(context);
            history.record(video('a')); history.record(video('b')); history.record(video('a'));
            worker(history).submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(history.snapshot().size() == 2 && history.snapshot().get(0).id.equals(video('a').id), "local history deduplicates and orders recent plays");
            history.setEnabled(false); history.record(video('c'));
            worker(history).submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(history.snapshot().size() == 2, "paused local history records nothing");
            history.setEnabled(true);
            AtomicFile.failNextWrite = true;
            history.record(video('c')); worker(history).submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(history.snapshot().size() == 2, "failed history write keeps the previous snapshot");
            history.record(video('d')); worker(history).submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(history.snapshot().size() == 3 && history.error() == null, "history automatically retries after a transient write failure");
            history.clear(); worker(history).submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(history.snapshot().isEmpty(), "clear history commits an empty store");
        } finally {
            worker(store).shutdownNow();
            if (recovered != null) worker(recovered).shutdownNow();
            if (history != null) worker(history).shutdownNow();
            try (var paths = Files.walk(directory.toPath())) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} });
            }
        }
        System.out.println("DreamPipe store regression checks passed: " + checks);
    }
}
