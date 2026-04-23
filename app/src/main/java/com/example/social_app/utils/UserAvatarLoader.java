package com.example.social_app.utils;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.social_app.R;

/**
 * Loads user avatars. If {@code avatarUrl} is null, blank, or not an http(s) URL,
 * {@link Constants#DEFAULT_AVATAR_URL} is used.
 */
public final class UserAvatarLoader {

    private UserAvatarLoader() {}

    /**
     * URL thực tế sẽ load (luôn là http(s), mặc định khi không hợp lệ).
     */
    public static String effectiveAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            return Constants.DEFAULT_AVATAR_URL;
        }
        String t = avatarUrl.trim();
        if (t.isEmpty()) {
            return Constants.DEFAULT_AVATAR_URL;
        }
        if (!t.startsWith("http://") && !t.startsWith("https://")) {
            return Constants.DEFAULT_AVATAR_URL;
        }
        return t;
    }

    public static void load(ImageView imageView, String avatarUrl) {
        if (imageView == null) {
            return;
        }
        Context ctx = imageView.getContext();
        if (ctx == null) {
            return;
        }
        String url = effectiveAvatarUrl(avatarUrl);
        Glide.with(ctx)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.avatar_placeholder)
                .error(R.drawable.avatar_placeholder)
                .into(imageView);
    }
}
