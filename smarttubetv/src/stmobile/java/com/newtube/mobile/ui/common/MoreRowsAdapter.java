package com.newtube.mobile.ui.common;

import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Explicit expansion, outside the video's identity/diffing model. */
public final class MoreRowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final Runnable mMore;
    private boolean mVisible;
    public MoreRowsAdapter(Runnable more) { mMore = more; }
    public void setVisible(boolean visible) {
        if (visible == mVisible) return;
        mVisible = visible;
        if (visible) notifyItemInserted(0); else notifyItemRemoved(0);
    }
    @Override public int getItemCount() { return mVisible ? 1 : 0; }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        FrameLayout frame = new FrameLayout(parent.getContext());
        frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        int inset = Math.round(12 * parent.getResources().getDisplayMetrics().density);
        frame.setPadding(inset, 0, inset, inset);
        MaterialButton button = new MaterialButton(parent.getContext());
        button.setText(R.string.dreampipe_show_more);
        button.setOnClickListener(v -> mMore.run());
        frame.addView(button, new FrameLayout.LayoutParams(-1, -2));
        return new RecyclerView.ViewHolder(frame) {};
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
}
