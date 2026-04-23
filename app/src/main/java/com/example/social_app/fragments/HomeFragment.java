package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.R;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.social_app.adapters.PostAdapter;
import com.example.social_app.data.model.Post;
import com.example.social_app.viewmodels.HomeViewModel;
import com.example.social_app.viewmodels.NewPostViewModel;

import java.util.List;

import androidx.lifecycle.ViewModelProvider;

/**
 * HomeFragment displays the social media feed with posts and post composer.
 * Implements infinite scroll functionality and manages feed state.
 */
public class HomeFragment extends Fragment implements PostAdapter.OnPostActionListener {

    private RecyclerView feedRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar loadingIndicator;
    private LinearLayout emptyState;
    private LinearLayout errorState;
    private PostAdapter postAdapter;
    private NewPostViewModel newPostViewModel;
    private HomeViewModel homeViewModel;

    private List<Post> posts;
    private boolean isLoading = false;
    private boolean hasMorePosts = true;
    private int currentPage = 0;

    private ScrollListenerCallback scrollListenerCallback;

    /**
     * Callback interface for scroll listener setup.
     */
    public interface ScrollListenerCallback {
        void setupScrollListener(RecyclerView recyclerView);
    }

    /**
     * Sets the scroll listener callback from MainActivity.
     */
    public void setBottomNavigationCallback(ScrollListenerCallback callback) {
        this.scrollListenerCallback = callback;
    }

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        setupObservers();

        // Initialize views
        feedRecyclerView = view.findViewById(R.id.feed_recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);

        // Set up RecyclerView
        setupRecyclerView();

        // Load initial posts
        loadPosts();

        // Set up retry button
        view.findViewById(R.id.retry_button).setOnClickListener(v -> {
            errorState.setVisibility(View.GONE);
            loadPosts();
        });

        // Set up SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(() -> {
            homeViewModel.loadPosts(true);
        });
    }

    private void setupObservers() {
        homeViewModel.getPosts().observe(getViewLifecycleOwner(), postsList -> {
            if (postsList != null) {
                this.posts = postsList;
                if (postsList.isEmpty()) {
                    showEmptyState();
                } else {
                    showFeed();
                    postAdapter.setEngagementData(homeViewModel.getLikedPostIds(), homeViewModel.getBookmarkedPostIds());
                    postAdapter.setPosts(postsList);
                }
            }
        });

        homeViewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            this.isLoading = loading;
            showLoading(loading);
        });

        homeViewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null) {
                showErrorState();
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });

        newPostViewModel.getPostSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success) {
                Toast.makeText(requireContext(), "Thao tác thành công!", Toast.LENGTH_SHORT).show();
                homeViewModel.loadPosts(true); // Reload feed từ Firestore
                newPostViewModel.resetPostSuccess();
            }
        });

        newPostViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Sets up the RecyclerView with layout manager and adapter.
     */
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        feedRecyclerView.setLayoutManager(layoutManager);

        postAdapter = new PostAdapter(requireContext(), this);
        feedRecyclerView.setAdapter(postAdapter);

        // Set up scroll listener callback for bottom navigation hide/show behavior
        if (scrollListenerCallback != null) {
            scrollListenerCallback.setupScrollListener(feedRecyclerView);
        }

        // Set up infinite scroll
        feedRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null) {
                    int visibleItemCount = manager.getChildCount();
                    int totalItemCount = manager.getItemCount();
                    int firstVisibleItemPosition = manager.findFirstVisibleItemPosition();

                    // Check if we should load more data
                    if (!isLoading && hasMorePosts &&
                            (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - 5)) {
                        loadMorePosts();
                    }
                }
            }
        });
    }

    /**
     * Loads the initial batch of posts.
     */
    private void loadPosts() {
        homeViewModel.loadPosts(true);
    }

    /**
     * Loads more posts for infinite scroll.
     */
    private void loadMorePosts() {
        homeViewModel.loadPosts(false);
    }

    /**
     * Shows the loading indicator.
     */
    private void showLoading(boolean show) {
        if (!show) {
            swipeRefreshLayout.setRefreshing(false);
        }
        loadingIndicator.setVisibility(show && !swipeRefreshLayout.isRefreshing() ? View.VISIBLE : View.GONE);
    }

    /**
     * Shows the feed with posts.
     */
    private void showFeed() {
        feedRecyclerView.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
    }

    /**
     * Shows the empty state message.
     */
    private void showEmptyState() {
        feedRecyclerView.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
    }

    /**
     * Shows the error state message.
     */
    private void showErrorState() {
        feedRecyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLikeClicked(Post post, int position) {
        homeViewModel.toggleLike(post);
    }

    @Override
    public void onCommentClicked(Post post) {
        // Prevent multiple bottom sheets from opening
        if (getParentFragmentManager().findFragmentByTag("comments_bottom_sheet") != null) {
            return;
        }
        
        // Open comments in a bottom sheet with swipe-to-dismiss gesture
        BottomSheetCommentFragment bottomSheetCommentFragment = BottomSheetCommentFragment.newInstance(post.getId());
        bottomSheetCommentFragment.show(getParentFragmentManager(), "comments_bottom_sheet");
    }

    @Override
    public void onShareClicked(Post post) {
        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = post.getCaption() != null ? post.getCaption() : "Xem bài viết thú vị này trên Social App!";
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Social App Post");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ bài viết qua"));
    }

    @Override
    public void onBookmarkClicked(Post post) {
        homeViewModel.toggleBookmark(post);
    }

    @Override
    public void onComposerPostClicked(String content) {
        if (content.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập nội dung", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Tạo bài viết nhanh (text-only) với privacy mặc định PUBLIC.
        // Signature: createPost(content, mediaUris, location, taggedPeople, privacyLevel)
        newPostViewModel.createPost(
                content,
                new java.util.ArrayList<>(),
                null,
                new java.util.ArrayList<>(),
                "PUBLIC"
        );
    }

    @Override
    public void onComposerClicked() {
        android.util.Log.d("HomeFragment", "Composer clicked - opening NewPostFragment");

        // Open NewPostFragment
        NewPostFragment newPostFragment = NewPostFragment.newInstance();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, newPostFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onComposerImageClicked() {
        NewPostFragment newPostFragment = NewPostFragment.newInstanceWithImage();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, newPostFragment)
                .addToBackStack(null)
                .commit();
    }

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
                .setTitle("Xóa bài viết")
                .setMessage("Bạn có chắc chắn muốn xóa bài viết này?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    newPostViewModel.deletePost(post.getId());
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}



