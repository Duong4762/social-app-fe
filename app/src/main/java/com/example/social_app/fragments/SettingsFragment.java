package com.example.social_app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.LoginActivity;
import com.example.social_app.R;
import com.example.social_app.firebase.FirebaseManager;
import com.google.android.material.button.MaterialButton;

public class SettingsFragment extends Fragment {

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
