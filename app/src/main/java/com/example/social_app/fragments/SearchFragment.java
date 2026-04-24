package com.example.social_app.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import com.example.social_app.adapters.PostAdapter;
import com.example.social_app.adapters.UserSearchAdapter;
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.viewmodels.HomeViewModel;
import com.example.social_app.viewmodels.NewPostViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.ViewModelProvider;

public class SearchFragment extends Fragment implements PostAdapter.OnPostActionListener {

    private EditText searchInput;
    private TextView suggestedLabel;
    private RecyclerView suggestedUsersRecycler;
    private UserSearchAdapter suggestedAdapter;

    private TabLayout tabLayout;
    private RecyclerView usersRecyclerView;
    private RecyclerView postsRecyclerView;
    private ProgressBar searchLoadingProgress;
    private ProgressBar globalLoadingProgress;
    private TextView searchEmptyStateText;
    private View searchResultsContainer;

    private UserSearchAdapter userSearchAdapter;
    private PostAdapter postSearchAdapter;
    private HomeViewModel homeViewModel;
    private NewPostViewModel newPostViewModel;

    private List<User> allUsers = new ArrayList<>();
    private List<Post> allPosts = new ArrayList<>();
    private List<User> filteredUsers = new ArrayList<>();
    private List<Post> filteredPosts = new ArrayList<>();

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private FirebaseFirestore firestore;

    private boolean isSearchMode = false;
    private String lastSearchQuery = "";

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);

        initViews(view);
        setupRecyclerViews();
        setupSearchListener();
        firestore = FirebaseManager.getInstance().getFirestore();
        loadSearchData();
    }

    private void initViews(View view) {
        searchInput = view.findViewById(R.id.etSearch);
        suggestedLabel = view.findViewById(R.id.suggested_label);
        suggestedUsersRecycler = view.findViewById(R.id.suggested_users_recycler);
        searchResultsContainer = view.findViewById(R.id.searchResultsContainer);
        tabLayout = view.findViewById(R.id.tabLayout);
        usersRecyclerView = view.findViewById(R.id.usersRecyclerView);
        postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        searchLoadingProgress = view.findViewById(R.id.searchLoadingProgress);
        globalLoadingProgress = view.findViewById(R.id.globalLoadingProgress);
        searchEmptyStateText = view.findViewById(R.id.searchEmptyStateText);

        // Khởi tạo tab
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_users)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_posts)));
    }

    private void setupRecyclerViews() {
        // Suggested users adapter (dùng chung UserSearchAdapter)
        suggestedUsersRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        suggestedAdapter = new UserSearchAdapter(requireContext(), new UserSearchAdapter.OnUserActionListener() {
            @Override
            public void onUserClicked(User user) {
                openOtherProfile(user);
            }

            @Override
            public void onFollowClicked(User user, int position) {
                Toast.makeText(requireContext(), getString(R.string.follow_user, user.getFullName()), Toast.LENGTH_SHORT).show();
            }
        });
        suggestedUsersRecycler.setAdapter(suggestedAdapter);

        // Search users adapter
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        userSearchAdapter = new UserSearchAdapter(requireContext(), new UserSearchAdapter.OnUserActionListener() {
            @Override
            public void onUserClicked(User user) {
                openOtherProfile(user);
            }

            @Override
            public void onFollowClicked(User user, int position) {
                Toast.makeText(requireContext(), getString(R.string.follow_user, user.getFullName()), Toast.LENGTH_SHORT).show();
            }
        });
        usersRecyclerView.setAdapter(userSearchAdapter);

        // Search posts adapter
        postsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        postSearchAdapter = new PostAdapter(requireContext(), this);
        postSearchAdapter.setUseSearchLayout(true);
        postsRecyclerView.setAdapter(postSearchAdapter);

        // Tab listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateResultTabVisibility(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                updateResultTabVisibility(tab.getPosition());
            }
        });
    }

    private void setupSearchListener() {
        // Khi nhấn Enter trên bàn phím
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = searchInput.getText().toString().trim();
                lastSearchQuery = query;
                performSearch(query);
                return true;
            }
            return false;
        });

        // Khi xóa hết text -> quay về Suggested
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().trim().isEmpty() && isSearchMode) {
                    // Quay lại suggested users
                    lastSearchQuery = "";
                    isSearchMode = false;
                    showSuggestedMode();
                }
            }
        });
    }

    private void loadSearchData() {
        loadUsersFromFirebase();
        loadPostsFromFirebase();
    }

    private void loadPostsFromFirebase() {
        setGlobalLoading(true);
        firestore.collection(FirebaseManager.COLLECTION_POSTS)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Post> posts = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        Post post = document.toObject(Post.class);
                        if (post != null) {
                            post.setId(document.getId());
                            posts.add(post);
                        }
                    }
                    if (!isAdded()) return;
                    setGlobalLoading(false);
                    allPosts = posts;
                    if (isSearchMode && !lastSearchQuery.isEmpty()) {
                        performSearch(lastSearchQuery);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setGlobalLoading(false);
                    allPosts = new ArrayList<>();
                    Toast.makeText(requireContext(), "Cannot load posts", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUsersFromFirebase() {
        setGlobalLoading(true);
        String currentUid = FirebaseManager.getInstance().getAuth().getCurrentUser() != null
                ? FirebaseManager.getInstance().getAuth().getCurrentUser().getUid()
                : null;

        firestore.collection(FirebaseManager.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<User> users = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        User user = document.toObject(User.class);
                        if (user == null) {
                            continue;
                        }

                        if (user.getId() == null || user.getId().isEmpty()) {
                            user.setId(document.getId());
                        }

                        if (currentUid != null && currentUid.equals(user.getId())) {
                            continue;
                        }
                        if (isAdminUser(user)) {
                            continue;
                        }
                        users.add(user);
                    }

                    if (!isAdded()) {
                        return;
                    }
                    setGlobalLoading(false);
                    allUsers = users;
                    if (isSearchMode && !lastSearchQuery.isEmpty()) {
                        performSearch(lastSearchQuery);
                    } else {
                        showSuggestedMode();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    setGlobalLoading(false);
                    allUsers = new ArrayList<>();
                    showSuggestedMode();
                    Toast.makeText(requireContext(), "Cannot load users", Toast.LENGTH_SHORT).show();
                });
    }

    private void showSuggestedMode() {
        isSearchMode = false;

        // Hiển thị TẤT CẢ user trong danh sách Suggested
        suggestedAdapter.setUsers(allUsers);

        suggestedLabel.setVisibility(View.VISIBLE);
        suggestedUsersRecycler.setVisibility(View.VISIBLE);
        searchResultsContainer.setVisibility(View.GONE);
    }

    private void performSearch(String query) {
        lastSearchQuery = query.trim();
        if (query.isEmpty()) {
            showSuggestedMode();
            return;
        }

        isSearchMode = true;
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // Chuyển sang chế độ search
        suggestedLabel.setVisibility(View.GONE);
        suggestedUsersRecycler.setVisibility(View.GONE);
        searchResultsContainer.setVisibility(View.VISIBLE);
        searchLoadingProgress.setVisibility(View.VISIBLE);
        usersRecyclerView.setVisibility(View.GONE);
        postsRecyclerView.setVisibility(View.GONE);
        tabLayout.setVisibility(View.GONE);
        searchEmptyStateText.setVisibility(View.GONE);

        executor.execute(() -> {
            // Lọc users
            List<User> users = new ArrayList<>();
            for (User user : allUsers) {
                String username = user.getUsername() == null ? "" : user.getUsername().toLowerCase(Locale.ROOT);
                String fullName = user.getFullName() == null ? "" : user.getFullName().toLowerCase(Locale.ROOT);
                if (username.contains(lowerQuery) || fullName.contains(lowerQuery)) {
                    users.add(user);
                }
            }

            // Lọc posts
            List<Post> posts = new ArrayList<>();
            for (Post post : allPosts) {
                if (post.getCaption() != null && post.getCaption().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    posts.add(post);
                }
            }

            mainHandler.post(() -> {
                filteredUsers = users;
                filteredPosts = posts;

                userSearchAdapter.setUsers(filteredUsers);
                postSearchAdapter.setPosts(filteredPosts);

                searchLoadingProgress.setVisibility(View.GONE);
                tabLayout.setVisibility(View.VISIBLE);

                if (filteredUsers.isEmpty() && filteredPosts.isEmpty()) {
                    searchEmptyStateText.setText("No results for \"" + query + "\"");
                    searchEmptyStateText.setVisibility(View.VISIBLE);
                    usersRecyclerView.setVisibility(View.GONE);
                    postsRecyclerView.setVisibility(View.GONE);
                } else {
                    searchEmptyStateText.setVisibility(View.GONE);
                    if (!filteredUsers.isEmpty()) {
                        tabLayout.selectTab(tabLayout.getTabAt(0));
                        updateResultTabVisibility(0);
                    } else {
                        tabLayout.selectTab(tabLayout.getTabAt(1));
                        updateResultTabVisibility(1);
                    }
                }
            });
        });
    }

    private void updateResultTabVisibility(int tabPosition) {
        if (tabPosition == 0) {
            usersRecyclerView.setVisibility(View.VISIBLE);
            postsRecyclerView.setVisibility(View.GONE);
            searchEmptyStateText.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            usersRecyclerView.setVisibility(View.GONE);
            postsRecyclerView.setVisibility(View.VISIBLE);
            searchEmptyStateText.setVisibility(filteredPosts.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void setGlobalLoading(boolean isLoading) {
        if (globalLoadingProgress != null) {
            globalLoadingProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    public void onUserClicked(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.user_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
        androidx.fragment.app.Fragment fragment;

        if (userId.equals(currentUserId)) {
            // Thay vì replace bằng ProfileFragment mới, có thể chuyển sang tab Profile nếu cần, 
            // nhưng ở đây ta cứ replace để đồng bộ flow OtherProfile.
            fragment = new ProfileFragment();
        } else {
            fragment = OtherProfileFragment.newInstance(userId);
        }

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void openOtherProfile(User user) {
        if (user == null) return;
        onUserClicked(user.getId());
    }

    private boolean isAdminUser(@Nullable User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        return "ADMIN".equalsIgnoreCase(user.getRole().trim());
    }

    @Override
    public void onLikeClicked(Post post, int position) {
        homeViewModel.toggleLike(post);
    }

    @Override
    public void onCommentClicked(Post post) {
        BottomSheetCommentFragment bottomSheetCommentFragment = BottomSheetCommentFragment.newInstance(post.getId());
        bottomSheetCommentFragment.show(getParentFragmentManager(), "comments_bottom_sheet");
    }

    @Override
    public void onShareClicked(Post post) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, post.getCaption());
        startActivity(android.content.Intent.createChooser(shareIntent, "Share post"));
    }

    @Override
    public void onBookmarkClicked(Post post) {
        homeViewModel.toggleBookmark(post);
    }

    @Override
    public void onComposerPostClicked(String content) {}

    @Override
    public void onComposerClicked() {}

    @Override
    public void onComposerImageClicked() {}

    @Override
    public void onEditPostClicked(Post post) {
        NewPostFragment editFragment = NewPostFragment.newInstanceForEdit(post.getId());
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, editFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDeletePostClicked(Post post) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_post_title))
                .setMessage(getString(R.string.delete_post_confirm))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    newPostViewModel.deletePost(post.getId());
                })
                .setNegativeButton(getString(R.string.cancel_action), null)
                .show();
    }
    @Override
    public void onReportPostClicked(Post post) {
        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Vui lòng đăng nhập để báo cáo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserId.equals(post.getUserId())) {
            Toast.makeText(requireContext(), "Bạn không thể báo cáo bài viết của chính mình", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] reasons = {
                "Nội dung không phù hợp",
                "Spam",
                "Quấy rối",
                "Thông tin sai sự thật",
                "Lý do khác"
        };

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Báo cáo bài viết")
                .setItems(reasons, (dialog, which) -> {
                    String selectedReason = reasons[which];

                    // CHỈ khi chọn "Lý do khác" (index 4) mới hiện dialog nhập chi tiết
                    if (which == 4) {
                        showPostReportDetailDialog(post, selectedReason);
                    } else {
                        homeViewModel.reportPost(post, selectedReason);
                        Toast.makeText(requireContext(), "Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showPostReportDetailDialog(Post post, String baseReason) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Nhập chi tiết lý do");
        input.setPadding(40, 20, 40, 20);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Nhập chi tiết lý do báo cáo")
                .setView(input)
                .setPositiveButton("Gửi", (dialog, which) -> {
                    String detail = input.getText().toString().trim();
                    String finalReason = detail.isEmpty() ? baseReason : baseReason + ": " + detail;
                    homeViewModel.reportPost(post, finalReason);
                    Toast.makeText(requireContext(), "Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}