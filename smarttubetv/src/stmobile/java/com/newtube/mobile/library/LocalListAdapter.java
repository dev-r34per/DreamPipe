package com.newtube.mobile.library;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/** Compact library rows with an explicit drag handle; feed cards remain upstream-owned. */
final class LocalListAdapter extends RecyclerView.Adapter<LocalListAdapter.Holder> {
    interface Host {
        void open(Object item);
        void actions(Object item);
        void drag(RecyclerView.ViewHolder holder);
        void reorderAccessibly(Object item);
    }

    private final Host mHost;
    private final List<Object> mItems = new ArrayList<>();
    private boolean mReorderable;
    private boolean mSelecting;
    private java.util.Set<String> mSelected = java.util.Collections.emptySet();

    void selection(boolean active, java.util.Set<String> selected) {
        if (mSelecting == active && mSelected.equals(selected)) return;
        mSelecting = active; mSelected = new java.util.HashSet<>(selected);
        if (!mItems.isEmpty()) notifyItemRangeChanged(0, mItems.size(), "selection");
    }


    LocalListAdapter(Host host) { mHost = host; }

    void submit(List<?> items, boolean reorderable) {
        List<Object> before = new ArrayList<>(mItems);
        boolean handlesChanged = mReorderable != reorderable;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return before.size(); }
            @Override public int getNewListSize() { return items.size(); }
            @Override public boolean areItemsTheSame(int a, int b) { return key(before.get(a)).equals(key(items.get(b))); }
            @Override public boolean areContentsTheSame(int a, int b) { return !handlesChanged && before.get(a) == items.get(b); }
        });
        mItems.clear(); mItems.addAll(items); mReorderable = reorderable;
        diff.dispatchUpdatesTo(this);
    }

    Object item(int position) { return mItems.get(position); }
    boolean isReorderable() { return mReorderable && !mSelecting; }
    void move(int from, int to) { mItems.add(to, mItems.remove(from)); notifyItemMoved(from, to); }
    static String key(Object item) {
        return item instanceof LocalPlaylist ? ((LocalPlaylist) item).id : ((LocalVideo) item).id;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_playlist, parent, false));
    }
    @Override public int getItemCount() { return mItems.size(); }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) { holder.bind(mItems.get(position)); }
    @Override public void onViewRecycled(@NonNull Holder holder) { Glide.with(holder.itemView).clear(holder.thumbnail); }

    final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        Holder(View view) { super(view); thumbnail = view.findViewById(R.id.playlist_thumbnail); }
        @SuppressLint("ClickableViewAccessibility") // ACTION_UP performs click; TalkBack offers position actions.
        void bind(Object item) {
            boolean playlistRow = item instanceof LocalPlaylist;
            LocalPlaylist playlist = playlistRow ? (LocalPlaylist) item : null;
            LocalVideo video = playlistRow ? playlist.thumbnail() : (LocalVideo) item;
            String name = playlistRow ? playlist.name : video.title;
            ((TextView) itemView.findViewById(R.id.playlist_title)).setText(name);
            ((TextView) itemView.findViewById(R.id.playlist_meta)).setText(playlistRow
                    ? itemView.getResources().getQuantityString(R.plurals.dreampipe_video_count, playlist.videos.size(), playlist.videos.size())
                    : video.author);
            Glide.with(itemView).load(video == null ? null : LocalVideos.toVideo(video).getCardImageUrl())
                    .centerCrop().into(thumbnail);
            itemView.findViewById(R.id.playlist_badge).setVisibility(playlistRow ? View.VISIBLE : View.GONE);
            itemView.setOnClickListener(v -> mHost.open(item));
            itemView.setOnLongClickListener(v -> { mHost.actions(item); return true; });
            android.widget.CheckBox check = itemView.findViewById(R.id.playlist_selected);
            check.setVisibility(mSelecting ? View.VISIBLE : View.GONE);
            check.setChecked(mSelected.contains(key(item)));
            itemView.setActivated(mSelecting && check.isChecked());
            androidx.core.view.ViewCompat.setAccessibilityDelegate(itemView, new androidx.core.view.AccessibilityDelegateCompat() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCheckable(mSelecting); info.setChecked(mSelecting && mSelected.contains(key(item)));
                }
            });
            View handle = itemView.findViewById(R.id.playlist_drag);
            handle.setVisibility(isReorderable() ? View.VISIBLE : View.GONE);
            handle.setContentDescription(itemView.getContext().getString(R.string.dreampipe_reorder_item, name));
            handle.setOnClickListener(v -> mHost.reorderAccessibly(item));
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) mHost.drag(this);
                // Let the button finish a tap for keyboard/accessibility position controls.
                return false;
            });
        }
    }
}
