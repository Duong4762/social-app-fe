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
import com.example.social_app.adapters.PostAdapter;
import com.example.social_app.models.Post;
import com.example.social_app.utils.MockDataGenerator;

import java.util.List;

/**
 * HomeFragment displays the social media feed with posts and post composer.
 * Implements infinite scroll functionality and manages feed state.
 */
public class HomeFragment extends Fragment implements PostAdapter.OnPostActionListener {

    private RecyclerView feedRecyclerView;
    private ProgressBar loadingIndicator;
    private LinearLayout emptyState;
    private LinearLayout errorState;
    private PostAdapter postAdapter;

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

        // Initialize views
        feedRecyclerView = view.findViewById(R.id.feed_recycler_view);
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
        showLoading(true);
        isLoading = true;
        currentPage = 0;

        // Simulate network delay with a handler
        new android.os.Handler().postDelayed(() -> {
            try {
                // Generate mock posts
                posts = MockDataGenerator.generateMockPosts(10);

                if (posts.isEmpty()) {
                    showEmptyState();
                } else {
                    showFeed();
                    postAdapter.setPosts(posts);
                }
            } catch (Exception e) {
                showErrorState();
            } finally {
                showLoading(false);
                isLoading = false;
            }
        }, 500);
    }

    /**
     * Loads more posts for infinite scroll.
     */
    private void loadMorePosts() {
        if (isLoading || !hasMorePosts) return;

        isLoading = true;
        currentPage++;

        // Simulate network delay
        new android.os.Handler().postDelayed(() -> {
            try {
                // Generate more mock posts
                List<Post> newPosts = MockDataGenerator.generateMockPosts(5);

                if (newPosts.isEmpty()) {
                    hasMorePosts = false;
                } else {
                    postAdapter.addPosts(newPosts);
                    posts.addAll(newPosts);
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to load more posts", Toast.LENGTH_SHORT).show();
            } finally {
                isLoading = false;
            }
        }, 500);
    }

    /**
     * Shows the loading indicator.
     */
    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
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
        feedRecyclerView.setVisibility(View.GONE);
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
        post.toggleLike();
        postAdapter.notifyItemChanged(position + 1); // +1 because of composer at position 0
        Toast.makeText(requireContext(), "Post " + (post.isLiked() ? "liked" : "unliked"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCommentClicked(Post post) {
        // Open comments in a bottom sheet with swipe-to-dismiss gesture
        BottomSheetCommentFragment bottomSheetCommentFragment = BottomSheetCommentFragment.newInstance(post.getId());
        bottomSheetCommentFragment.show(requireActivity().getSupportFragmentManager(), "comments_bottom_sheet");
    }

    @Override
    public void onShareClicked(Post post) {
        Toast.makeText(requireContext(), "Share post: " + post.getId(), Toast.LENGTH_SHORT).show();
        // TODO: Implement share functionality
    }

    @Override
    public void onBookmarkClicked(Post post) {
        Toast.makeText(requireContext(), "Bookmark post: " + post.getId(), Toast.LENGTH_SHORT).show();
        // TODO: Implement bookmark functionality
    }

    @Override
    public void onComposerPostClicked(String content) {
        Toast.makeText(requireContext(), "Post created: " + content, Toast.LENGTH_SHORT).show();
        // TODO: Implement post creation
    }

    @Override
    public void onComposerClicked() {
        android.util.Log.d("HomeFragment", "Composer clicked - opening NewPostFragment");

        // Open NewPostFragment
        NewPostFragment newPostFragment = new NewPostFragment();
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, newPostFragment)
                .addToBackStack(null)
                .commit();

        android.util.Log.d("HomeFragment", "NewPostFragment opened from composer");
    }
}



