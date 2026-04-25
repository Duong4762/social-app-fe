package com.example.social_app.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.social_app.R;
import com.example.social_app.data.model.Story;
import com.example.social_app.data.model.User;
import com.example.social_app.viewmodels.StoryViewModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StoryDetailFragment extends DialogFragment {

    private static final String ARG_STORIES = "stories";
    private static final String ARG_USER = "user";
    private static final String ARG_START_INDEX = "start_index";

    /** Thời lượng hiển thị mỗi story ảnh (cố định). */
    private static final int IMAGE_STORY_DURATION_MS = 15_000;
    private static final int PROGRESS_MAX = 1000;

    private ImageView imageView;
    private VideoView videoView;
    private LinearLayout segmentContainer;
    private ImageView userAvatar;
    private TextView username;
    private TextView storyPostedAgo;
    private View content;
    private ImageView btnClose;

    private ArrayList<Story> stories;
    private User user;
    private int startIndex;
    private int currentIndex;
    private final List<ProgressBar> segmentBars = new ArrayList<>();

    private StoryViewModel storyViewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    private float storyTouchDownX;
    private float storyTouchDownY;

    @NonNull
    public static StoryDetailFragment newInstance(
            @NonNull ArrayList<Story> stories,
            @NonNull User user,
            int startIndex
    ) {
        StoryDetailFragment fragment = new StoryDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_STORIES, stories);
        args.putSerializable(ARG_USER, user);
        args.putInt(ARG_START_INDEX, startIndex);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            @SuppressWarnings("unchecked")
            ArrayList<Story> list = (ArrayList<Story>) args.getSerializable(ARG_STORIES);
            stories = list != null ? list : new ArrayList<>();
            user = (User) args.getSerializable(ARG_USER);
            startIndex = args.getInt(ARG_START_INDEX, 0);
        } else {
            stories = new ArrayList<>();
            startIndex = 0;
        }
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

        storyViewModel = new ViewModelProvider(requireActivity()).get(StoryViewModel.class);

        imageView = view.findViewById(R.id.story_image);
        videoView = view.findViewById(R.id.story_video);
        segmentContainer = view.findViewById(R.id.story_progress_segments);
        userAvatar = view.findViewById(R.id.story_user_avatar);
        username = view.findViewById(R.id.story_username);
        storyPostedAgo = view.findViewById(R.id.story_posted_ago);
        content = view.findViewById(R.id.story_content);
        btnClose = view.findViewById(R.id.btn_close);

        btnClose.setOnClickListener(v -> dismiss());

        Glide.with(this)
                .load(user != null ? user.getAvatarUrl() : null)
                .placeholder(R.drawable.avatar_placeholder)
                .error(R.drawable.avatar_placeholder)
                .circleCrop()
                .into(userAvatar);

        buildSegmentBars();
        final float swipeDownDismissPx = 80f * getResources().getDisplayMetrics().density;
        content.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                storyTouchDownX = e.getX();
                storyTouchDownY = e.getY();
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                float dx = e.getX() - storyTouchDownX;
                float dy = e.getY() - storyTouchDownY;
                if (dy > swipeDownDismissPx && dy > Math.abs(dx) * 0.65f) {
                    dismiss();
                    return true;
                }
                float w = v.getWidth();
                if (w > 0f) {
                    if (e.getX() < w / 2f) {
                        goToPreviousStory();
                    } else {
                        advanceToNextStory();
                    }
                }
                return true;
            }
            return true;
        });

        if (stories == null || stories.isEmpty()) {
            dismiss();
            return;
        }
        currentIndex = Math.max(0, Math.min(startIndex, stories.size() - 1));
        showStoryAt(currentIndex);
    }

    private void buildSegmentBars() {
        segmentContainer.removeAllViews();
        segmentBars.clear();
        if (stories == null || stories.isEmpty()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int barHeight = Math.round(4f * density);
        int gap = Math.round(3f * density);
        int n = stories.size();
        for (int i = 0; i < n; i++) {
            ProgressBar pb = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(PROGRESS_MAX);
            pb.setProgress(0);
            if (ContextCompat.getDrawable(requireContext(), R.drawable.story_progress_horizontal) != null) {
                pb.setProgressDrawable(
                        ContextCompat.getDrawable(requireContext(), R.drawable.story_progress_horizontal));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, barHeight, 1f);
            if (i < n - 1) {
                lp.setMarginEnd(gap);
            }
            segmentContainer.addView(pb, lp);
            segmentBars.add(pb);
        }
    }

    private void bindStoryHeader(@NonNull Story story) {
        if (username != null) {
            if (user != null) {
                String label = user.getFullName() != null ? user.getFullName() : user.getUsername();
                username.setText(label != null ? label : "User");
            } else {
                username.setText("User");
            }
        }
        if (storyPostedAgo != null) {
            storyPostedAgo.setText(formatStoryRelativeTime(story.getCreatedAt()));
        }
    }

    @NonNull
    private String formatStoryRelativeTime(@Nullable Date createdAt) {
        if (createdAt == null) {
            return "";
        }
        if (!isAdded()) {
            return "";
        }
        long diffMs = System.currentTimeMillis() - createdAt.getTime();
        if (diffMs < 0L) {
            diffMs = 0L;
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        if (minutes < 1L) {
            return getString(R.string.just_now);
        }
        if (minutes < 60L) {
            return getString(R.string.minutes_ago, (int) minutes);
        }
        long hours = TimeUnit.MILLISECONDS.toHours(diffMs);
        if (hours < 24L) {
            return getString(R.string.hours_ago, (int) hours);
        }
        int days = (int) TimeUnit.MILLISECONDS.toDays(diffMs);
        return getString(R.string.days_ago, Math.max(1, days));
    }

    private void syncSegmentProgressUi() {
        for (int i = 0; i < segmentBars.size(); i++) {
            if (i < currentIndex) {
                segmentBars.get(i).setProgress(PROGRESS_MAX);
            } else if (i > currentIndex) {
                segmentBars.get(i).setProgress(0);
            } else {
                segmentBars.get(i).setProgress(0);
            }
        }
    }

    private void showStoryAt(int index) {
        if (!isAdded() || stories == null || index < 0 || index >= stories.size()) {
            dismiss();
            return;
        }
        currentIndex = index;
        stopProgressUpdates();
        stopVideoPlayback();
        syncSegmentProgressUi();

        Story story = stories.get(index);
        bindStoryHeader(story);
        if (story.getId() != null) {
            storyViewModel.incrementViewCount(story.getId());
        }

        if ("IMAGE".equals(story.getMediaType())) {
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            Glide.with(this).load(story.getMediaUrl()).into(imageView);
            startSegmentProgress(IMAGE_STORY_DURATION_MS);
        } else if ("VIDEO".equals(story.getMediaType())) {
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            videoView.setOnPreparedListener(null);
            videoView.setOnCompletionListener(null);
            videoView.setVideoURI(Uri.parse(story.getMediaUrl()));
            videoView.setOnPreparedListener(mp -> {
                if (!isAdded()) {
                    return;
                }
                int duration = mp.getDuration();
                if (duration <= 0) {
                    duration = 10000;
                }
                startSegmentProgressForVideo(duration);
            });
            videoView.setOnCompletionListener(mp -> {
                if (currentIndex >= 0 && currentIndex < segmentBars.size()) {
                    segmentBars.get(currentIndex).setProgress(PROGRESS_MAX);
                }
                advanceToNextStory();
            });
            videoView.start();
        } else {
            advanceToNextStory();
        }
    }

    private void startSegmentProgress(int durationMillis) {
        if (durationMillis <= 0 || currentIndex < 0 || currentIndex >= segmentBars.size()) {
            advanceToNextStory();
            return;
        }
        stopProgressUpdates();
        ProgressBar activeBar = segmentBars.get(currentIndex);
        final long startedAt = SystemClock.uptimeMillis();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) {
                    return;
                }
                long elapsed = SystemClock.uptimeMillis() - startedAt;
                int progress = (int) Math.min(PROGRESS_MAX, (elapsed * (long) PROGRESS_MAX) / durationMillis);
                activeBar.setProgress(progress);
                if (progress < PROGRESS_MAX) {
                    handler.postDelayed(this, 50);
                } else {
                    advanceToNextStory();
                }
            }
        };
        handler.post(progressRunnable);
    }

    /** Video: cập nhật thanh segment theo thời lượng thật. */
    private void startSegmentProgressForVideo(int durationMillis) {
        if (durationMillis <= 0 || currentIndex < 0 || currentIndex >= segmentBars.size()) {
            advanceToNextStory();
            return;
        }
        stopProgressUpdates();
        ProgressBar activeBar = segmentBars.get(currentIndex);
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || videoView == null) {
                    return;
                }
                int current = videoView.getCurrentPosition();
                int progress = (int) Math.min(PROGRESS_MAX, (current * (long) PROGRESS_MAX) / durationMillis);
                activeBar.setProgress(progress);
                if (videoView.isPlaying() && current < durationMillis - 1) {
                    handler.postDelayed(this, 50);
                }
            }
        };
        handler.post(progressRunnable);
    }

    /** Nửa trái màn hình: story trước; ở story đầu thì đóng popup. */
    private void goToPreviousStory() {
        stopProgressUpdates();
        if (stories == null || stories.isEmpty()) {
            dismiss();
            return;
        }
        if (currentIndex <= 0) {
            dismiss();
            return;
        }
        showStoryAt(currentIndex - 1);
    }

    /** Nửa phải: story tiếp; ở story cuối thì đóng popup. */
    private void advanceToNextStory() {
        stopProgressUpdates();
        if (stories == null) {
            dismiss();
            return;
        }
        if (currentIndex + 1 >= stories.size()) {
            dismiss();
            return;
        }
        showStoryAt(currentIndex + 1);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private void stopVideoPlayback() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopProgressUpdates();
        stopVideoPlayback();
        if (getActivity() != null) {
            new ViewModelProvider(requireActivity()).get(StoryViewModel.class).loadStories();
        }
    }
}
