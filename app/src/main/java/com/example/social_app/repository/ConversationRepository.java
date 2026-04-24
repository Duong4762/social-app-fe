package com.example.social_app.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

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
import com.example.social_app.utils.LanguageUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Đồng bộ hội thoại với Firestore: lắng nghe danh sách theo user đăng nhập.
 */
public class ConversationRepository {

    private final FirebaseFirestore db = FirebaseManager.getInstance().getFirestore();
    private final Context appContext;

    /** Inbox: listener phụ theo từng {@code conversations/{id}} để preview cập nhật khi có tin (nhóm & DM). */
    private final List<ListenerRegistration> inboxConversationDocRegs = new ArrayList<>();
    private final Handler inboxDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable inboxDebounceLoad;
    @Nullable
    private volatile List<String> inboxConversationIds = Collections.emptyList();
    @Nullable
    private volatile String inboxListenUserId;
    @Nullable
    private volatile ConversationListCallback inboxListCallback;

    public ConversationRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Chuỗi UI theo ngôn ngữ trong Cài đặt (không dùng trực tiếp {@link #appContext} cho getString). */
    @NonNull
    private Context localeContext() {
        return LanguageUtils.contextWithSavedLanguage(appContext);
    }

    /**
     * Lắng nghe mọi conversation mà {@code currentUserId} tham gia (qua {@code conversation_members}),
     * đồng thời lắng nghe từng document {@code conversations/{id}} để khi có tin mới (cập nhật {@code updatedAt})
     * danh sách inbox được làm mới — chỉ members thì không đủ cho nhóm/DM.
     */
    @NonNull
    public ListenerRegistration listenMyConversations(
            @NonNull String currentUserId,
            @NonNull ConversationListCallback callback) {
        inboxListenUserId = currentUserId;
        inboxListCallback = callback;
        ListenerRegistration membersReg = db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    clearInboxConversationDocListeners();
                    if (snap == null || snap.isEmpty()) {
                        inboxConversationIds = Collections.emptyList();
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
                    List<String> idList = new ArrayList<>(ids);
                    inboxConversationIds = idList;
                    attachInboxConversationDocumentListeners(idList);
                    loadConversationItems(idList, currentUserId, callback);
                });
        return () -> {
            inboxDebounceHandler.removeCallbacksAndMessages(null);
            inboxDebounceLoad = null;
            membersReg.remove();
            clearInboxConversationDocListeners();
            inboxConversationIds = Collections.emptyList();
            inboxListenUserId = null;
            inboxListCallback = null;
        };
    }

    private void clearInboxConversationDocListeners() {
        for (ListenerRegistration r : inboxConversationDocRegs) {
            r.remove();
        }
        inboxConversationDocRegs.clear();
    }

    private void attachInboxConversationDocumentListeners(@NonNull List<String> conversationIds) {
        for (String cid : conversationIds) {
            ListenerRegistration reg = db.collection(FirebaseManager.COLLECTION_CONVERSATIONS)
                    .document(cid)
                    .addSnapshotListener(MetadataChanges.INCLUDE, (docSnap, err) -> {
                        if (err != null) {
                            ConversationListCallback cb = inboxListCallback;
                            if (cb != null) {
                                cb.onError(err);
                            }
                            return;
                        }
                        scheduleDebouncedInboxReload();
                    });
            inboxConversationDocRegs.add(reg);
        }
    }

    private void scheduleDebouncedInboxReload() {
        ConversationListCallback cb = inboxListCallback;
        String uid = inboxListenUserId;
        List<String> ids = inboxConversationIds;
        if (cb == null || uid == null || ids == null || ids.isEmpty()) {
            return;
        }
        inboxDebounceHandler.removeCallbacks(inboxDebounceLoad);
        inboxDebounceLoad = () -> loadConversationItems(new ArrayList<>(ids), uid, cb);
        inboxDebounceHandler.postDelayed(inboxDebounceLoad, 220);
    }

    /**
     * Làm mới danh sách inbox (preview, unread). Hữu ích khi quay lại từ chat hoặc khi chỉ {@code message_reads} đổi.
     */
    public void reloadConversationList(
            @NonNull String currentUserId,
            @NonNull ConversationListCallback callback) {
        db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener(snap ->
                        loadConversationsForMemberQuerySnapshot(snap, currentUserId, callback))
                .addOnFailureListener(callback::onError);
    }

    private void loadConversationsForMemberQuerySnapshot(
            @Nullable QuerySnapshot snap,
            @NonNull String currentUserId,
            @NonNull ConversationListCallback callback) {
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
                    if (conv == null) {
                        return Tasks.forResult(null);
                    }
                    if (conv.isGroup()) {
                        return fetchGroupConversationItem(conversationId, conv, currentUserId);
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
                                        .continueWithTask(t3 -> {
                                            DocumentSnapshot us = userTask.isSuccessful()
                                                    ? userTask.getResult() : null;
                                            QuerySnapshot qs = lastMsgTask.isSuccessful()
                                                    ? lastMsgTask.getResult() : null;
                                            Message lastMsg = messageFromLastQuerySnapshot(qs);
                                            if (lastMsg == null || lastMsg.getId() == null) {
                                                return Tasks.forResult(buildItem(
                                                        conversationId,
                                                        conv,
                                                        peer,
                                                        us,
                                                        null,
                                                        false,
                                                        currentUserId));
                                            }
                                            boolean fromPeer = lastMsg.getSenderId() != null
                                                    && peer.equals(lastMsg.getSenderId());
                                            if (!fromPeer) {
                                                return Tasks.forResult(buildItem(
                                                        conversationId,
                                                        conv,
                                                        peer,
                                                        us,
                                                        lastMsg,
                                                        false,
                                                        currentUserId));
                                            }
                                            String readDocId = lastMsg.getId() + "_" + currentUserId;
                                            return db.collection(FirebaseManager.COLLECTION_MESSAGE_READS)
                                                    .document(readDocId)
                                                    .get()
                                                    .continueWith(readTask -> {
                                                        boolean unread = readTask.isSuccessful()
                                                                && readTask.getResult() != null
                                                                && !readTask.getResult().exists();
                                                        return buildItem(
                                                                conversationId,
                                                                conv,
                                                                peer,
                                                                us,
                                                                lastMsg,
                                                                unread,
                                                                currentUserId);
                                                    });
                                        });
                            });
                });
    }

    @Nullable
    private static Message messageFromLastQuerySnapshot(@Nullable QuerySnapshot lastMsgSnap) {
        if (lastMsgSnap == null || lastMsgSnap.isEmpty()) {
            return null;
        }
        DocumentSnapshot lastDoc = lastMsgSnap.getDocuments().get(0);
        Message msg = lastDoc.toObject(Message.class);
        if (msg != null) {
            msg.setId(lastDoc.getId());
        }
        return msg;
    }

    @Nullable
    private MessagesConversationAdapter.Item buildItem(
            @NonNull String conversationId,
            @NonNull Conversation conv,
            @NonNull String peerUserId,
            @Nullable DocumentSnapshot userSnap,
            @Nullable Message lastMsg,
            boolean unreadFromPeer,
            @NonNull String currentUserId) {
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

        if (lastMsg != null) {
            if ("IMAGE".equalsIgnoreCase(lastMsg.getMessageType())
                    || "VIDEO".equalsIgnoreCase(lastMsg.getMessageType())) {
                showPhoto = true;
                preview = "IMAGE".equalsIgnoreCase(lastMsg.getMessageType())
                        ? localeContext().getString(R.string.message_preview_sent_photo)
                        : localeContext().getString(R.string.message_preview_sent_video);
            } else {
                preview = lastMsg.getContent() != null ? lastMsg.getContent() : "";
            }
            if (lastMsg.getCreatedAt() != null) {
                lastActivity = Math.max(lastActivity, lastMsg.getCreatedAt().getTime());
            }
        }

        boolean fromSelf = lastMsg != null && lastMsg.getSenderId() != null
                && currentUserId.equals(lastMsg.getSenderId());
        if (fromSelf && !preview.isEmpty()) {
            preview = localeContext().getString(R.string.message_preview_you_prefix) + preview;
        }

        if (preview.isEmpty()) {
            preview = localeContext().getString(R.string.message_no_messages_yet);
        }

        boolean unread = unreadFromPeer && lastMsg != null && lastMsg.getSenderId() != null
                && peerUserId.equals(lastMsg.getSenderId());

        String timeLabel = formatRelativeTime(lastActivity > 0 ? lastActivity : System.currentTimeMillis());

        return new MessagesConversationAdapter.Item(
                conversationId,
                peerUserId,
                name,
                preview,
                timeLabel,
                unread,
                showPhoto,
                avatarUrl,
                lastActivity,
                false);
    }

    @NonNull
    private Task<MessagesConversationAdapter.Item> fetchGroupConversationItem(
            @NonNull String conversationId,
            @NonNull Conversation conv,
            @NonNull String currentUserId) {
        Task<QuerySnapshot> membersTask = db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("conversationId", conversationId)
                .get();
        Task<QuerySnapshot> lastMsgTask = db.collection(FirebaseManager.COLLECTION_MESSAGES)
                .whereEqualTo("conversationId", conversationId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get();
        return Tasks.whenAllComplete(membersTask, lastMsgTask)
                .continueWithTask(task -> {
                    if (!membersTask.isSuccessful() || membersTask.getResult() == null) {
                        return Tasks.forResult(null);
                    }
                    List<String> memberIds = new ArrayList<>();
                    for (QueryDocumentSnapshot d : membersTask.getResult()) {
                        ConversationMember m = d.toObject(ConversationMember.class);
                        if (m != null && m.getUserId() != null) {
                            memberIds.add(m.getUserId());
                        }
                    }
                    List<Task<DocumentSnapshot>> userTasks = new ArrayList<>();
                    for (String uid : memberIds) {
                        userTasks.add(db.collection(FirebaseManager.COLLECTION_USERS).document(uid).get());
                    }
                    return Tasks.whenAllComplete(userTasks)
                            .continueWithTask(t2 -> {
                                Map<String, User> byId = new HashMap<>();
                                for (Task<DocumentSnapshot> ut : userTasks) {
                                    if (ut.isSuccessful() && ut.getResult() != null && ut.getResult().exists()) {
                                        User u = ut.getResult().toObject(User.class);
                                        if (u != null) {
                                            u.setId(ut.getResult().getId());
                                            byId.put(u.getId(), u);
                                        }
                                    }
                                }
                                QuerySnapshot qs = lastMsgTask.isSuccessful()
                                        ? lastMsgTask.getResult() : null;
                                Message lastMsg = messageFromLastQuerySnapshot(qs);
                                if (lastMsg == null || lastMsg.getId() == null) {
                                    return Tasks.forResult(buildGroupItem(
                                            conversationId, conv, memberIds, byId, null, false, currentUserId));
                                }
                                boolean fromOther = lastMsg.getSenderId() != null
                                        && !currentUserId.equals(lastMsg.getSenderId());
                                if (!fromOther) {
                                    return Tasks.forResult(buildGroupItem(
                                            conversationId, conv, memberIds, byId, lastMsg, false, currentUserId));
                                }
                                String readDocId = lastMsg.getId() + "_" + currentUserId;
                                return db.collection(FirebaseManager.COLLECTION_MESSAGE_READS)
                                        .document(readDocId)
                                        .get()
                                        .continueWith(readTask -> {
                                            boolean unread = readTask.isSuccessful()
                                                    && readTask.getResult() != null
                                                    && !readTask.getResult().exists();
                                            return buildGroupItem(
                                                    conversationId,
                                                    conv,
                                                    memberIds,
                                                    byId,
                                                    lastMsg,
                                                    unread,
                                                    currentUserId);
                                        });
                            });
                });
    }

    @Nullable
    private MessagesConversationAdapter.Item buildGroupItem(
            @NonNull String conversationId,
            @NonNull Conversation conv,
            @NonNull List<String> memberIds,
            @NonNull Map<String, User> usersById,
            @Nullable Message lastMsg,
            boolean unreadFromOthers,
            @NonNull String currentUserId) {
        String stored = conv.getName();
        String displayName;
        if (stored != null && !stored.trim().isEmpty()) {
            displayName = stored.trim();
        } else {
            List<String> labels = new ArrayList<>();
            for (String uid : memberIds) {
                User u = usersById.get(uid);
                labels.add(u != null ? displayNameOf(u) : uid);
            }
            Collections.sort(labels, String.CASE_INSENSITIVE_ORDER);
            displayName = TextUtils.join(", ", labels);
        }

        String preview = "";
        boolean showPhoto = false;
        long lastActivity = 0L;
        Date convUpdated = conv.getUpdatedAt() != null ? conv.getUpdatedAt() : conv.getCreatedAt();
        if (convUpdated != null) {
            lastActivity = convUpdated.getTime();
        }

        if (lastMsg != null) {
            if ("IMAGE".equalsIgnoreCase(lastMsg.getMessageType())
                    || "VIDEO".equalsIgnoreCase(lastMsg.getMessageType())) {
                showPhoto = true;
                preview = "IMAGE".equalsIgnoreCase(lastMsg.getMessageType())
                        ? localeContext().getString(R.string.message_preview_sent_photo)
                        : localeContext().getString(R.string.message_preview_sent_video);
            } else {
                preview = lastMsg.getContent() != null ? lastMsg.getContent() : "";
            }
            if (lastMsg.getCreatedAt() != null) {
                lastActivity = Math.max(lastActivity, lastMsg.getCreatedAt().getTime());
            }
        }

        boolean fromSelf = lastMsg != null && lastMsg.getSenderId() != null
                && currentUserId.equals(lastMsg.getSenderId());
        if (fromSelf && !preview.isEmpty()) {
            preview = localeContext().getString(R.string.message_preview_you_prefix) + preview;
        }

        if (preview.isEmpty()) {
            preview = localeContext().getString(R.string.message_no_messages_yet);
        }

        boolean unread = unreadFromOthers && lastMsg != null && lastMsg.getSenderId() != null
                && !currentUserId.equals(lastMsg.getSenderId());

        String timeLabel = formatRelativeTime(lastActivity > 0 ? lastActivity : System.currentTimeMillis());

        return new MessagesConversationAdapter.Item(
                conversationId,
                "",
                displayName,
                preview,
                timeLabel,
                unread,
                showPhoto,
                null,
                lastActivity,
                true);
    }

    @NonNull
    private String displayNameOf(@NonNull User u) {
        if (u.getFullName() != null && !u.getFullName().trim().isEmpty()) {
            return u.getFullName().trim();
        }
        if (u.getUsername() != null && !u.getUsername().trim().isEmpty()) {
            return u.getUsername().trim();
        }
        return u.getId() != null ? u.getId() : "";
    }

    /**
     * Người dùng đã có hội thoại trực tiếp 1-1 (để gợi ý thêm vào nhóm).
     */
    public void loadUsersWithDirectConversation(
            @NonNull String currentUserId,
            @NonNull DirectPeersCallback callback) {
        db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        callback.onPeersLoaded(Collections.emptyList());
                        return;
                    }
                    Set<String> convIds = new LinkedHashSet<>();
                    for (QueryDocumentSnapshot d : snap) {
                        ConversationMember m = d.toObject(ConversationMember.class);
                        if (m != null && m.getConversationId() != null) {
                            convIds.add(m.getConversationId());
                        }
                    }
                    List<Task<User>> peerTasks = new ArrayList<>();
                    for (String cid : convIds) {
                        peerTasks.add(fetchPeerInDirectConversation(cid, currentUserId));
                    }
                    Tasks.whenAllComplete(peerTasks)
                            .addOnSuccessListener(unused -> {
                                Map<String, User> dedupe = new LinkedHashMap<>();
                                for (Task<User> t : peerTasks) {
                                    if (t.isSuccessful()) {
                                        User u = t.getResult();
                                        if (u != null && u.getId() != null) {
                                            dedupe.put(u.getId(), u);
                                        }
                                    }
                                }
                                callback.onPeersLoaded(new ArrayList<>(dedupe.values()));
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    @NonNull
    private Task<User> fetchPeerInDirectConversation(
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
                                final String pid = peerId;
                                return db.collection(FirebaseManager.COLLECTION_USERS)
                                        .document(pid)
                                        .get()
                                        .continueWith(task3 -> {
                                            if (!task3.isSuccessful() || task3.getResult() == null
                                                    || !task3.getResult().exists()) {
                                                return null;
                                            }
                                            User u = task3.getResult().toObject(User.class);
                                            if (u != null) {
                                                u.setId(pid);
                                            }
                                            return u;
                                        });
                            });
                });
    }

    /**
     * Tạo nhóm chat mới; trả về {@code conversationId}.
     */
    @NonNull
    public Task<String> createGroupConversation(
            @NonNull String creatorId,
            @NonNull List<String> otherMemberIds,
            @Nullable String groupName) {
        String conversationId = UUID.randomUUID().toString();
        WriteBatch batch = db.batch();
        Map<String, Object> convPayload = new HashMap<>();
        convPayload.put("id", conversationId);
        String trimmedName = groupName != null ? groupName.trim() : "";
        convPayload.put("name", trimmedName.isEmpty() ? null : trimmedName);
        convPayload.put("isGroup", true);
        convPayload.put("createdBy", creatorId);
        convPayload.put("createdAt", FieldValue.serverTimestamp());
        convPayload.put("updatedAt", FieldValue.serverTimestamp());
        batch.set(
                db.collection(FirebaseManager.COLLECTION_CONVERSATIONS).document(conversationId),
                convPayload,
                SetOptions.merge());
        Set<String> all = new HashSet<>(otherMemberIds);
        all.add(creatorId);
        for (String uid : all) {
            if (uid == null || uid.isEmpty()) {
                continue;
            }
            batch.set(
                    db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                            .document(conversationId + "_" + uid),
                    buildConversationMemberPayload(conversationId, uid),
                    SetOptions.merge());
        }
        return batch.commit().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                Exception ex = task.getException();
                return Tasks.forException(ex != null ? ex : new Exception("commit failed"));
            }
            return Tasks.forResult(conversationId);
        });
    }

    /**
     * Gửi tin nhắn chữ trong nhóm (không tạo hội thoại 1-1).
     */
    @NonNull
    public Task<Void> sendGroupTextMessage(
            @NonNull String conversationId,
            @NonNull String senderId,
            @NonNull String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setRepliedMessageId(null);
        msg.setContent(content);
        msg.setMessageType("TEXT");
        return db.collection(FirebaseManager.COLLECTION_MESSAGES).add(msg)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        return Tasks.forException(ex != null ? ex : new Exception("send failed"));
                    }
                    return touchConversationUpdatedAt(conversationId);
                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        return Tasks.forException(ex != null ? ex : new Exception("send failed"));
                    }
                    notifyGroupChatMembersAfterSend(conversationId, senderId);
                    return Tasks.forResult(null);
                });
    }

    /**
     * Gửi ảnh trong nhóm.
     */
    @NonNull
    public Task<Void> sendGroupImageMessage(
            @NonNull String conversationId,
            @NonNull String senderId,
            @NonNull String imageUrl) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setRepliedMessageId(null);
        msg.setContent(imageUrl);
        msg.setMessageType("IMAGE");
        return db.collection(FirebaseManager.COLLECTION_MESSAGES).add(msg)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        return Tasks.forException(ex != null ? ex : new Exception("send failed"));
                    }
                    return touchConversationUpdatedAt(conversationId);
                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception ex = task.getException();
                        return Tasks.forException(ex != null ? ex : new Exception("send failed"));
                    }
                    notifyGroupChatMembersAfterSend(conversationId, senderId);
                    return Tasks.forResult(null);
                });
    }

    public interface DirectPeersCallback {
        void onPeersLoaded(@NonNull List<User> peers);

        void onError(@NonNull Exception e);
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
                // INCLUDE: bắn lại khi server điền ServerTimestamp — tin không bị “mất” tới khi có createdAt.
                .addSnapshotListener(MetadataChanges.INCLUDE, (snap, e) -> {
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
                    // Gửi thông báo đến người nhận
                    sendNotification(peerUserId, senderId, "MESSAGE", conversationId);
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
                    // Gửi thông báo đến người nhận
                    sendNotification(peerUserId, senderId, "MESSAGE", conversationId);
                    return touchConversationUpdatedAt(conversationId);
                });
    }

    private void sendNotification(String userId, String actorId, String type, String referenceId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("actorId", actorId);
        notification.put("type", type);
        notification.put("referenceId", referenceId);
        notification.put("isRead", false); // Dùng isRead để đồng bộ với model Notification
        notification.put("createdAt", FieldValue.serverTimestamp());

        db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS).add(notification)
                .addOnSuccessListener(doc -> {
                    String notifDocId = doc.getId();
                    db.collection(FirebaseManager.COLLECTION_USERS).document(actorId).get()
                            .addOnSuccessListener(userDoc -> {
                                String actorName = userDoc.getString("fullName");
                                if (actorName == null || actorName.isEmpty()) {
                                    actorName = userDoc.getString("username");
                                }
                                if (actorName == null || actorName.isEmpty()) {
                                    actorName = "Ai đó";
                                }
                                String actorAvatar = userDoc.getString("avatarUrl");
                                String title = "Tin nhắn mới";
                                String body = actorName + " đã gửi cho bạn một tin nhắn";
                                sendPushNotification(
                                        userId,
                                        title,
                                        body,
                                        "MESSAGE",
                                        referenceId,
                                        notifDocId,
                                        actorId,
                                        actorName,
                                        actorAvatar);
                            });
                });
    }

    /**
     * Thông báo realtime + FCM cho mọi thành viên nhóm (trừ người gửi). {@code actorId} trên document = conversationId.
     */
    private void notifyGroupChatMembersAfterSend(
            @NonNull String conversationId,
            @NonNull String senderId) {
        Task<DocumentSnapshot> convTask = db.collection(FirebaseManager.COLLECTION_CONVERSATIONS)
                .document(conversationId)
                .get();
        Task<DocumentSnapshot> senderTask = db.collection(FirebaseManager.COLLECTION_USERS)
                .document(senderId)
                .get();
        Task<QuerySnapshot> membersTask = db.collection(FirebaseManager.COLLECTION_CONVERSATION_MEMBERS)
                .whereEqualTo("conversationId", conversationId)
                .get();
        Tasks.whenAllComplete(convTask, senderTask, membersTask)
                .addOnSuccessListener(unused -> {
                    DocumentSnapshot convDoc = convTask.getResult();
                    DocumentSnapshot senderDoc = senderTask.getResult();
                    QuerySnapshot membersSnap = membersTask.getResult();
                    if (membersSnap == null) {
                        return;
                    }
                    Conversation conv = convDoc != null && convDoc.exists()
                            ? convDoc.toObject(Conversation.class)
                            : null;
                    if (conv == null || !conv.isGroup()) {
                        return;
                    }
                    String groupTitle = "";
                    if (conv.getName() != null && !conv.getName().trim().isEmpty()) {
                        groupTitle = conv.getName().trim();
                    }
                    Context lc = localeContext();
                    String displayGroup = groupTitle.isEmpty()
                            ? lc.getString(R.string.chat_group_subtitle)
                            : groupTitle;
                    String senderName = "Ai đó";
                    if (senderDoc != null && senderDoc.exists()) {
                        String fn = senderDoc.getString("fullName");
                        String un = senderDoc.getString("username");
                        if (fn != null && !fn.trim().isEmpty()) {
                            senderName = fn.trim();
                        } else if (un != null && !un.trim().isEmpty()) {
                            senderName = un.trim();
                        }
                    }
                    String pushTitle = lc.getString(R.string.notification_groupchat_title);
                    String pushBody = lc.getString(R.string.notification_groupchat_body, senderName, displayGroup);

                    for (QueryDocumentSnapshot mdoc : membersSnap) {
                        ConversationMember mem = mdoc.toObject(ConversationMember.class);
                        if (mem == null || mem.getUserId() == null) {
                            continue;
                        }
                        String memberId = mem.getUserId();
                        if (memberId.equals(senderId)) {
                            continue;
                        }
                        Map<String, Object> notifPayload = new HashMap<>();
                        notifPayload.put("userId", memberId);
                        notifPayload.put("actorId", conversationId);
                        notifPayload.put("type", "GROUPCHAT");
                        notifPayload.put("referenceId", conversationId);
                        notifPayload.put("groupChatSenderName", senderName);
                        notifPayload.put("groupChatTitle", displayGroup);
                        notifPayload.put("isRead", false);
                        notifPayload.put("createdAt", FieldValue.serverTimestamp());

                        db.collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                                .add(notifPayload)
                                .addOnSuccessListener(docRef -> sendPushNotification(
                                        memberId,
                                        pushTitle,
                                        pushBody,
                                        "GROUPCHAT",
                                        conversationId,
                                        docRef.getId(),
                                        conversationId,
                                        displayGroup,
                                        null));
                    }
                });
    }

    private void sendPushNotification(
            @NonNull String targetUserId,
            @NonNull String title,
            @NonNull String body,
            @NonNull String type,
            @NonNull String refId,
            @Nullable String notifDocId,
            @Nullable String fcmActorId,
            @Nullable String fcmActorName,
            @Nullable String fcmActorAvatar) {
        db.collection(FirebaseManager.COLLECTION_USERS).document(targetUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String token = documentSnapshot.getString("fcmToken");
                        if (token != null && !token.isEmpty()) {
                            com.example.social_app.firebase.FcmSender.sendNotification(
                                    token,
                                    title,
                                    body,
                                    type,
                                    refId,
                                    targetUserId,
                                    fcmActorId,
                                    fcmActorName,
                                    fcmActorAvatar,
                                    notifDocId);
                        }
                    }
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
        Context lc = localeContext();
        long now = System.currentTimeMillis();
        long diff = now - timeMillis;
        if (diff < 60_000L) {
            return lc.getString(R.string.just_now);
        }
        if (diff < 3600_000L) {
            return lc.getString(R.string.minutes_ago, (int) (diff / 60_000L));
        }
        if (diff < 86400_000L) {
            return lc.getString(R.string.hours_ago, (int) (diff / 3600_000L));
        }
        if (diff < 604800_000L) {
            return lc.getString(R.string.days_ago, (int) (diff / 86400_000L));
        }
        Locale loc = lc.getResources().getConfiguration().getLocales().get(0);
        java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, loc);
        return df.format(new Date(timeMillis));
    }

    public interface ConversationListCallback {
        void onConversationsLoaded(@NonNull List<MessagesConversationAdapter.Item> items);

        void onError(@NonNull Exception e);
    }
}
