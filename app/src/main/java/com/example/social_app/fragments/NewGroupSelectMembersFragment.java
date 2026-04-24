package com.example.social_app.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.repository.ConversationRepository;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NewGroupSelectMembersFragment extends Fragment {

    private final List<User> dmPeers = new ArrayList<>();
    private final List<User> allUsers = new ArrayList<>();
    private final List<User> visibleRows = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private final Map<String, User> userById = new LinkedHashMap<>();

    private TextView btnCreate;
    private EditText searchInput;
    private RecyclerView recyclerView;
    private PickAdapter adapter;

    public NewGroupSelectMembersFragment() {
        super(R.layout.fragment_new_group_select);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton back = view.findViewById(R.id.btn_new_group_back);
        btnCreate = view.findViewById(R.id.btn_new_group_create);
        searchInput = view.findViewById(R.id.new_group_search);
        recyclerView = view.findViewById(R.id.new_group_user_list);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PickAdapter();
        recyclerView.setAdapter(adapter);

        back.setOnClickListener(v -> requireParentFragmentManager().popBackStack());
        btnCreate.setOnClickListener(v -> goToConfirmStep());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshVisibleRows();
            }
        });

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            Toast.makeText(requireContext(), R.string.messages_need_login, Toast.LENGTH_SHORT).show();
            requireParentFragmentManager().popBackStack();
            return;
        }

        loadDmPeers(me.getUid());
        loadAllUsers(me.getUid());
        updateCreateButton();
        refreshVisibleRows();
    }

    private void refreshVisibleRows() {
        visibleRows.clear();
        visibleRows.addAll(getDisplayedUsers());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private androidx.fragment.app.FragmentManager requireParentFragmentManager() {
        return requireActivity().getSupportFragmentManager();
    }

    private void loadDmPeers(@NonNull String myUid) {
        new ConversationRepository(requireContext()).loadUsersWithDirectConversation(
                myUid,
                new ConversationRepository.DirectPeersCallback() {
                    @Override
                    public void onPeersLoaded(@NonNull List<User> peers) {
                        if (!isAdded()) return;
                        dmPeers.clear();
                        dmPeers.addAll(peers);
                        indexUsers(peers);
                        refreshVisibleRows();
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadAllUsers(@NonNull String myUid) {
        FirebaseFirestore.getInstance()
                .collection(FirebaseManager.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    allUsers.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        User u = doc.toObject(User.class);
                        if (u == null) continue;
                        if (u.getId() == null || u.getId().isEmpty()) {
                            u.setId(doc.getId());
                        }
                        if (myUid.equals(u.getId())) continue;
                        if (isAdminUser(u)) continue;
                        allUsers.add(u);
                    }
                    indexUsers(allUsers);
                    refreshVisibleRows();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void indexUsers(@NonNull List<User> users) {
        for (User u : users) {
            if (u.getId() != null) {
                userById.put(u.getId(), u);
            }
        }
    }

    private boolean isAdminUser(@Nullable User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        return "ADMIN".equalsIgnoreCase(user.getRole().trim());
    }

    @NonNull
    private List<User> getDisplayedUsers() {
        String q = searchInput != null && searchInput.getText() != null
                ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT)
                : "";
        if (q.isEmpty()) {
            return new ArrayList<>(dmPeers);
        }
        List<User> out = new ArrayList<>();
        for (User u : allUsers) {
            String un = u.getUsername() != null ? u.getUsername().toLowerCase(Locale.ROOT) : "";
            String fn = u.getFullName() != null ? u.getFullName().toLowerCase(Locale.ROOT) : "";
            if (un.contains(q) || fn.contains(q)) {
                out.add(u);
            }
        }
        return out;
    }

    private void toggleUser(@NonNull User user) {
        String id = user.getId();
        if (id == null) return;
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        updateCreateButton();
        adapter.notifyDataSetChanged();
    }

    private void updateCreateButton() {
        boolean ok = selectedIds.size() >= 2;
        btnCreate.setEnabled(ok);
        btnCreate.setAlpha(ok ? 1f : 0.55f);
        int color = ContextCompat.getColor(requireContext(), ok ? R.color.primary : R.color.muted);
        btnCreate.setTextColor(color);
    }

    private void goToConfirmStep() {
        if (selectedIds.size() < 2) {
            return;
        }
        List<User> picked = new ArrayList<>();
        for (String id : selectedIds) {
            User u = userById.get(id);
            if (u != null) {
                picked.add(u);
            }
        }
        if (picked.size() < 2) {
            Toast.makeText(requireContext(), R.string.new_group_need_two_members, Toast.LENGTH_SHORT).show();
            return;
        }
        requireParentFragmentManager().beginTransaction()
                .replace(R.id.full_screen_group_overlay, NewGroupConfirmFragment.newInstance(new ArrayList<>(picked)))
                .addToBackStack(null)
                .commit();
    }

    private final class PickAdapter extends RecyclerView.Adapter<PickVH> {

        @NonNull
        @Override
        public PickVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_new_group_pick_user, parent, false);
            return new PickVH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull PickVH h, int position) {
            User u = visibleRows.get(position);
            String label = u.getFullName() != null && !u.getFullName().trim().isEmpty()
                    ? u.getFullName().trim()
                    : (u.getUsername() != null ? u.getUsername() : u.getId());
            h.name.setText(label);
            UserAvatarLoader.load(h.avatar, u.getAvatarUrl());
            boolean checked = u.getId() != null && selectedIds.contains(u.getId());
            h.checkbox.setChecked(checked);
            h.itemView.setOnClickListener(v -> toggleUser(u));
        }

        @Override
        public int getItemCount() {
            return visibleRows.size();
        }
    }

    static final class PickVH extends RecyclerView.ViewHolder {
        final ShapeableImageView avatar;
        final TextView name;
        final CheckBox checkbox;

        PickVH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.pick_user_avatar);
            name = itemView.findViewById(R.id.pick_user_name);
            checkbox = itemView.findViewById(R.id.pick_user_checkbox);
        }
    }
}
