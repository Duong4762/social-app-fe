package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.View;
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

public class MessagesFragment extends Fragment {

    private ConversationRepository conversationRepository;
    private ListenerRegistration conversationsListener;
    private TextView emptyView;
    private RecyclerView listView;

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
        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
        MessagesConversationAdapter adapter = new MessagesConversationAdapter();
        listView.setAdapter(adapter);
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
                        adapter.setItems(items);
                        boolean empty = items.isEmpty();
                        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
                        if (empty) {
                            emptyView.setText(R.string.messages_empty);
                        }
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
