package com.example.social_app.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.adapters.ChatMessagesAdapter;
import com.example.social_app.data.model.Message;
import com.example.social_app.repository.ConversationRepository;
import com.example.social_app.utils.CloudinaryUploadUtil;
import com.example.social_app.utils.UserAvatarLoader;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private ListenerRegistration peerStatusListener;
    private ChatMessagesAdapter messagesAdapter;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;

    private String mConversationId;
    private String mPeerUid;
    private String mMyUid;
    private TextView headerStatus;
    private View headerOnlineDot;
    private View mediaPickerSheet;
    private RecyclerView mediaPickerList;
    private FloatingActionButton sendImageFab;
    private View chatInputContainer;
    private View chatTypingRow;
    private Uri selectedImageUri;
    private boolean isUploadingImage;
    private View rootView;
    private int basePaddingLeft;
    private int basePaddingTop;
    private int basePaddingRight;
    private int basePaddingBottom;
    private int imeBottomInset;
    private int mediaSheetInset;
    private final List<Message> mLastMessages = new ArrayList<>();
    private final List<Uri> galleryImageUris = new ArrayList<>();
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
        headerStatus = view.findViewById(R.id.chat_header_status);
        headerOnlineDot = view.findViewById(R.id.chat_header_online_dot);
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
        ImageButton attachBtn = view.findViewById(R.id.btn_chat_attach);
        mediaPickerSheet = view.findViewById(R.id.chat_media_picker_sheet);
        mediaPickerList = view.findViewById(R.id.chat_media_picker_list);
        sendImageFab = view.findViewById(R.id.btn_chat_send_image);
        chatInputContainer = view.findViewById(R.id.chat_input_container);
        chatTypingRow = view.findViewById(R.id.chat_typing_row);

        setupMediaPicker();
        attachBtn.setOnClickListener(v -> toggleMediaPicker());
        sendImageFab.setOnClickListener(v -> uploadAndSendSelectedImage());

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
        setupKeyboardBehavior(view, input);

        startListening(peerAvatar);
        listenPeerActiveStatus();
    }

    private void setupKeyboardBehavior(@NonNull View root, @NonNull EditText input) {
        rootView = root;
        basePaddingLeft = root.getPaddingLeft();
        basePaddingTop = root.getPaddingTop();
        basePaddingRight = root.getPaddingRight();
        basePaddingBottom = root.getPaddingBottom();
        imeBottomInset = 0;
        mediaSheetInset = 0;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            imeBottomInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            applyBottomInset();
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                hideMediaPicker();
                showIme(v);
            }
        });
        input.setOnClickListener(v -> {
            hideMediaPicker();
            showIme(v);
        });
    }

    private void applyBottomInset() {
        if (rootView == null) {
            return;
        }
        rootView.setPadding(
                basePaddingLeft,
                basePaddingTop,
                basePaddingRight,
                basePaddingBottom + imeBottomInset
        );
        float chatShift = -mediaSheetInset;
        if (recyclerView != null) {
            recyclerView.setTranslationY(chatShift);
        }
        if (chatTypingRow != null) {
            chatTypingRow.setTranslationY(chatShift);
        }
        if (chatInputContainer != null) {
            chatInputContainer.setTranslationY(chatShift);
        }
    }

    private void showIme(@NonNull View target) {
        WindowInsetsControllerCompat controller =
                ViewCompat.getWindowInsetsController(target);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.ime());
        }
    }

    private void setupMediaPicker() {
        if (mediaPickerList == null) {
            return;
        }
        mediaPickerList.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mediaPickerList.setAdapter(new GalleryImagesAdapter());
    }

    private void toggleMediaPicker() {
        if (mediaPickerSheet == null) {
            return;
        }
        if (mediaPickerSheet.getVisibility() == View.VISIBLE) {
            hideMediaPicker();
            return;
        }
        View focused = requireView().findFocus();
        if (focused != null) {
            focused.clearFocus();
        }
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(requireView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.ime());
        }
        showMediaPicker();
    }

    private void showMediaPicker() {
        if (mediaPickerSheet == null) {
            return;
        }
        mediaPickerSheet.setVisibility(View.VISIBLE);
        selectedImageUri = null;
        updateSendImageFabState();
        if (!loadDeviceImagesIfPermitted()) {
            Toast.makeText(requireContext(), R.string.chat_need_image_permission, Toast.LENGTH_SHORT).show();
        }
        mediaPickerSheet.post(this::updateMediaSheetInset);
    }

    private void hideMediaPicker() {
        if (mediaPickerSheet == null) {
            return;
        }
        mediaPickerSheet.setVisibility(View.GONE);
        selectedImageUri = null;
        mediaSheetInset = 0;
        applyBottomInset();
        updateSendImageFabState();
    }

    private void updateMediaSheetInset() {
        if (mediaPickerSheet == null || rootView == null || mediaPickerSheet.getVisibility() != View.VISIBLE) {
            mediaSheetInset = 0;
            applyBottomInset();
            return;
        }
        mediaSheetInset = mediaPickerSheet.getHeight();
        applyBottomInset();
    }

    private boolean loadDeviceImagesIfPermitted() {
        String permission = android.os.Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, 7001);
            return false;
        }
        loadDeviceImages();
        return true;
    }

    private void loadDeviceImages() {
        galleryImageUris.clear();
        String[] projection = new String[]{MediaStore.Images.Media._ID};
        String orderBy = MediaStore.Images.Media.DATE_ADDED + " DESC";
        Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                orderBy
        );
        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        String.valueOf(id)
                );
                galleryImageUris.add(contentUri);
                if (galleryImageUris.size() >= 120) {
                    break;
                }
            }
            cursor.close();
        }
        if (mediaPickerList != null && mediaPickerList.getAdapter() != null) {
            mediaPickerList.getAdapter().notifyDataSetChanged();
        }
    }

    private void uploadAndSendSelectedImage() {
        if (selectedImageUri == null || isUploadingImage) {
            return;
        }
        isUploadingImage = true;
        updateSendImageFabState();
        CloudinaryUploadUtil.uploadImage(requireContext(), selectedImageUri, new CloudinaryUploadUtil.UploadCallback() {
            @Override
            public void onSuccess(String secureUrl, String publicId) {
                repository.sendImageMessage(mConversationId, mMyUid, secureUrl)
                        .addOnCompleteListener(task -> {
                            isUploadingImage = false;
                            if (task.isSuccessful()) {
                                hideMediaPicker();
                            } else {
                                Toast.makeText(requireContext(), R.string.chat_send_failed, Toast.LENGTH_SHORT).show();
                            }
                            updateSendImageFabState();
                        });
            }

            @Override
            public void onError(String message, Throwable throwable) {
                isUploadingImage = false;
                updateSendImageFabState();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSendImageFabState() {
        if (sendImageFab == null) {
            return;
        }
        sendImageFab.setVisibility(selectedImageUri != null ? View.VISIBLE : View.GONE);
        sendImageFab.setEnabled(selectedImageUri != null && !isUploadingImage);
        sendImageFab.setAlpha(sendImageFab.isEnabled() ? 1f : 0.5f);
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
        if (peerStatusListener != null) {
            peerStatusListener.remove();
            peerStatusListener = null;
        }
    }

    private void listenPeerActiveStatus() {
        if (mPeerUid == null || mPeerUid.trim().isEmpty()) {
            return;
        }
        peerStatusListener = repository.listenUserActiveStatus(
                mPeerUid,
                new ConversationRepository.UserActiveStatusCallback() {
                    @Override
                    public void onStatusChanged(boolean isActive) {
                        updateHeaderStatusUi(isActive);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        updateHeaderStatusUi(false);
                    }
                });
    }

    private void updateHeaderStatusUi(boolean isActive) {
        if (headerStatus != null) {
            headerStatus.setText(isActive
                    ? R.string.chat_active_now
                    : R.string.chat_not_active_now);
        }
        if (headerOnlineDot != null) {
            headerOnlineDot.setVisibility(isActive ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 7001) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                loadDeviceImages();
            } else {
                Toast.makeText(requireContext(), R.string.chat_cannot_read_images, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final class GalleryImagesAdapter extends RecyclerView.Adapter<GalleryImagesAdapter.ImageVH> {
        @NonNull
        @Override
        public ImageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_gallery_image, parent, false);
            return new ImageVH(item);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageVH holder, int position) {
            Uri uri = galleryImageUris.get(position);
            holder.image.setImageURI(uri);
            boolean isSelected = uri.equals(selectedImageUri);
            holder.selectedOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                selectedImageUri = uri;
                notifyDataSetChanged();
                updateSendImageFabState();
            });
        }

        @Override
        public int getItemCount() {
            return galleryImageUris.size();
        }

        final class ImageVH extends RecyclerView.ViewHolder {
            private final ImageView image;
            private final View selectedOverlay;

            ImageVH(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.chat_gallery_image);
                selectedOverlay = itemView.findViewById(R.id.chat_gallery_selected_overlay);
            }
        }
    }

    @Override
    public void onDestroyView() {
        stopListening();
        super.onDestroyView();
    }
}
