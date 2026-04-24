package com.example.social_app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.Locale;

public class LanguageUtils {
    private static final String PREF_NAME = "settings";
    private static final String KEY_LANGUAGE = "language";

    public static void setLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(locale);
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_LANGUAGE, lang);
        editor.apply();
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "en");
    }

    public static void loadLocale(Context context) {
        String language = getLanguage(context);
        setLocale(context, language);
    }

    /**
     * Context (Resources) theo ngôn ngữ đã lưu trong prefs — cần khi dùng {@code getApplicationContext()},
     * vì application context không đi qua {@code attachBaseContext} của Activity.
     */
    @NonNull
    public static Context contextWithSavedLanguage(@NonNull Context base) {
        String lang = getLanguage(base);
        Locale locale = new Locale(lang);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
