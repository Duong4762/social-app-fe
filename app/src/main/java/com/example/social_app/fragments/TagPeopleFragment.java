package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.example.social_app.utils.UserAvatarLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagPeopleFragment extends Fragment {

    private RecyclerView recyclerView;
    private EditText searchInput;
    private ImageButton backButton;
    private TextView doneButton;
    private PeopleAdapter adapter;
    private OnPeopleTaggedListener listener;
    private Set<String> previouslySelectedIds = new HashSet<>();

    public interface OnPeopleTaggedListener {
        void onPeopleTagged(List<User> taggedUsers);
    }

    public static TagPeopleFragment newInstance(ArrayList<String> selectedIds) {
        TagPeopleFragment fragment = new TagPeopleFragment();
        Bundle args = new Bundle();
        args.putStringArrayList("selected_ids", selectedIds);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnPeopleTaggedListener(OnPeopleTaggedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tag_people, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (getArguments() != null) {
            List<String> ids = getArguments().getStringArrayList("selected_ids");
            if (ids != null) previouslySelectedIds.addAll(ids);
        }

        recyclerView = view.findViewById(R.id.people_recycler_view);
        searchInput = view.findViewById(R.id.search_input);
        backButton = view.findViewById(R.id.back_button);
        doneButton = view.findViewById(R.id.done_button);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // Mock data for demo - in real app, fetch from ViewModel/Firestore
        List<User> mockUsers = new ArrayList<>();
        mockUsers.add(new User("1", "johndoe", "john@example.com", "John Doe", null, null, null, null, "USER", true));
        mockUsers.add(new User("2", "janedoe", "jane@example.com", "Jane Doe", null, null, null, null, "USER", true));
        mockUsers.add(new User("3", "bobsmith", "bob@example.com", "Bob Smith", null, null, null, null, "USER", true));

        adapter = new PeopleAdapter(mockUsers, previouslySelectedIds);
        recyclerView.setAdapter(adapter);

        backButton.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        doneButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPeopleTagged(new ArrayList<>(adapter.getSelectedUsers()));
            }
            getParentFragmentManager().popBackStack();
        });
    }

    private static class PeopleAdapter extends RecyclerView.Adapter<PeopleAdapter.ViewHolder> {
        private List<User> users;
        private Set<String> selectedUserIds;

        PeopleAdapter(List<User> users, Set<String> selectedIds) {
            this.users = users;
            this.selectedUserIds = new HashSet<>(selectedIds);
        }

        Set<String> getSelectedUserIds() { return selectedUserIds; }

        List<User> getSelectedUsers() {
            List<User> selected = new ArrayList<>();
            for (User u : users) {
                if (selectedUserIds.contains(u.getId())) {
                    selected.add(u);
                }
            }
            return selected;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_tag, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = users.get(position);
            holder.nameText.setText(user.getFullName());
            holder.usernameText.setText("@" + user.getUsername());
            UserAvatarLoader.load(holder.avatarImage, user.getAvatarUrl());
            holder.checkBox.setChecked(selectedUserIds.contains(user.getId()));

            holder.itemView.setOnClickListener(v -> {
                if (selectedUserIds.contains(user.getId())) {
                    selectedUserIds.remove(user.getId());
                } else {
                    selectedUserIds.add(user.getId());
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() { return users.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView avatarImage;
            TextView nameText, usernameText;
            CheckBox checkBox;

            ViewHolder(View itemView) {
                super(itemView);
                avatarImage = itemView.findViewById(R.id.user_avatar);
                nameText = itemView.findViewById(R.id.user_name);
                usernameText = itemView.findViewById(R.id.user_username);
                checkBox = itemView.findViewById(R.id.user_checkbox);
            }
        }
    }
}
