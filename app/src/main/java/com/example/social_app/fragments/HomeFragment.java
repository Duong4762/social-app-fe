package com.example.social_app.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.social_app.R;
import com.example.social_app.adapters.PostAdapter;
import com.example.social_app.data.model.Post;
import com.example.social_app.firebase.FirebaseManager;
import com.example.social_app.utils.UserAvatarLoader;
import com.example.social_app.viewmodels.HomeViewModel;
import com.example.social_app.viewmodels.NewPostViewModel;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class HomeFragment extends Fragment implements PostAdapter.OnPostActionListener {

    private RecyclerView feedRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar loadingIndicator;
    private LinearLayout emptyState;
    private LinearLayout errorState;
    private PostAdapter postAdapter;
    private NewPostViewModel newPostViewModel;
    private HomeViewModel homeViewModel;

    private boolean isLoading = false;
    private boolean hasMorePosts = true;

    private ScrollListenerCallback scrollListenerCallback;

    public interface ScrollListenerCallback {
        void setupScrollListener(RecyclerView recyclerView);
    }

    public void setBottomNavigationCallback(ScrollListenerCallback callback) {
        this.scrollListenerCallback = callback;
    }

    public HomeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        setupObservers();

        feedRecyclerView = view.findViewById(R.id.feed_recycler_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);

        setupRecyclerView();
        loadCurrentUserAvatar(view);
        loadPosts();

        view.findViewById(R.id.retry_button).setOnClickListener(v -> {
            errorState.setVisibility(View.GONE);
            loadPosts();
        });

        swipeRefreshLayout.setOnRefreshListener(() -> homeViewModel.loadPosts(true));
    }

    private void setupObservers() {
        homeViewModel.getPosts().observe(getViewLifecycleOwner(), postsList -> {
            if (postsList != null) {
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
            isLoading = loading;
            if (!loading) {
                swipeRefreshLayout.setRefreshing(false);
            }
            loadingIndicator.setVisibility(loading && !swipeRefreshLayout.isRefreshing() ? View.VISIBLE : View.GONE);
        });

        homeViewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null) {
                showErrorState();
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });

        newPostViewModel.getPostSuccess().observe(getViewLifecycleOwner(), success -> {
            if (success != null && success) {
                Toast.makeText(requireContext(), "Thao tác thành công!", Toast.LENGTH_SHORT).show();
                homeViewModel.loadPosts(true);
                newPostViewModel.resetPostSuccess();
            }
        });

        newPostViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        feedRecyclerView.setLayoutManager(layoutManager);

        postAdapter = new PostAdapter(requireContext(), this);
        feedRecyclerView.setAdapter(postAdapter);

        if (scrollListenerCallback != null) {
            scrollListenerCallback.setupScrollListener(feedRecyclerView);
        }

        feedRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null) {
                    int visibleItemCount = manager.getChildCount();
                    int totalItemCount = manager.getItemCount();
                    int firstVisibleItemPosition = manager.findFirstVisibleItemPosition();

                    if (!isLoading && hasMorePosts &&
                            (visibleItemCount + firstVisibleItemPosition) >= (totalItemCount - 5)) {
                        loadMorePosts();
                    }
                }
            }
        });
    }

    private void loadCurrentUserAvatar(View view) {
        ShapeableImageView ivUserAvatar = view.findViewById(R.id.iv_user_avatar);
        String uid = FirebaseManager.getInstance().getAuth().getUid();

        if (ivUserAvatar != null) {
            ivUserAvatar.setImageResource(R.drawable.avatar_placeholder);
        }

        if (uid != null) {
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_USERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (isAdded() && documentSnapshot.exists()) {
                            String avatarUrl = documentSnapshot.getString("avatarUrl");
                            if (ivUserAvatar != null) {
                                UserAvatarLoader.load(ivUserAvatar, avatarUrl);
                            }
                            if (postAdapter != null) {
                                postAdapter.setCurrentUserAvatarUrl(avatarUrl);
                            }
                        }
                    });
        }
    }

    private void loadPosts() {
        homeViewModel.loadPosts(true);
    }

    private void loadMorePosts() {
        homeViewModel.loadPosts(false);
    }

    private void showFeed() {
        feedRecyclerView.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        feedRecyclerView.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.VISIBLE);
        errorState.setVisibility(View.GONE);
    }

    private void showErrorState() {
        feedRecyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
    }

    // ==================== PostAdapter.OnPostActionListener ====================

    @Override
    public void onUserClicked(String userId) {
        if (userId == null || userId.isEmpty()) return;
        String currentUserId = FirebaseManager.getInstance().getAuth().getUid();

        if (userId.equals(currentUserId)) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        } else {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, OtherProfileFragment.newInstance(userId))
                    .addToBackStack(null)
                    .commit();
        }
    }

    @Override
    public void onLikeClicked(Post post, int position) {
        homeViewModel.toggleLike(post);
    }

    @Override
    public void onCommentClicked(Post post) {
        if (getParentFragmentManager().findFragmentByTag("comments_bottom_sheet") != null) {
            return;
        }
        BottomSheetCommentFragment bottomSheet = BottomSheetCommentFragment.newInstance(post.getId());
        bottomSheet.show(getParentFragmentManager(), "comments_bottom_sheet");
    }

    @Override
    public void onShareClicked(Post post) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = post.getCaption() != null ? post.getCaption() : "Check out this post on Social App!";
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(shareIntent, "Share post"));
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
        newPostViewModel.createPost(content, new java.util.ArrayList<>(), null, new java.util.ArrayList<>(), "PUBLIC");
    }

    @Override
    public void onComposerClicked() {
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
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa bài viết")
                .setMessage("Bạn có chắc chắn muốn xóa bài viết này?")
                .setPositiveButton("Xóa", (dialog, which) -> newPostViewModel.deletePost(post.getId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ==================== REPORT BÀI VIẾT ====================

    @Override
    public void onReportPostClicked(Post post) {
        showReportDialog(post);
    }

    private void showReportDialog(Post post) {
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

        new AlertDialog.Builder(requireContext())
                .setTitle("Báo cáo bài viết")
                .setItems(reasons, (dialog, which) -> {
                    String selectedReason = reasons[which];

                    // CHỈ khi chọn "Lý do khác" (index 4) mới hiện dialog nhập chi tiết
                    if (which == 4) {
                        showDetailInputDialog(post, selectedReason);
                    } else {
                        homeViewModel.reportPost(post, selectedReason);
                        Toast.makeText(requireContext(), "Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showDetailInputDialog(Post post, String baseReason) {
        EditText input = new EditText(requireContext());
        input.setHint("Nhập chi tiết lý do");
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(requireContext())
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