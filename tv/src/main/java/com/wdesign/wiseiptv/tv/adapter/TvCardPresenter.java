package com.wdesign.wiseiptv.tv.adapter;
import android.content.Context;
import android.view.ViewGroup;
import androidx.leanback.widget.*;
import com.bumptech.glide.Glide;
import com.wdesign.wiseiptv.core.db.entity.ChannelEntity;
import com.wdesign.wiseiptv.tv.R;
public class TvCardPresenter extends Presenter {
    @Override public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context ctx = parent.getContext();
        ImageCardView card = new ImageCardView(ctx);
        card.setFocusable(true); card.setFocusableInTouchMode(true);
        float d = ctx.getResources().getDisplayMetrics().density;
        card.setMainImageDimensions(Math.round(160*d), Math.round(100*d));
        return new TvCardHolder(card);
    }
    @Override public void onBindViewHolder(ViewHolder vh, Object item) {
        ChannelEntity ch = (ChannelEntity) item;
        ImageCardView card = (ImageCardView) vh.view;
        if (vh instanceof TvCardHolder && !((TvCardHolder) vh).hasEntered) {
            ((TvCardHolder) vh).hasEntered = true;
            card.setAlpha(0f);
            card.setTranslationY(10f);
            card.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
        card.setTitleText(ch.name);
        card.setContentText(ch.groupTitle != null ? ch.groupTitle : "");
        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            Glide.with(card.getContext()).load(ch.logoUrl).centerCrop()
                .placeholder(R.drawable.ic_channel_placeholder).into(card.getMainImageView());
        } else {
            card.getMainImageView().setImageResource(R.drawable.ic_channel_placeholder);
        }
    }
    @Override public void onUnbindViewHolder(ViewHolder vh) {
        ImageCardView card = (ImageCardView) vh.view;
        card.animate().cancel();
        card.setAlpha(1f);
        card.setTranslationY(0f);
        card.setMainImage(null);
    }

    private static class TvCardHolder extends ViewHolder {
        boolean hasEntered;
        TvCardHolder(ImageCardView card) { super(card); }
    }
}
