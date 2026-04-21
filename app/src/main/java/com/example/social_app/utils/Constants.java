package com.example.social_app.utils;

import java.util.Calendar;

public class Constants {
    // App Name
    public static final String APP_NAME = "Social9";

    // Time constants
    public static final long HOUR_IN_MILLIS = 3600000;
    public static final long DAY_IN_MILLIS = 86400000;
    public static final long WEEK_IN_MILLIS = 604800000;

    // Intent extras
    public static final String EXTRA_POST_ID = "post_id";
    public static final String EXTRA_USER_ID = "user_id";

    // Fragment tags
    public static final String TAG_HOME_FRAGMENT = "home_fragment";
    public static final String TAG_SEARCH_FRAGMENT = "search_fragment";
    public static final String TAG_PROFILE_FRAGMENT = "profile_fragment";

    // Dummy avatar drawables (drawable resource IDs)
    public static final int[] AVATAR_COLORS = {
            0xFF7C3AED, // Purple
            0xFF3B82F6, // Blue
            0xFF10B981, // Green
            0xFFF59E0B, // Amber
            0xFFEF4444, // Red
            0xFF8B5CF6, // Violet
            0xFF06B6D4, // Cyan
            0xFFEC4899  // Pink
    };

    private Constants() {
        // Prevent instantiation
    }
}

