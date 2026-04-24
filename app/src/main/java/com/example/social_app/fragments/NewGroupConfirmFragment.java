package com.example.social_app.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.example.social_app.repository.ConversationRepository;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NewGroupConfirmFragment extends Fragment {

    private static final String ARG_USERS = "selected_users";

    private ArrayList<User> selectedUsers;

    public NewGroupConfirmFragment() {
        super(R.layout.fragment_new_group_confirm);
    }

    @NonNull
    public static NewGroupConfirmFragment newInstance(@NonNull ArrayList<User> users) {
        NewGroupConfirmFragment f = new NewGroupConfirmFragment();
        Bundle b = new Bundle();
        b.putSerializable(ARG_USERS, users);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            @SuppressWarnings("unchecked")
            ArrayList<User> u = (ArrayList<User>) args.getSerializable(ARG_USERS);
            selectedUsers = u != null ? u : new ArrayList<>();
        } else {
            selectedUsers = new ArrayList<>();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton back = view.findViewById(R.id.btn_confirm_back);
        TextView btnCreate = view.findViewById(R.id.btn_confirm_create);
        TextInputEditText inputName = view.findViewById(R.id.input_group_name);
        LinearLayout chips = view.findViewById(R.id.invited_chips_container);

        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (User u : selectedUsers) {
            View chip = inflater.inflate(R.layout.item_invited_user_chip, chips, false);
            ShapeableImageView av = chip.findViewById(R.id.chip_avatar);
            TextView nm = chip.findViewById(R.id.chip_name);
            String label = displayName(u);
            nm.setText(label);
            UserAvatarLoader.load(av, u.getAvatarUrl());
            chips.addView(chip);
        }

        btnCreate.setOnClickListener(v -> {
            FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
            if (me == null) {
                Toast.makeText(requireContext(), R.string.messages_need_login, Toast.LENGTH_SHORT).show();
                return;
            }
            String rawName = inputName.getText() != null ? inputName.getText().toString().trim() : "";
            List<String> otherIds = new ArrayList<>();
            for (User u : selectedUsers) {
                if (u.getId() != null) {
                    otherIds.add(u.getId());
                }
            }
            if (otherIds.size() < 2) {
                Toast.makeText(requireContext(), R.string.new_group_need_two_members, Toast.LENGTH_SHORT).show();
                return;
            }

            String displayTitle = buildDisplayTitle(rawName);

            ConversationRepository repo = new ConversationRepository(requireContext());
            repo.createGroupConversation(me.getUid(), otherIds, rawName.isEmpty() ? null : rawName)
                    .addOnSuccessListener(convId -> {
                        if (!isAdded() || !(getActivity() instanceof MainActivity)) {
                            return;
                        }
                        ((MainActivity) getActivity()).finishNewGroupFlowAndOpenGroupChat(convId, displayTitle);
                    })
                    .addOnFailureListener(e -> Toast.makeText(
                            requireContext(),
                            R.string.new_group_create_failed,
                            Toast.LENGTH_SHORT
                    ).show());
        });
    }

    @NonNull
    private String buildDisplayTitle(@NonNull String rawName) {
        if (!rawName.isEmpty()) {
            return rawName;
        }
        List<String> names = new ArrayList<>();
        for (User u : selectedUsers) {
            names.add(displayName(u));
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return TextUtils.join(", ", names);
    }

    @NonNull
    private String displayName(@NonNull User u) {
        if (u.getFullName() != null && !u.getFullName().trim().isEmpty()) {
            return u.getFullName().trim();
        }
        if (u.getUsername() != null && !u.getUsername().trim().isEmpty()) {
            return u.getUsername().trim();
        }
        return u.getId() != null ? u.getId() : "";
    }
}
