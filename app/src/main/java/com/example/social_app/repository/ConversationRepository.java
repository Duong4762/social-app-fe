package com.example.social_app.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.social_app.R;
import com.example.social_app.adapters.MessagesConversationAdapter;
import com.example.social_app.data.model.Conversation;
import com.example.social_app.data.model.ConversationMember;
import com.example.social_app.data.model.Message;
import com.example.social_app.data.model.MessageRead;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đồng bộ hội thoại với Firestore: lắng nghe danh sách theo user đăng nhập.
 */
public class ConversationRepository {

    private final FirebaseFirestore db = FirebaseManager.getInstance().getFirestore();
    private final Context appContext;

    public ConversationRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Lắng nghe mọi conversation mà {@code currentUserId} tham gia (qua {@code conversation_members}).
     */
    @NonNull
    public ListenerRegistration listenMyConversations(
            @NonNull String currentUserId,
            @NonNull ConversationListCallback callback) {
        return db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    if (snap == null || snap.isEmpty()) {
                        callback.onConversationsLoaded(Collections.emptyList());
                        return;
                    }
                    Set<String> ids = new LinkedHashSet<>();
                    for (QueryDocumentSnapshot d : snap) {
                        ConversationMember m = d.toObject(ConversationMember.class);
                        if (m != null && m.getConversationId() != null) {
                            ids.add(m.getConversationId());
                        }
                    }
                    loadConversationItems(new ArrayList<>(ids), currentUserId, callback);
                });
    }

    private void loadConversationItems(
            @NonNull List<String> conversationIds,
            @NonNull String currentUserId,
            @NonNull ConversationListCallback callback) {
        if (conversationIds.isEmpty()) {
            callback.onConversationsLoaded(Collections.emptyList());
            return;
        }
        List<Task<MessagesConversationAdapter.Item>> tasks = new ArrayList<>();
        for (String cid : conversationIds) {
            tasks.add(fetchConversationItem(cid, currentUserId));
        }
        Tasks.whenAllComplete(tasks)
                .addOnCompleteListener(task -> {
                    List<MessagesConversationAdapter.Item> items = new ArrayList<>();
                    for (Task<MessagesConversationAdapter.Item> t : tasks) {
                        if (t.isSuccessful() && t.getResult() != null) {
                            items.add(t.getResult());
                        }
                    }
                    items.sort(Comparator.comparingLong(
                            (MessagesConversationAdapter.Item i) -> i.lastActivityMillis).reversed());
                    callback.onConversationsLoaded(items);
                });
    }

    @NonNull
    private Task<MessagesConversationAdapter.Item> fetchConversationItem(
            @NonNull String conversationId,
            @NonNull String currentUserId) {
        return db.collection(FirebaseManager.COLLECTION_CONVERSATIONS)
                .document(conversationId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                        return Tasks.forResult(null);
                    }
                    Conversation conv = task.getResult().toObject(Conversation.class);
                    if (conv == null || conv.isGroup()) {
                        return Tasks.forResult(null);
                    }
                    return db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                            .whereEqualTo("conversationId", conversationId)
                            .get()
                            .continueWithTask(task2 -> {
                                if (!task2.isSuccessful() || task2.getResult() == null) {
                                    return Tasks.forResult(null);
                                }
                                String peerId = null;
                                for (QueryDocumentSnapshot d : task2.getResult()) {
                                    ConversationMember m = d.toObject(ConversationMember.class);
                                    if (m != null && m.getUserId() != null
                                            && !m.getUserId().equals(currentUserId)) {
                                        peerId = m.getUserId();
                                        break;
                                    }
                                }
                                if (peerId == null) {
                                    return Tasks.forResult(null);
                                }
                                final String peer = peerId;
                                Task<DocumentSnapshot> userTask =
                                        db.collection(FirebaseManager.COLLECTION_USERS)
                                                .document(peer)
                                                .get();
                                Task<QuerySnapshot> lastMsgTask = db
                                        .collection(FirebaseManager.COLLECTION_MESSAGES)
                                        .whereEqualTo("conversationId", conversationId)
                                        .orderBy("createdAt", Query.Direction.DESCENDING)
                                        .limit(1)
                                        .get();

                                return Tasks.whenAllComplete(userTask, lastMsgTask)
                                        .continueWith(t3 -> {
                                            DocumentSnapshot us = userTask.isSuccessful()
                                                    ? userTask.getResult() : null;
                                            QuerySnapshot qs = lastMsgTask.isSuccessful()
                                                    ? lastMsgTask.getResult() : null;
                                            return buildItem(
                                                    conversationId,
                                                    conv,
                                                    peer,
                                                    us,
                                                    qs);
                                        });
                            });
                });
    }

    @Nullable
    private MessagesConversationAdapter.Item buildItem(
            @NonNull String conversationId,
            @NonNull Conversation conv,
            @NonNull String peerUserId,
            @Nullable DocumentSnapshot userSnap,
            @Nullable QuerySnapshot lastMsgSnap) {
        User peer = userSnap != null && userSnap.exists()
                ? userSnap.toObject(User.class)
                : null;
        String name = peer != null && peer.getFullName() != null && !peer.getFullName().trim().isEmpty()
                ? peer.getFullName().trim()
                : (peer != null && peer.getUsername() != null ? peer.getUsername() : peerUserId);
        String avatarUrl = peer != null ? peer.getAvatarUrl() : null;

        String preview = "";
        boolean showPhoto = false;
        long lastActivity = 0L;
        Date convUpdated = conv.getUpdatedAt() != null ? conv.getUpdatedAt() : conv.getCreatedAt();
        if (convUpdated != null) {
            lastActivity = convUpdated.getTime();
        }

        if (lastMsgSnap != null && !lastMsgSnap.isEmpty()) {
            DocumentSnapshot lastDoc = lastMsgSnap.getDocuments().get(0);
            Message msg = lastDoc.toObject(Message.class);
            if (msg != null) {
                msg.setId(lastDoc.getId());
                if ("IMAGE".equalsIgnoreCase(msg.getMessageType())
                        || "VIDEO".equalsIgnoreCase(msg.getMessageType())) {
                    showPhoto = true;
                    preview = "IMAGE".equalsIgnoreCase(msg.getMessageType())
                            ? appContext.getString(R.string.message_preview_sent_photo)
                            : appContext.getString(R.string.message_preview_sent_video);
                } else {
                    preview = msg.getContent() != null ? msg.getContent() : "";
                }
                if (msg.getCreatedAt() != null) {
                    lastActivity = Math.max(lastActivity, msg.getCreatedAt().getTime());
                }
            }
        }
        if (preview.isEmpty()) {
            preview = appContext.getString(R.string.message_no_messages_yet);
        }

        String timeLabel = formatRelativeTime(lastActivity > 0 ? lastActivity : System.currentTimeMillis());

        return new MessagesConversationAdapter.Item(
                conversationId,
                peerUserId,
                name,
                preview,
                timeLabel,
                false,
                showPhoto,
                avatarUrl,
                lastActivity);
    }

    /**
     * Lắng nghe tin nhắn trong một cuộc hội thoại (theo thời gian tăng dần).
     */
    @NonNull
    public ListenerRegistration listenMessages(
            @NonNull String conversationId,
            @NonNull ChatMessagesCallback callback) {
        return db.collection(FirebaseManager.COLLECTION_MESSAGES)
                .whereEqualTo("conversationId", conversationId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    List<Message> list = new ArrayList<>();
                    if (snap != null) {
                        for (QueryDocumentSnapshot d : snap) {
                            Message m = d.toObject(Message.class);
                            if (m != null) {
                                m.setId(d.getId());
                                list.add(m);
                            }
                        }
                    }
                    callback.onMessages(list);
                });
    }

    /**
     * Gửi tin nhắn dạng chữ.
     */
    @NonNull
    public Task<Void> sendTextMessage(
            @NonNull String conversationId,
            @NonNull String senderId,
            @NonNull String peerUserId,
            @NonNull String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setRepliedMessageId(null);
        msg.setContent(content);
        msg.setMessageType("TEXT");
        return ensureDirectConversationExists(conversationId, senderId, peerUserId)
                .continueWithTask(task -> db.collection(FirebaseManager.COLLECTION_MESSAGES).add(msg))
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        return Tasks.forException(ex != null ? ex : new Exception("send failed"));
                    }
                    return touchConversationUpdatedAt(conversationId);
                });
    }

    /**
     * Gửi tin nhắn ảnh (content là image URL).
     */
    @NonNull
    public Task<Void> sendImageMessage(
            @NonNull String conversationId,
            @NonNull String senderId,
            @NonNull String peerUserId,
            @NonNull String imageUrl) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setRepliedMessageId(null);
        msg.setContent(imageUrl);
        msg.setMessageType("IMAGE");
        return ensureDirectConversationExists(conversationId, senderId, peerUserId)
                .continueWithTask(task -> db.collection(FirebaseManager.COLLECTION_MESSAGES).add(msg))
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        return Tasks.forException(ex != null ? ex : new Exception("send failed"));
                    }
                    return touchConversationUpdatedAt(conversationId);
                });
    }

    @NonNull
    public static String buildDirectConversationId(@NonNull String userAId, @NonNull String userBId) {
        return userAId.compareTo(userBId) <= 0
                ? userAId + "_" + userBId
                : userBId + "_" + userAId;
    }

    @NonNull
    public Task<String> findExistingDirectConversationId(
            @NonNull String userAId,
            @NonNull String userBId
    ) {
        Task<QuerySnapshot> userAMembersTask = db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("userId", userAId)
                .get();
        Task<QuerySnapshot> userBMembersTask = db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("userId", userBId)
                .get();

        return Tasks.whenAllSuccess(userAMembersTask, userBMembersTask)
                .continueWithTask(task -> {
                    QuerySnapshot userASnapshot = userAMembersTask.getResult();
                    QuerySnapshot userBSnapshot = userBMembersTask.getResult();
                    if (userASnapshot == null || userBSnapshot == null) {
                        return Tasks.forResult(null);
                    }

                    Set<String> userAConversationIds = new HashSet<>();
                    for (QueryDocumentSnapshot doc : userASnapshot) {
                        String cid = doc.getString("conversationId");
                        if (cid != null && !cid.trim().isEmpty()) {
                            userAConversationIds.add(cid);
                        }
                    }
                    Set<String> sharedConversationIds = new HashSet<>();
                    for (QueryDocumentSnapshot doc : userBSnapshot) {
                        String cid = doc.getString("conversationId");
                        if (cid != null && userAConversationIds.contains(cid)) {
                            sharedConversationIds.add(cid);
                        }
                    }
                    if (sharedConversationIds.isEmpty()) {
                        return Tasks.forResult(null);
                    }

                    List<Task<DocumentSnapshot>> conversationTasks = new ArrayList<>();
                    for (String cid : sharedConversationIds) {
                        conversationTasks.add(
                                db.collection(FirebaseManager.COLLECTION_CONVERSATIONS)
                                        .document(cid)
                                        .get()
                        );
                    }

                    return Tasks.whenAllComplete(conversationTasks)
                            .continueWith(innerTask -> {
                                String bestConversationId = null;
                                long bestUpdatedAt = Long.MIN_VALUE;
                                for (Task<DocumentSnapshot> conversationTask : conversationTasks) {
                                    if (!conversationTask.isSuccessful() || conversationTask.getResult() == null) {
                                        continue;
                                    }
                                    DocumentSnapshot snapshot = conversationTask.getResult();
                                    if (!snapshot.exists()) {
                                        continue;
                                    }
                                    Conversation conversation = snapshot.toObject(Conversation.class);
                                    if (conversation == null || conversation.isGroup()) {
                                        continue;
                                    }
                                    Date updatedAt = conversation.getUpdatedAt() != null
                                            ? conversation.getUpdatedAt()
                                            : conversation.getCreatedAt();
                                    long ts = updatedAt != null ? updatedAt.getTime() : 0L;
                                    if (bestConversationId == null || ts > bestUpdatedAt) {
                                        bestConversationId = snapshot.getId();
                                        bestUpdatedAt = ts;
                                    }
                                }
                                return bestConversationId;
                            });
                });
    }

    @NonNull
    private Task<Void> ensureDirectConversationExists(
            @NonNull String conversationId,
            @NonNull String userAId,
            @NonNull String userBId
    ) {
        WriteBatch batch = db.batch();

        Map<String, Object> conversationPayload = new HashMap<>();
        conversationPayload.put("id", conversationId);
        conversationPayload.put("name", null);
        conversationPayload.put("isGroup", false);
        conversationPayload.put("createdBy", userAId);
        conversationPayload.put("createdAt", FieldValue.serverTimestamp());
        conversationPayload.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(
                db.collection(FirebaseManager.COLLECTION_CONVERSATIONS).document(conversationId),
                conversationPayload,
                SetOptions.merge()
        );

        batch.set(
                db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                        .document(conversationId + "_" + userAId),
                buildConversationMemberPayload(conversationId, userAId),
                SetOptions.merge()
        );
        batch.set(
                db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                        .document(conversationId + "_" + userBId),
                buildConversationMemberPayload(conversationId, userBId),
                SetOptions.merge()
        );

        return batch.commit();
    }

    @NonNull
    private Task<Void> touchConversationUpdatedAt(@NonNull String conversationId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection(FirebaseManager.COLLECTION_CONVERSATIONS)
                .document(conversationId)
                .set(payload, SetOptions.merge());
    }

    @NonNull
    private Map<String, Object> buildConversationMemberPayload(
            @NonNull String conversationId,
            @NonNull String userId
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", conversationId + "_" + userId);
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("role", "MEMBER");
        payload.put("joinedAt", FieldValue.serverTimestamp());
        return payload;
    }

    /**
     * Ghi nhận người dùng hiện tại đã đọc một tin (tin do đối phương gửi). Document id cố định để idempotent.
     */
    public void ensureMessageReadByReader(
            @NonNull String conversationId,
            @NonNull String messageId,
            @NonNull String readerUserId) {
        String docId = messageId + "_" + readerUserId;
        MessageRead mr = new MessageRead();
        mr.setMessageId(messageId);
        mr.setUserId(readerUserId);
        mr.setConversationId(conversationId);
        db.collection(FirebaseManager.COLLECTION_MESSAGE_READS)
                .document(docId)
                .set(mr, SetOptions.merge());
    }

    /**
     * Realtime: các tin mà {@code peerUserId} (đối phương) đã đọc trong hội thoại — dùng để hiện ✓✓ trên tin gửi của mình.
     */
    @NonNull
    public ListenerRegistration listenPeerReadReceipts(
            @NonNull String conversationId,
            @NonNull String peerUserId,
            @NonNull PeerReadReceiptsCallback callback) {
        return db.collection(FirebaseManager.COLLECTION_MESSAGE_READS)
                .whereEqualTo("conversationId", conversationId)
                .whereEqualTo("userId", peerUserId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    Set<String> ids = new HashSet<>();
                    if (snap != null) {
                        for (QueryDocumentSnapshot d : snap) {
                            MessageRead mr = d.toObject(MessageRead.class);
                            if (mr != null && mr.getMessageId() != null) {
                                ids.add(mr.getMessageId());
                            }
                        }
                    }
                    callback.onPeerReadMessageIds(ids);
                });
    }

    public interface PeerReadReceiptsCallback {
        void onPeerReadMessageIds(@NonNull Set<String> messageIds);

        void onError(@NonNull Exception e);
    }

    public interface ChatMessagesCallback {
        void onMessages(@NonNull List<Message> messages);

        void onError(@NonNull Exception e);
    }

    @NonNull
    public ListenerRegistration listenUserActiveStatus(
            @NonNull String userId,
            @NonNull UserActiveStatusCallback callback) {
        return db.collection(FirebaseManager.COLLECTION_USERS)
                .document(userId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    boolean isActive = false;
                    if (snap != null && snap.exists()) {
                        Boolean value = snap.getBoolean("isActive");
                        isActive = value != null && value;
                    }
                    callback.onStatusChanged(isActive);
                });
    }

    public interface UserActiveStatusCallback {
        void onStatusChanged(boolean isActive);

        void onError(@NonNull Exception e);
    }

    @NonNull
    private String formatRelativeTime(long timeMillis) {
        long now = System.currentTimeMillis();
        long diff = now - timeMillis;
        if (diff < 60_000L) {
            return appContext.getString(R.string.just_now);
        }
        if (diff < 3600_000L) {
            return appContext.getString(R.string.minutes_ago, (int) (diff / 60_000L));
        }
        if (diff < 86400_000L) {
            return appContext.getString(R.string.hours_ago, (int) (diff / 3600_000L));
        }
        if (diff < 604800_000L) {
            return appContext.getString(R.string.days_ago, (int) (diff / 86400_000L));
        }
        java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
        return df.format(new Date(timeMillis));
    }

    public interface ConversationListCallback {
        void onConversationsLoaded(@NonNull List<MessagesConversationAdapter.Item> items);

        void onError(@NonNull Exception e);
    }
}
