package com.example.social_app.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.Story;
import com.example.social_app.data.model.User;
import com.example.social_app.viewmodels.StoryViewModel;

public class StoryDetailFragment extends DialogFragment {


    private static final String ARG_STORY = "story";
    private static final String ARG_USER = "user";

    private ImageView imageView;
    private VideoView videoView;
    private ProgressBar progressBar;
    private ImageView userAvatar;
    private TextView username;
    private View content;
    private ImageView btnClose; // 🔥 nút X

    private Story story;
    private User user;
    private StoryViewModel storyViewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable dismissRunnable;
    private Runnable progressRunnable;

    public static StoryDetailFragment newInstance(Story story, User user) {
        StoryDetailFragment fragment = new StoryDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_STORY, story);
        args.putSerializable(ARG_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            story = (Story) getArguments().getSerializable(ARG_STORY);
            user = (User) getArguments().getSerializable(ARG_USER);
        }
        storyViewModel = new StoryViewModel();
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_story_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imageView = view.findViewById(R.id.story_image);
        videoView = view.findViewById(R.id.story_video);
        progressBar = view.findViewById(R.id.story_progress);
        userAvatar = view.findViewById(R.id.story_user_avatar);
        username = view.findViewById(R.id.story_username);
        content = view.findViewById(R.id.story_content);
        btnClose = view.findViewById(R.id.btn_close); // 🔥 init nút X

        // 🔥 xử lý click nút X
        btnClose.setOnClickListener(v -> dismiss());

        username.setText(user != null ? user.getFullName() : "User");
        Glide.with(this)
                .load(user != null ? user.getAvatarUrl() : null)
                .placeholder(R.drawable.avatar_placeholder)
                .error(R.drawable.avatar_placeholder)
                .circleCrop()
                .into(userAvatar);

        if (story != null && story.getId() != null) {
            storyViewModel.incrementViewCount(story.getId());
        }

        if (story != null && "IMAGE".equals(story.getMediaType())) {
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);

            Glide.with(this)
                    .load(story.getMediaUrl())
                    .into(imageView);

            startAutoDismiss(15000);
            startImageProgress(15000);

        } else if (story != null && "VIDEO".equals(story.getMediaType())) {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);

            videoView.setVideoURI(Uri.parse(story.getMediaUrl()));
            videoView.start();

            videoView.setOnPreparedListener(mp -> {
                int duration = mp.getDuration();
                startAutoDismiss(duration);
                startVideoProgress(duration);
            });

            videoView.setOnCompletionListener(mp -> dismiss());
        }

        content.setOnClickListener(v -> dismiss());
    }

    private void startAutoDismiss(int delayMillis) {
        dismissRunnable = () -> {
            if (isAdded()) {
                dismiss();
            }
        };
        handler.postDelayed(dismissRunnable, delayMillis);
    }

    private void startImageProgress(int durationMillis) {
        if (durationMillis <= 0) return;
        stopProgressUpdates();

        final long startedAt = SystemClock.uptimeMillis();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                long elapsed = SystemClock.uptimeMillis() - startedAt;
                int progress = (int) Math.min(1000L, (elapsed * 1000L) / durationMillis);
                progressBar.setProgress(progress);
                if (progress < 1000) {
                    handler.postDelayed(this, 50);
                }
            }
        };
        handler.post(progressRunnable);
    }

    private void startVideoProgress(int durationMillis) {
        if (durationMillis <= 0) return;
        stopProgressUpdates();

        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || videoView == null) return;
                int current = videoView.getCurrentPosition();
                int progress = (int) Math.min(1000L, (current * 1000L) / durationMillis);
                progressBar.setProgress(progress);
                if (current < durationMillis) {
                    handler.postDelayed(this, 50);
                }
            }
        };
        handler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dismissRunnable != null) {
            handler.removeCallbacks(dismissRunnable);
            dismissRunnable = null;
        }
        stopProgressUpdates();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }


}
