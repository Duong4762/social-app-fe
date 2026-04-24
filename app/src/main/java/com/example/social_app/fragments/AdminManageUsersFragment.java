package com.example.social_app.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.adapters.AdminManageUserAdapter;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminManageUsersFragment extends Fragment {
    private RecyclerView rvUsers;
    private EditText edtSearch;
    private MaterialButton chipAll;
    private MaterialButton chipActive;
    private MaterialButton chipBanned;
    private MaterialButton chipWarned;
    private AdminManageUserAdapter adapter;
    private final List<User> allUsers = new ArrayList<>();
    private int selectedFilter = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manage_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvUsers = view.findViewById(R.id.rvUsers);
        edtSearch = view.findViewById(R.id.edtSearch);
        chipAll = view.findViewById(R.id.chipAll);
        chipActive = view.findViewById(R.id.chipActive);
        chipBanned = view.findViewById(R.id.chipBanned);
        chipWarned = view.findViewById(R.id.chipWarned);

        adapter = new AdminManageUserAdapter(new AdminManageUserAdapter.Listener() {
            @Override
            public void onEdit(User user) {
                openEditDialog(user);
            }

            @Override
            public void onToggleBan(User user) {
                toggleBan(user);
            }
        });
        rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvUsers.setAdapter(adapter);

        setupFilters();
        setupSearch();
        loadUsers();
    }

    private void setupFilters() {
        chipAll.setOnClickListener(v -> {
            selectedFilter = 0;
            updateFilterUI();
            applyFilters();
        });
        chipActive.setOnClickListener(v -> {
            selectedFilter = 1;
            updateFilterUI();
            applyFilters();
        });
        chipBanned.setOnClickListener(v -> {
            selectedFilter = 2;
            updateFilterUI();
            applyFilters();
        });
        chipWarned.setOnClickListener(v -> {
            selectedFilter = 3;
            updateFilterUI();
            applyFilters();
        });
        updateFilterUI();
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadUsers() {
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(query -> {
                    allUsers.clear();
                    query.getDocuments().forEach(doc -> {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setId(doc.getId());
                            allUsers.add(user);
                        }
                    });
                    applyFilters();
                })
                .addOnFailureListener(e -> showToast("Không tải được danh sách users"));
    }

    private void applyFilters() {
        String keyword = edtSearch.getText() == null ? "" : edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<User> filtered = new ArrayList<>();
        for (User user : allUsers) {
            if (selectedFilter == 1 && user.isBanned()) {
                continue;
            }
            if (selectedFilter == 2 && !user.isBanned()) {
                continue;
            }
            if (selectedFilter == 3 && user.getWarningCount() <= 0) {
                continue;
            }
            if (!keyword.isEmpty()) {
                String fullName = user.getFullName() == null ? "" : user.getFullName().toLowerCase(Locale.ROOT);
                String username = user.getUsername() == null ? "" : user.getUsername().toLowerCase(Locale.ROOT);
                String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase(Locale.ROOT);
                if (!fullName.contains(keyword) && !username.contains(keyword) && !email.contains(keyword)) {
                    continue;
                }
            }
            filtered.add(user);
        }
        adapter.setItems(filtered);
    }

    private void updateFilterUI() {
        styleChip(chipAll, selectedFilter == 0);
        styleChip(chipActive, selectedFilter == 1);
        styleChip(chipBanned, selectedFilter == 2);
        styleChip(chipWarned, selectedFilter == 3);
    }

    private void styleChip(MaterialButton button, boolean active) {
        int bg = active ? requireContext().getColor(R.color.primary_purple) : resolveThemeColor(com.google.android.material.R.attr.colorSurface);
        int text = active ? requireContext().getColor(R.color.white) : resolveThemeColor(com.google.android.material.R.attr.colorOnSurface);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
        button.setTextColor(text);
        button.setStrokeWidth(active ? 0 : 1);
    }

    private int resolveThemeColor(int attrResId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private void toggleBan(User user) {
        boolean nextBanned = !user.isBanned();
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(user.getId())
                .update("isBanned", nextBanned)
                .addOnSuccessListener(unused -> {
                    user.setBanned(nextBanned);
                    applyFilters();
                    showToast(nextBanned ? "Đã ban user" : "Đã mở ban user");
                })
                .addOnFailureListener(e -> showToast("Không thể cập nhật trạng thái ban"));
    }

    private void openEditDialog(User user) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_admin_edit_user, null, false);
        EditText edtFullName = dialogView.findViewById(R.id.edtFullName);
        EditText edtUsername = dialogView.findViewById(R.id.edtUsername);
        EditText edtEmail = dialogView.findViewById(R.id.edtEmail);
        EditText edtBio = dialogView.findViewById(R.id.edtBio);
        EditText edtGender = dialogView.findViewById(R.id.edtGender);
        EditText edtDob = dialogView.findViewById(R.id.edtDob);

        edtFullName.setText(user.getFullName());
        edtUsername.setText(user.getUsername());
        edtEmail.setText(user.getEmail());
        edtBio.setText(user.getBio());
        edtGender.setText(user.getGender());
        edtDob.setText(user.getDateOfBirth());

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit user")
                .setView(dialogView)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("OK", (dialog, which) -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("fullName", trim(edtFullName.getText()));
                    updates.put("username", trim(edtUsername.getText()));
                    updates.put("email", trim(edtEmail.getText()));
                    updates.put("bio", trim(edtBio.getText()));
                    updates.put("gender", trim(edtGender.getText()));
                    updates.put("dateOfBirth", trim(edtDob.getText()));
                    FirebaseManager.getInstance().getFirestore()
                            .collection(FirebaseManager.COLLECTION_USERS)
                            .document(user.getId())
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                user.setFullName((String) updates.get("fullName"));
                                user.setUsername((String) updates.get("username"));
                                user.setEmail((String) updates.get("email"));
                                user.setBio((String) updates.get("bio"));
                                user.setGender((String) updates.get("gender"));
                                user.setDateOfBirth((String) updates.get("dateOfBirth"));
                                applyFilters();
                                showToast("Đã cập nhật thông tin user");
                            })
                            .addOnFailureListener(e -> showToast("Không thể cập nhật user"));
                })
                .show();
    }

    private String trim(Editable editable) {
        return editable == null ? "" : editable.toString().trim();
    }

    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
