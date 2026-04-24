package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.social_app.R;
import com.example.social_app.firebase.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminStatsFragment extends Fragment {
    private TextView tvTotalUsers;
    private TextView tvGrowthUsers;
    private TextView tvBannedUsers;
    private TextView tvPostsInPeriod;
    private TextView tvActiveUsers;
    private TextView tvTotalPosts;
    private TextView tvPostsLabel;
    private TextView tvChartSubtitle;
    private LinearLayout chartBarsContainer;
    private MaterialButton chip7Days;
    private MaterialButton chip30Days;
    private MaterialButton chip90Days;
    private int selectedDays = 7;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvTotalUsers = view.findViewById(R.id.tvTotalUsers);
        tvGrowthUsers = view.findViewById(R.id.tvGrowthUsers);
        tvBannedUsers = view.findViewById(R.id.tvBannedUsers);
        tvPostsInPeriod = view.findViewById(R.id.tvPostsInPeriod);
        tvActiveUsers = view.findViewById(R.id.tvActiveUsers);
        tvTotalPosts = view.findViewById(R.id.tvTotalPosts);
        tvPostsLabel = view.findViewById(R.id.tvPostsLabel);
        tvChartSubtitle = view.findViewById(R.id.tvChartSubtitle);
        chartBarsContainer = view.findViewById(R.id.chartBarsContainer);
        chip7Days = view.findViewById(R.id.chip7Days);
        chip30Days = view.findViewById(R.id.chip30Days);
        chip90Days = view.findViewById(R.id.chip90Days);
        setupFilters();
        loadStatsForPeriod();
    }

    private void setupFilters() {
        chip7Days.setOnClickListener(v -> {
            selectedDays = 7;
            updateFilterUI();
            loadStatsForPeriod();
        });
        chip30Days.setOnClickListener(v -> {
            selectedDays = 30;
            updateFilterUI();
            loadStatsForPeriod();
        });
        chip90Days.setOnClickListener(v -> {
            selectedDays = 90;
            updateFilterUI();
            loadStatsForPeriod();
        });
        updateFilterUI();
    }

    private void updateFilterUI() {
        styleChip(chip7Days, selectedDays == 7);
        styleChip(chip30Days, selectedDays == 30);
        styleChip(chip90Days, selectedDays == 90);
    }

    private void styleChip(MaterialButton button, boolean active) {
        int bg = active ? requireContext().getColor(R.color.primary_purple) : resolveThemeColor(com.google.android.material.R.attr.colorSurface);
        int text = active ? requireContext().getColor(R.color.white) : resolveThemeColor(com.google.android.material.R.attr.colorOnSurface);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
        button.setTextColor(text);
        button.setStrokeWidth(active ? 0 : 1);
    }

    private int resolveThemeColor(int attrResId) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private void loadStatsForPeriod() {
        loadTotalUsersAndGrowth(selectedDays);
        loadBannedUsers();
        loadPostsInRange(selectedDays);
        loadGrowthChart(selectedDays);
        loadAdditionalStats();
    }

    private void loadTotalUsersAndGrowth(int days) {
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(allUsersQuery -> {
                    int totalUsers = allUsersQuery.size();
                    tvTotalUsers.setText(formatNumber(totalUsers));
                    calculateGrowthRate(days);
                })
                .addOnFailureListener(e -> {
                    tvTotalUsers.setText("0");
                    tvGrowthUsers.setText("0%");
                });
    }

    private void calculateGrowthRate(int days) {
        Calendar now = Calendar.getInstance();
        Date end = now.getTime();

        Calendar startCurrent = (Calendar) now.clone();
        startCurrent.add(Calendar.DAY_OF_YEAR, -days);

        Calendar startPrevious = (Calendar) now.clone();
        startPrevious.add(Calendar.DAY_OF_YEAR, -(days * 2));

        Date currentStartDate = startCurrent.getTime();
        Date previousStartDate = startPrevious.getTime();

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .whereGreaterThanOrEqualTo("createdAt", new Timestamp(currentStartDate))
                .whereLessThan("createdAt", new Timestamp(end))
                .get()
                .addOnSuccessListener(currentQuery -> {
                    int current = currentQuery.size();
                    FirebaseManager.getInstance().getFirestore()
                            .collection(FirebaseManager.COLLECTION_USERS)
                            .whereGreaterThanOrEqualTo("createdAt", new Timestamp(previousStartDate))
                            .whereLessThan("createdAt", new Timestamp(currentStartDate))
                            .get()
                            .addOnSuccessListener(previousQuery -> {
                                int previous = previousQuery.size();
                                tvGrowthUsers.setText(buildGrowthText(current, previous));
                            })
                            .addOnFailureListener(e -> tvGrowthUsers.setText("0%"));
                })
                .addOnFailureListener(e -> tvGrowthUsers.setText("0%"));
    }

    private void loadBannedUsers() {
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .whereEqualTo("isBanned", true)
                .get()
                .addOnSuccessListener(query -> tvBannedUsers.setText(formatNumber(query.size())))
                .addOnFailureListener(e -> tvBannedUsers.setText("0"));
    }

    private void loadPostsInRange(int days) {
        Calendar startCal = Calendar.getInstance();
        startCal.add(Calendar.DAY_OF_YEAR, -days);

        Calendar endCal = Calendar.getInstance();

        Timestamp dayStart = new Timestamp(startCal.getTime());
        Timestamp dayEnd = new Timestamp(endCal.getTime());
        tvPostsLabel.setText("Posts (" + days + " ngày)");

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_POSTS)
                .whereGreaterThanOrEqualTo("createdAt", dayStart)
                .whereLessThan("createdAt", dayEnd)
                .get()
                .addOnSuccessListener(query -> tvPostsInPeriod.setText(formatNumber(query.size())))
                .addOnFailureListener(e -> tvPostsInPeriod.setText("0"));
    }

    private void loadAdditionalStats() {
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(query -> tvActiveUsers.setText(formatNumber(query.size())))
                .addOnFailureListener(e -> tvActiveUsers.setText("0"));

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_POSTS)
                .get()
                .addOnSuccessListener(query -> tvTotalPosts.setText(formatNumber(query.size())))
                .addOnFailureListener(e -> tvTotalPosts.setText("0"));
    }

    private void loadGrowthChart(int days) {
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_YEAR, -days);
        Timestamp startTs = new Timestamp(start.getTime());
        Timestamp endTs = new Timestamp(new Date());
        tvChartSubtitle.setText("Số user tạo mới trong " + days + " ngày gần đây");

        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .whereGreaterThanOrEqualTo("createdAt", startTs)
                .whereLessThanOrEqualTo("createdAt", endTs)
                .get()
                .addOnSuccessListener(query -> renderChart(days, query.getDocuments()))
                .addOnFailureListener(e -> renderChart(days, new ArrayList<>()));
    }

    private void renderChart(int days, List<DocumentSnapshot> docs) {
        List<Bucket> buckets = buildBuckets(days);
        for (DocumentSnapshot doc : docs) {
            Timestamp createdAt = doc.getTimestamp("createdAt");
            if (createdAt == null) {
                continue;
            }
            Date createdDate = createdAt.toDate();
            for (Bucket bucket : buckets) {
                if (!createdDate.before(bucket.start) && createdDate.before(bucket.end)) {
                    bucket.count++;
                    break;
                }
            }
        }

        int max = 0;
        for (Bucket bucket : buckets) {
            if (bucket.count > max) {
                max = bucket.count;
            }
        }
        chartBarsContainer.removeAllViews();
        for (Bucket bucket : buckets) {
            chartBarsContainer.addView(createBarRow(bucket, max));
        }
    }

    private List<Bucket> buildBuckets(int days) {
        int bucketSize = days == 7 ? 1 : (days == 30 ? 5 : 10);
        List<Bucket> buckets = new ArrayList<>();
        Calendar cursor = Calendar.getInstance();
        cursor.add(Calendar.DAY_OF_YEAR, -days);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM", Locale.getDefault());

        while (true) {
            Date start = cursor.getTime();
            Calendar endCal = (Calendar) cursor.clone();
            endCal.add(Calendar.DAY_OF_YEAR, bucketSize);
            Date end = endCal.getTime();
            String label = fmt.format(start);
            buckets.add(new Bucket(start, end, label));
            cursor = endCal;
            if (!cursor.before(Calendar.getInstance())) {
                break;
            }
        }
        return buckets;
    }

    private View createBarRow(Bucket bucket, int max) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 4, 0, 4);

        TextView label = new TextView(requireContext());
        label.setText(bucket.label);
        label.setTextSize(12f);
        label.setTextColor(requireContext().getColor(R.color.muted));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT);
        label.setLayoutParams(labelParams);
        row.addView(label);

        View bar = new View(requireContext());
        int full = dp(180);
        int width = max <= 0 ? dp(4) : Math.max(dp(4), (int) ((bucket.count / (float) max) * full));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(width, dp(8));
        barParams.setMarginEnd(dp(8));
        bar.setLayoutParams(barParams);
        bar.setBackgroundColor(requireContext().getColor(R.color.primary_purple));
        row.addView(bar);

        TextView value = new TextView(requireContext());
        value.setText(String.valueOf(bucket.count));
        value.setTextSize(12f);
        value.setTextColor(requireContext().getColor(R.color.text));
        row.addView(value);
        return row;
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String buildGrowthText(int current, int previous) {
        if (previous <= 0) {
            if (current <= 0) {
                return "0%";
            }
            return "+100%";
        }
        double rate = ((double) (current - previous) / (double) previous) * 100.0;
        return String.format(Locale.getDefault(), "%+.1f%%", rate);
    }

    private String formatNumber(int number) {
        return String.format(Locale.getDefault(), "%,d", Math.max(number, 0));
    }

    private static class Bucket {
        final Date start;
        final Date end;
        final String label;
        int count;

        Bucket(Date start, Date end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
            this.count = 0;
        }
    }
}
