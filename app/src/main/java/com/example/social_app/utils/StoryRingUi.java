package com.example.social_app.utils;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.social_app.R;
import com.example.social_app.data.model.Story;

import java.util.List;

/**
 * Vòng story quanh avatar: xanh nếu còn story chưa xem, xám nếu đã xem hết, ẩn nếu không có story.
 */
public final class StoryRingUi {

    public enum Tone {
        NONE,
        GREEN,
        GRAY
    }

    private StoryRingUi() {}

    @NonNull
    public static Tone toneForUser(
            @Nullable String userId,
            @Nullable List<Story> allStories,
            @Nullable String viewerUserId
    ) {
        if (userId == null || allStories == null || allStories.isEmpty()) {
            return Tone.NONE;
        }
        boolean hasActive = false;
        boolean allViewed = true;
        for (Story s : allStories) {
            if (s == null || !userId.equals(s.getUserId())) {
                continue;
            }
            if (!s.isValid()) {
                continue;
            }
            hasActive = true;
            boolean seen = viewerUserId != null
                    && s.getViewedBy() != null
                    && s.getViewedBy().contains(viewerUserId);
            if (!seen) {
                allViewed = false;
            }
        }
        if (!hasActive) {
            return Tone.NONE;
        }
        return allViewed ? Tone.GRAY : Tone.GREEN;
    }

    /**
     * @param avatarDiameterDp đường kính avatar (phần tròn bên trong), không gồm viền vòng.
     */
    public static void apply(@Nullable View ringView, @NonNull Tone tone, float avatarDiameterDp) {
        if (ringView == null) {
            return;
        }
        if (tone == Tone.NONE) {
            ringView.setVisibility(View.GONE);
            ringView.setBackground(null);
            return;
        }
        ringView.setVisibility(View.VISIBLE);
        float density = ringView.getResources().getDisplayMetrics().density;
        int thicknessPx = Math.max(1, Math.round(2f * density));
        int innerRadiusPx = Math.max(1, Math.round((avatarDiameterDp * density) / 2f));

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RING);
        gd.setUseLevel(false);
        gd.setThickness(thicknessPx);
        gd.setInnerRadius(innerRadiusPx);
        int color = ContextCompat.getColor(
                ringView.getContext(),
                tone == Tone.GREEN ? R.color.story_ring_green : R.color.story_ring_gray
        );
        gd.setColor(color);
        ringView.setBackground(gd);
    }
}
