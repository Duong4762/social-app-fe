package com.example.social_app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.social_app.adapters.AdminReportAdapter;
import com.example.social_app.R;
import com.example.social_app.adapters.AdminReportedUserAdapter;
import com.example.social_app.data.model.Post;
import com.example.social_app.data.model.Report;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminDashboardFragment extends Fragment {
    public interface DashboardListener {
        void onReportCountChanged(int count);
    }

    private RecyclerView rvReportedUsers;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextView tvPendingReportsValue;
    private MaterialButton tabNewReports;
    private MaterialButton tabProcessed;
    private MaterialButton tabBannedUsers;
    private AdminReportedUserAdapter bannedUserAdapter;
    private AdminReportAdapter reportAdapter;
    private DashboardListener dashboardListener;
    private int selectedTab = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (requireActivity() instanceof DashboardListener) {
            dashboardListener = (DashboardListener) requireActivity();
        }
        rvReportedUsers = view.findViewById(R.id.rvReportedUsers);
        progressBar = view.findViewById(R.id.progressBar);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvPendingReportsValue = view.findViewById(R.id.tvPendingReportsValue);
        tabNewReports = view.findViewById(R.id.tabNewReports);
        tabProcessed = view.findViewById(R.id.tabProcessed);
        tabBannedUsers = view.findViewById(R.id.tabBannedUsers);

        bannedUserAdapter = new AdminReportedUserAdapter((user, reportCount) -> {
            if (user == null || user.getId() == null || user.getId().isEmpty()) {
                return;
            }
            if (user.isBanned()) {
                toggleBanUser(user, false);
            } else {
                showConfirmBanDialog(user);
            }
        });
        reportAdapter = new AdminReportAdapter(new AdminReportAdapter.Listener() {
            @Override
            public void onBanTargetUser(AdminReportAdapter.Item item) {
                if (item == null || item.targetUserId == null || item.targetUserId.isEmpty()) {
                    return;
                }
                String username = item.targetUsername == null ? "user" : item.targetUsername;
                showConfirmBanDialogByTarget(item.targetUserId, username);
            }

            @Override
            public void onHandlePostReport(AdminReportAdapter.Item item) {
                if (item != null) {
                    showPostReportActionDialog(item);
                }
            }
        });
        rvReportedUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReportedUsers.setAdapter(reportAdapter);
        setupTabs();
        refreshPendingReportsCount();
        loadCurrentTabData();
    }

    private void showConfirmBanDialog(User user) {
        if (!isAdded()) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận ban user")
                .setMessage("Bạn có chắc muốn ban user @" + user.getUsername() + " không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Ban", (dialog, which) -> toggleBanUser(user, true))
                .show();
    }

    private void showConfirmBanDialogByTarget(@NonNull String userId, @NonNull String username) {
        if (!isAdded()) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận ban user")
                .setMessage("Bạn có chắc muốn ban user @" + username + " không?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Ban", (dialog, which) -> toggleBanUserById(userId))
                .show();
    }

    private void toggleBanUser(@NonNull User user, boolean ban) {
        setLoading(true);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(user.getId())
                .update("isBanned", ban)
                .addOnSuccessListener(unused -> {
                    if (ban) {
                        markUserReportsAsProcessed(user.getId());
                    } else {
                        setLoading(false);
                        showToast("Đã mở khóa user");
                        loadCurrentTabData();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showToast("Không thể cập nhật trạng thái ban");
                });
    }

    private void toggleBanUserById(@NonNull String userId) {
        setLoading(true);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(userId)
                .update("isBanned", true)
                .addOnSuccessListener(unused -> markUserReportsAsProcessed(userId))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showToast("Không thể cập nhật trạng thái ban");
                });
    }

    private void markUserReportsAsProcessed(String targetUserId) {
        FirebaseManager.getInstance().getFirestore()
                .collection("reports")
                .whereEqualTo("type", "USER")
                .whereEqualTo("targetId", targetUserId)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        setLoading(false);
                        showToast("Đã ban user");
                        loadCurrentTabData();
                        return;
                    }
                    final int[] remaining = {query.size()};
                    query.getDocuments().forEach(doc -> doc.getReference()
                            .update("status", "PROCESSED")
                            .addOnCompleteListener(task -> {
                                remaining[0]--;
                                if (remaining[0] == 0) {
                                    setLoading(false);
                                    showToast("Đã ban user và xử lý report");
                                    loadCurrentTabData();
                                }
                            }));
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showToast("Ban thành công nhưng chưa cập nhật trạng thái report");
                    loadCurrentTabData();
                });
    }

    private void loadUnprocessedReports() {
        loadReportsByStatus("UNPROCESSED");
    }

    private void loadProcessedReports() {
        loadReportsByStatus("PROCESSED");
    }

    private void loadReportsByStatus(@NonNull String status) {
        setLoading(true);
        rvReportedUsers.setAdapter(reportAdapter);
        FirebaseManager.getInstance().getFirestore()
                .collection("reports")
                .whereEqualTo("status", status)
                .get()
                .addOnSuccessListener(reportQuery -> {
                    if (reportQuery.isEmpty()) {
                        setLoading(false);
                        reportAdapter.setItems(new ArrayList<>());
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }
                    List<AdminReportAdapter.Item> items = new ArrayList<>();
                    int[] remaining = {reportQuery.size()};
                    for (DocumentSnapshot doc : reportQuery.getDocuments()) {
                        Report report = doc.toObject(Report.class);
                        if (report == null) {
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                onReportItemsBuilt(items);
                            }
                            continue;
                        }
                        report.setId(doc.getId());
                        buildReportItem(report, item -> {
                            items.add(item);
                            remaining[0]--;
                            if (remaining[0] == 0) {
                                onReportItemsBuilt(items);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    tvEmpty.setVisibility(View.VISIBLE);
                    reportAdapter.setItems(new ArrayList<>());
                    showToast("Không tải được danh sách report");
                });
    }

    private void loadBannedUsers() {
        setLoading(true);
        rvReportedUsers.setAdapter(bannedUserAdapter);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .whereEqualTo("isBanned", true)
                .get()
                .addOnSuccessListener(query -> {
                    List<AdminReportedUserAdapter.ReportedUserItem> items = new ArrayList<>();
                    query.getDocuments().forEach(doc -> {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setId(doc.getId());
                            items.add(new AdminReportedUserAdapter.ReportedUserItem(user, 0));
                        }
                    });
                    setLoading(false);
                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    bannedUserAdapter.setItems(items);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    tvEmpty.setVisibility(View.VISIBLE);
                    showToast("Không tải được danh sách user bị ban");
                });
    }

    private void refreshPendingReportsCount() {
        FirebaseManager.getInstance().getFirestore()
                .collection("reports")
                .whereEqualTo("status", "UNPROCESSED")
                .get()
                .addOnSuccessListener(query -> {
                    int pendingCount = query.size();
                    notifyReportCount(pendingCount);
                    updatePendingReportsCard(pendingCount);
                })
                .addOnFailureListener(e -> {
                    notifyReportCount(0);
                    updatePendingReportsCard(0);
                });
    }

    private void setupTabs() {
        tabNewReports.setOnClickListener(v -> {
            selectedTab = 0;
            updateTabUI();
            loadCurrentTabData();
        });
        tabProcessed.setOnClickListener(v -> {
            selectedTab = 1;
            updateTabUI();
            loadCurrentTabData();
        });
        tabBannedUsers.setOnClickListener(v -> {
            selectedTab = 2;
            updateTabUI();
            loadCurrentTabData();
        });
        updateTabUI();
    }

    private void loadCurrentTabData() {
        refreshPendingReportsCount();
        if (selectedTab == 1) {
            loadProcessedReports();
            return;
        }
        if (selectedTab == 2) {
            loadBannedUsers();
            return;
        }
        loadUnprocessedReports();
    }

    private void updateTabUI() {
        styleTab(tabNewReports, selectedTab == 0);
        styleTab(tabProcessed, selectedTab == 1);
        styleTab(tabBannedUsers, selectedTab == 2);
    }

    private void styleTab(MaterialButton button, boolean active) {
        if (button == null) {
            return;
        }
        button.setStrokeWidth(active ? 0 : 1);
        int bgColor = requireContext().getColor(active ? R.color.primary_purple : R.color.white);
        int textColor = requireContext().getColor(active ? R.color.white : R.color.text);
        int strokeColor = requireContext().getColor(R.color.divider);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        button.setTextColor(textColor);
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        rvReportedUsers.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        if (loading) {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void notifyReportCount(int count) {
        if (dashboardListener != null) {
            dashboardListener.onReportCountChanged(count);
        }
    }

    private void updatePendingReportsCard(int count) {
        if (tvPendingReportsValue != null) {
            tvPendingReportsValue.setText(String.valueOf(Math.max(count, 0)));
        }
    }

    private void onReportItemsBuilt(List<AdminReportAdapter.Item> items) {
        setLoading(false);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        reportAdapter.setItems(items);
    }

    private void buildReportItem(@NonNull Report report, @NonNull ReportItemCallback callback) {
        AdminReportAdapter.Item item = new AdminReportAdapter.Item();
        item.reportId = report.getId();
        item.targetId = report.getTargetId();
        item.type = report.getType();
        item.reason = report.getReason();
        item.createdAt = report.getCreatedAt();
        item.targetUserId = "USER".equalsIgnoreCase(report.getType()) ? report.getTargetId() : null;
        resolveReporterInfo(report.getReporterId(), item, reporterName -> {
            item.reporterName = reporterName;
            resolveTargetLabel(report, item, () -> callback.onDone(item));
        });
    }

    private void resolveReporterInfo(@Nullable String reporterId, @NonNull AdminReportAdapter.Item item, @NonNull StringCallback callback) {
        if (reporterId == null || reporterId.isEmpty()) {
            item.reporterAvatarUrl = null;
            callback.onDone("-");
            return;
        }
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(reporterId)
                .get()
                .addOnSuccessListener(doc -> {
                    User reporter = doc.toObject(User.class);
                    item.reporterAvatarUrl = reporter != null ? reporter.getAvatarUrl() : null;
                    String name = reporter != null && reporter.getUsername() != null && !reporter.getUsername().isEmpty()
                            ? "@" + reporter.getUsername()
                            : reporterId;
                    callback.onDone(name);
                })
                .addOnFailureListener(e -> {
                    item.reporterAvatarUrl = null;
                    callback.onDone(reporterId);
                });
    }

    private void resolveTargetLabel(@NonNull Report report, @NonNull AdminReportAdapter.Item item, @NonNull Runnable done) {
        String targetType = report.getType() == null ? "" : report.getType();
        String targetId = report.getTargetId() == null ? "" : report.getTargetId();
        if ("USER".equalsIgnoreCase(targetType)) {
            if (targetId.isEmpty()) {
                item.targetLabel = "User: -";
                done.run();
                return;
            }
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_USERS)
                    .document(targetId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        User targetUser = userDoc.toObject(User.class);
                        item.targetUserId = targetId;
                        item.targetUsername = targetUser != null ? targetUser.getUsername() : null;
                        item.targetAvatarUrl = targetUser != null ? targetUser.getAvatarUrl() : null;
                        String username = targetUser != null && targetUser.getUsername() != null ? targetUser.getUsername() : targetId;
                        item.targetLabel = "User bị báo cáo: @" + username;
                        done.run();
                    })
                    .addOnFailureListener(e -> {
                        item.targetUserId = targetId;
                        item.targetUsername = targetId;
                        item.targetLabel = "User bị báo cáo: @" + targetId;
                        done.run();
                    });
            return;
        }
        if ("POST".equalsIgnoreCase(targetType)) {
            if (targetId.isEmpty()) {
                item.targetLabel = "Bài viết: -";
                done.run();
                return;
            }
            FirebaseManager.getInstance().getFirestore()
                    .collection(FirebaseManager.COLLECTION_POSTS)
                    .document(targetId)
                    .get()
                    .addOnSuccessListener(postDoc -> {
                        Post post = postDoc.toObject(Post.class);
                        if (post == null || post.getUserId() == null || post.getUserId().isEmpty()) {
                            item.targetLabel = "Bài viết bị báo cáo: " + targetId;
                            item.postPreviewUrl = null;
                            done.run();
                            return;
                        }
                        resolvePostOwnerAndPreview(post, item, done);
                    })
                    .addOnFailureListener(e -> {
                        item.targetLabel = "Bài viết bị báo cáo: " + targetId;
                        item.postPreviewUrl = null;
                        done.run();
                    });
            return;
        }
        item.targetLabel = "Đối tượng bị báo cáo: " + targetId;
        item.targetAvatarUrl = null;
        item.postPreviewUrl = null;
        done.run();
    }

    private void resolvePostOwnerAndPreview(@NonNull Post post, @NonNull AdminReportAdapter.Item item, @NonNull Runnable done) {
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(post.getUserId())
                .get()
                .addOnSuccessListener(ownerDoc -> {
                    User owner = ownerDoc.toObject(User.class);
                    item.targetAvatarUrl = owner != null ? owner.getAvatarUrl() : null;
                    item.targetUserId = post.getUserId();
                    item.targetUsername = owner != null ? owner.getUsername() : post.getUserId();
                    String ownerUsername = owner != null && owner.getUsername() != null ? owner.getUsername() : post.getUserId();
                    item.targetLabel = "Bài viết của @" + ownerUsername;
                    resolvePostPreview(post.getId(), item, done);
                })
                .addOnFailureListener(e -> {
                    item.targetAvatarUrl = null;
                    item.targetUserId = post.getUserId();
                    item.targetUsername = post.getUserId();
                    item.targetLabel = "Bài viết của user: " + post.getUserId();
                    resolvePostPreview(post.getId(), item, done);
                });
    }

    private void resolvePostPreview(@Nullable String postId, @NonNull AdminReportAdapter.Item item, @NonNull Runnable done) {
        if (postId == null || postId.isEmpty()) {
            item.postPreviewUrl = null;
            done.run();
            return;
        }
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_POST_MEDIA)
                .whereEqualTo("postId", postId)
                .whereEqualTo("mediaType", "IMAGE")
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        item.postPreviewUrl = null;
                        done.run();
                        return;
                    }
                    item.postPreviewUrl = query.getDocuments().get(0).getString("mediaUrl");
                    done.run();
                })
                .addOnFailureListener(e -> {
                    item.postPreviewUrl = null;
                    done.run();
                });
    }

    private interface StringCallback {
        void onDone(String value);
    }

    private interface ReportItemCallback {
        void onDone(AdminReportAdapter.Item item);
    }

    private void showPostReportActionDialog(@NonNull AdminReportAdapter.Item item) {
        if (!isAdded()) {
            return;
        }
        String[] actions = new String[]{"Xóa bài viết + cảnh báo", "Ban user"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Xử lý report bài post")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        handleDeletePostAndWarn(item);
                    } else if (which == 1) {
                        handleBlockUserFromPost(item);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void handleDeletePostAndWarn(@NonNull AdminReportAdapter.Item item) {
        if (item.targetId == null || item.targetId.isEmpty()) {
            showToast("Không tìm thấy post để xóa");
            return;
        }
        setLoading(true);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_POST_MEDIA)
                .whereEqualTo("postId", item.targetId)
                .get()
                .addOnSuccessListener(mediaQuery -> {
                    WriteBatch batch = FirebaseManager.getInstance().getFirestore().batch();
                    batch.delete(FirebaseManager.getInstance().getFirestore()
                            .collection(FirebaseManager.COLLECTION_POSTS)
                            .document(item.targetId));
                    mediaQuery.getDocuments().forEach(doc -> batch.delete(doc.getReference()));
                    batch.commit()
                            .addOnSuccessListener(unused -> sendWarnAndMarkProcessed(item))
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showToast("Không thể xóa bài post");
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showToast("Không thể tải media của post");
                });
    }

    private void sendWarnAndMarkProcessed(@NonNull AdminReportAdapter.Item item) {
        if (item.targetUserId == null || item.targetUserId.isEmpty()) {
            markSingleReportProcessed(item.reportId, "DELETE_AND_WARN");
            return;
        }
        String notificationId = FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                .document()
                .getId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", notificationId);
        payload.put("userId", item.targetUserId);
        payload.put("type", "ADMIN_WARN");
        payload.put("referenceId", item.targetId != null ? item.targetId : "");
        payload.put("isRead", false);
        payload.put("content", "Bài viết của bạn đang bị báo cáo. Vui lòng tuân thủ quy định cộng đồng.");
        payload.put("createdAt", FieldValue.serverTimestamp());

        WriteBatch warnBatch = FirebaseManager.getInstance().getFirestore().batch();
        warnBatch.set(
                FirebaseManager.getInstance().getFirestore()
                        .collection(FirebaseManager.COLLECTION_NOTIFICATIONS)
                        .document(notificationId),
                payload
        );
        Map<String, Object> userWarnUpdate = new HashMap<>();
        userWarnUpdate.put("warningCount", FieldValue.increment(1));
        warnBatch.update(
                FirebaseManager.getInstance().getFirestore()
                        .collection(FirebaseManager.COLLECTION_USERS)
                        .document(item.targetUserId),
                userWarnUpdate
        );
        warnBatch.commit()
                .addOnSuccessListener(unused -> markSingleReportProcessed(item.reportId, "DELETE_AND_WARN"))
                .addOnFailureListener(e -> {
                    // Post already deleted, still finish report with note.
                    showToast("Đã xóa bài viết nhưng ghi nhận cảnh cáo thất bại");
                    markSingleReportProcessed(item.reportId, "DELETE_AND_WARN");
                });
    }

    private void handleBlockUserFromPost(@NonNull AdminReportAdapter.Item item) {
        if (item.targetUserId == null || item.targetUserId.isEmpty()) {
            showToast("Không tìm thấy user để chặn");
            return;
        }
        setLoading(true);
        FirebaseManager.getInstance().getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(item.targetUserId)
                .update("isBanned", true)
                .addOnSuccessListener(unused -> markSingleReportProcessed(item.reportId, "BLOCK_USER"))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showToast("Không thể chặn user");
                });
    }

    private void markSingleReportProcessed(@Nullable String reportId, @NonNull String resolution) {
        if (reportId == null || reportId.isEmpty()) {
            setLoading(false);
            loadCurrentTabData();
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "PROCESSED");
        updates.put("resolution", resolution);
        updates.put("handledAt", FieldValue.serverTimestamp());
        FirebaseManager.getInstance().getFirestore()
                .collection("reports")
                .document(reportId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    showToast("Đã xử lý report");
                    loadCurrentTabData();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showToast("Đã xử lý hành động nhưng chưa cập nhật report");
                    loadCurrentTabData();
                });
    }
}
