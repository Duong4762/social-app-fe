package com.example.social_app.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class FirebaseManager {

    // -------------------------------------------------------
    // Firestore Collection Names
    // -------------------------------------------------------
    public static final String COLLECTION_USERS                = "users";
    public static final String COLLECTION_FOLLOWS              = "follows";
    public static final String COLLECTION_POSTS                = "posts";
    public static final String COLLECTION_POST_MEDIA           = "post_media";
    public static final String COLLECTION_POST_LIKES           = "post_likes";
    public static final String COLLECTION_COMMENTS             = "comments";
    public static final String COLLECTION_CONVERSATIONS        = "conversations";
    public static final String COLLECTION_CONVERSATION_MEMBERS = "conversation_members";
    public static final String COLLECTION_MESSAGES             = "messages";
    public static final String COLLECTION_MESSAGE_READS        = "message_reads";
    public static final String COLLECTION_NOTIFICATIONS        = "notifications";
    public static final String COLLECTION_REPORTS              = "reports";
    public static final String COLLECTION_BOOKMARKS            = "bookmarks";
    public static final String COLLECTION_STORIES = "stories";
    // -------------------------------------------------------
    // Firebase Storage Paths
    // -------------------------------------------------------
    public static final String STORAGE_AVATARS    = "avatars/";
    public static final String STORAGE_POST_MEDIA = "post_media/";

    // -------------------------------------------------------
    // Singleton
    // -------------------------------------------------------
    private static volatile FirebaseManager instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;

    private FirebaseManager() {
        auth      = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage   = FirebaseStorage.getInstance();
    }

    public static FirebaseManager getInstance() {
        if (instance == null) {
            synchronized (FirebaseManager.class) {
                if (instance == null) {
                    instance = new FirebaseManager();
                }
            }
        }
        return instance;
    }

    // -------------------------------------------------------
    // Accessors
    // -------------------------------------------------------
    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public FirebaseStorage getStorage() {
        return storage;
    }
}
