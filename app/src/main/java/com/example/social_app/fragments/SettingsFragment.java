package com.example.social_app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.example.social_app.LoginActivity;
import com.example.social_app.R;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.LanguageUtils;
import com.example.social_app.utils.ThemeUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Theme Switch
        MaterialSwitch darkModeSwitch = view.findViewById(R.id.switch_dark_mode);
        darkModeSwitch.setChecked(ThemeUtils.isDarkMode(requireContext()));
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ThemeUtils.setDarkMode(requireContext(), isChecked);
            requireActivity().recreate();
        });

        // Language RadioGroup
        RadioGroup rgLanguage = view.findViewById(R.id.rg_language);
        String currentLang = LanguageUtils.getLanguage(requireContext());
        if (currentLang.equals("vi")) {
            ((RadioButton) view.findViewById(R.id.rb_vietnamese)).setChecked(true);
        } else {
            ((RadioButton) view.findViewById(R.id.rb_english)).setChecked(true);
        }

        rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String lang = "en";
            if (checkedId == R.id.rb_vietnamese) {
                lang = "vi";
            }
            if (!lang.equals(LanguageUtils.getLanguage(requireContext()))) {
                LanguageUtils.setLocale(requireContext(), lang);
                requireActivity().recreate();
            }
        });

        MaterialButton logoutButton = view.findViewById(R.id.btn_logout);
        logoutButton.setOnClickListener(v -> logout());
    }

    private void logout() {
        FirebaseManager.getInstance().getAuth().signOut();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
