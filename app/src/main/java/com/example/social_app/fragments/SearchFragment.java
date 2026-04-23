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
import com.example.social_app.adapters.PostSearchAdapter;
import com.example.social_app.adapters.UserSearchAdapter;
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.MockDataGenerator;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment {

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
    private PostSearchAdapter postSearchAdapter;

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
        tabLayout.addTab(tabLayout.newTab().setText("People"));
        tabLayout.addTab(tabLayout.newTab().setText("Posts"));
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
                Toast.makeText(requireContext(), "Follow: " + user.getFullName(), Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), "Follow: " + user.getFullName(), Toast.LENGTH_SHORT).show();
            }
        });
        usersRecyclerView.setAdapter(userSearchAdapter);

        // Search posts adapter
        postsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        postSearchAdapter = new PostSearchAdapter(requireContext(), post -> {
            Toast.makeText(requireContext(), "View post", Toast.LENGTH_SHORT).show();
        });
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
        executor.execute(() -> allPosts = MockDataGenerator.generateMockPosts(50));
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

    private void openOtherProfile(User user) {
        if (user == null || user.getId() == null || user.getId().trim().isEmpty()) {
            Toast.makeText(requireContext(), "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, OtherProfileFragment.newInstance(user.getId()))
                .addToBackStack(null)
                .commit();
    }

    private boolean isAdminUser(@Nullable User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        return "ADMIN".equalsIgnoreCase(user.getRole().trim());
    }
}