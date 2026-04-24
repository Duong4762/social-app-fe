package com.example.social_app.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.MainActivity;
import com.example.social_app.R;
import com.example.social_app.adapters.MessagesConversationAdapter;
import com.example.social_app.repository.ConversationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessagesFragment extends Fragment {

    private ConversationRepository conversationRepository;
    private ListenerRegistration conversationsListener;
    private TextView emptyView;
    private RecyclerView listView;
    private EditText searchInput;
    private final List<MessagesConversationAdapter.Item> allConversationItems = new ArrayList<>();

    public MessagesFragment() {
        super(R.layout.fragment_messages);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        conversationRepository = new ConversationRepository(requireContext());

        view.findViewById(R.id.btn_compose).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.messages_compose, Toast.LENGTH_SHORT).show());

        listView = view.findViewById(R.id.conversations_list);
        emptyView = view.findViewById(R.id.messages_empty);
        searchInput = view.findViewById(R.id.messages_search);
        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
        MessagesConversationAdapter adapter = new MessagesConversationAdapter();
        listView.setAdapter(adapter);
        setupSearch(adapter);
        adapter.setOnConversationClickListener(item -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openChatDetail(
                        item.conversationId,
                        item.name,
                        item.avatarUrl,
                        item.peerUserId);
            }
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.messages_need_login);
            listView.setVisibility(View.GONE);
            return;
        }

        startListening(user.getUid(), adapter);
    }

    private void startListening(@NonNull String currentUserId, @NonNull MessagesConversationAdapter adapter) {
        stopListening();
        conversationsListener = conversationRepository.listenMyConversations(
                currentUserId,
                new ConversationRepository.ConversationListCallback() {
                    @Override
                    public void onConversationsLoaded(
                            @NonNull java.util.List<MessagesConversationAdapter.Item> items) {
                        allConversationItems.clear();
                        allConversationItems.addAll(items);
                        applySearchFilter(adapter);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        Toast.makeText(
                                requireContext(),
                                e.getMessage() != null ? e.getMessage() : "Firestore error",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void setupSearch(@NonNull MessagesConversationAdapter adapter) {
        if (searchInput == null) return;
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applySearchFilter(adapter);
            }
        });
    }

    private void applySearchFilter(@NonNull MessagesConversationAdapter adapter) {
        String keyword = searchInput != null && searchInput.getText() != null
                ? searchInput.getText().toString().trim().toLowerCase(Locale.ROOT)
                : "";
        List<MessagesConversationAdapter.Item> filtered = new ArrayList<>();
        for (MessagesConversationAdapter.Item item : allConversationItems) {
            String name = item.name != null ? item.name.toLowerCase(Locale.ROOT) : "";
            if (keyword.isEmpty() || name.contains(keyword)) {
                filtered.add(item);
            }
        }
        adapter.setItems(filtered);
        boolean empty = filtered.isEmpty();
        if (empty) {
            if (keyword.isEmpty()) {
                emptyView.setText(R.string.messages_empty);
            } else {
                emptyView.setText("Khong tim thay cuoc tro chuyen phu hop");
            }
        }
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void stopListening() {
        if (conversationsListener != null) {
            conversationsListener.remove();
            conversationsListener = null;
        }
    }

    @Override
    public void onDestroyView() {
        stopListening();
        super.onDestroyView();
    }
}
