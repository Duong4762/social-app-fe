package com.example.social_app.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.adapters.ChatMessagesAdapter;
import com.example.social_app.data.model.Message;
import com.example.social_app.repository.ConversationRepository;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatDetailFragment extends Fragment {

    private static final String ARG_CONVERSATION_ID = "conversation_id";
    private static final String ARG_PEER_NAME = "peer_name";
    private static final String ARG_PEER_AVATAR = "peer_avatar";
    private static final String ARG_PEER_UID = "peer_uid";

    private ConversationRepository repository;
    private ListenerRegistration messagesListener;
    private ListenerRegistration readReceiptsListener;
    private ChatMessagesAdapter messagesAdapter;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;

    private String mConversationId;
    private String mPeerUid;
    private String mMyUid;
    private final List<Message> mLastMessages = new ArrayList<>();
    private Set<String> mPeerReadMessageIds = Collections.emptySet();
    /** Tránh ghi trùng message_reads khi snapshot lặp lại. */
    private final Set<String> mMarkedIncomingReadIds = new HashSet<>();

    public ChatDetailFragment() {
        super(R.layout.fragment_chat_detail);
    }

    @NonNull
    public static ChatDetailFragment newInstance(
            @NonNull String conversationId,
            @NonNull String peerName,
            @Nullable String peerAvatarUrl,
            @NonNull String peerUserId) {
        ChatDetailFragment f = new ChatDetailFragment();
        Bundle b = new Bundle();
        b.putString(ARG_CONVERSATION_ID, conversationId);
        b.putString(ARG_PEER_NAME, peerName);
        b.putString(ARG_PEER_AVATAR, peerAvatarUrl != null ? peerAvatarUrl : "");
        b.putString(ARG_PEER_UID, peerUserId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        String conversationId = args.getString(ARG_CONVERSATION_ID);
        String peerName = args.getString(ARG_PEER_NAME);
        String peerAvatar = args.getString(ARG_PEER_AVATAR);
        String peerUid = args.getString(ARG_PEER_UID);
        if (conversationId == null || peerName == null || peerUid == null) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }
        if (peerAvatar != null && peerAvatar.isEmpty()) {
            peerAvatar = null;
        }

        mConversationId = conversationId;
        mPeerUid = peerUid;

        repository = new ConversationRepository(requireContext());

        ImageButton back = view.findViewById(R.id.btn_chat_back);
        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        ImageView headerAvatar = view.findViewById(R.id.chat_header_avatar);
        TextView headerName = view.findViewById(R.id.chat_header_name);
        headerName.setText(peerName);
        UserAvatarLoader.load(headerAvatar, peerAvatar);

        view.findViewById(R.id.btn_chat_info).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.chat_info, Toast.LENGTH_SHORT).show());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), R.string.messages_need_login, Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }
        mMyUid = user.getUid();

        messagesAdapter = new ChatMessagesAdapter(mMyUid, peerAvatar);
        recyclerView = view.findViewById(R.id.chat_messages_list);
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(messagesAdapter);

        EditText input = view.findViewById(R.id.chat_message_input);
        ImageButton sendBtn = view.findViewById(R.id.btn_chat_send);
        Runnable sendAction = () -> {
            String text = input.getText() != null ? input.getText().toString().trim() : "";
            if (TextUtils.isEmpty(text)) {
                return;
            }
            input.setText("");
            repository.sendTextMessage(mConversationId, mMyUid, text)
                    .addOnFailureListener(e -> Toast.makeText(
                            requireContext(),
                            R.string.chat_send_failed,
                            Toast.LENGTH_SHORT
                    ).show());
        };
        sendBtn.setOnClickListener(v -> sendAction.run());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean hasChar = s != null && s.length() > 0;
                sendBtn.setVisibility(hasChar ? View.VISIBLE : View.GONE);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendAction.run();
                return true;
            }
            return false;
        });

        startListening(peerAvatar);
    }

    private void startListening(@Nullable String peerAvatar) {
        stopListening();
        mLastMessages.clear();
        mPeerReadMessageIds = Collections.emptySet();

        readReceiptsListener = repository.listenPeerReadReceipts(
                mConversationId,
                mPeerUid,
                new ConversationRepository.PeerReadReceiptsCallback() {
                    @Override
                    public void onPeerReadMessageIds(@NonNull Set<String> messageIds) {
                        mPeerReadMessageIds = messageIds;
                        applyMessagesToAdapter(peerAvatar);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

        messagesListener = repository.listenMessages(
                mConversationId,
                new ConversationRepository.ChatMessagesCallback() {
                    @Override
                    public void onMessages(@NonNull List<Message> messages) {
                        messagesAdapter.setPeerAvatarUrl(peerAvatar);
                        mLastMessages.clear();
                        mLastMessages.addAll(messages);
                        markIncomingFromPeerAsRead(messages);
                        applyMessagesToAdapter(peerAvatar);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Tin do đối phương gửi: ghi nhận mình đã đọc (để đối phương thấy ✓✓ trên tin của họ). */
    private void markIncomingFromPeerAsRead(@NonNull List<Message> messages) {
        for (Message m : messages) {
            if (m.getId() == null || m.getCreatedAt() == null) {
                continue;
            }
            if (!mPeerUid.equals(m.getSenderId())) {
                continue;
            }
            if (mMarkedIncomingReadIds.contains(m.getId())) {
                continue;
            }
            mMarkedIncomingReadIds.add(m.getId());
            repository.ensureMessageReadByReader(mConversationId, m.getId(), mMyUid);
        }
    }

    private void applyMessagesToAdapter(@Nullable String peerAvatar) {
        messagesAdapter.setPeerAvatarUrl(peerAvatar);
        messagesAdapter.submitMessages(requireContext(), mLastMessages, mPeerReadMessageIds);
        if (recyclerView != null && messagesAdapter.getItemCount() > 0) {
            recyclerView.post(() ->
                    recyclerView.scrollToPosition(messagesAdapter.getItemCount() - 1));
        }
    }

    private void stopListening() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        if (readReceiptsListener != null) {
            readReceiptsListener.remove();
            readReceiptsListener = null;
        }
    }

    @Override
    public void onDestroyView() {
        stopListening();
        super.onDestroyView();
    }
}
