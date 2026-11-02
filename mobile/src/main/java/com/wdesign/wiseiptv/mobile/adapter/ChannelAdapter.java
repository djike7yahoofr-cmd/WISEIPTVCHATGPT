package com.wdesign.wiseiptv.mobile.adapter;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.wdesign.wiseiptv.core.db.entity.ChannelEntity;
import com.wdesign.wiseiptv.mobile.R;
import java.util.*;
public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {
    public interface OnChannelClick { void onClick(ChannelEntity ch); }
    private List<ChannelEntity> data = new ArrayList<>();
    private final OnChannelClick listener;
    public ChannelAdapter(OnChannelClick l) { this.listener = l; }
    public void setData(List<ChannelEntity> list) {
        data = list != null ? list : new ArrayList<>(); notifyDataSetChanged();
    }
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_channel, p, false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        ChannelEntity ch = data.get(pos);
        if (!h.hasEntered) {
            h.hasEntered = true;
            h.itemView.setAlpha(0f);
            h.itemView.setTranslationY(12f);
            h.itemView.animate().alpha(1f).translationY(0f)
                .setDuration(220).setStartDelay(Math.min(pos * 16L, 144L)).start();
        }
        h.tvName.setText(ch.name);
        h.tvLive.setVisibility(ch.contentType == ChannelEntity.TYPE_LIVE ? View.VISIBLE : View.GONE);
        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            Glide.with(h.imgLogo).load(ch.logoUrl).centerCrop()
                .placeholder(R.drawable.ic_channel_placeholder).into(h.imgLogo);
        } else {
            h.imgLogo.setImageResource(R.drawable.ic_channel_placeholder);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(ch));
    }
    @Override public int getItemCount() { return data.size(); }
    @Override public void onViewRecycled(@NonNull VH h) {
        h.itemView.animate().cancel();
        h.itemView.setAlpha(1f);
        h.itemView.setTranslationY(0f);
        super.onViewRecycled(h);
    }
    static class VH extends RecyclerView.ViewHolder {
        ImageView imgLogo; TextView tvName, tvLive;
        boolean hasEntered;
        VH(View v) { super(v); imgLogo = v.findViewById(R.id.img_logo);
            tvName = v.findViewById(R.id.tv_name); tvLive = v.findViewById(R.id.tv_live); }
    }
}
