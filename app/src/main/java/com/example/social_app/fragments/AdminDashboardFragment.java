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

import com.example.social_app.R;
import com.example.social_app.adapters.AdminReportedUserAdapter;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.android.material.button.MaterialButton;

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
    private AdminReportedUserAdapter adapter;
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

        adapter = new AdminReportedUserAdapter((user, reportCount) -> {
            if (user == null || user.getId() == null || user.getId().isEmpty()) {
                return;
            }
            if (user.isBanned()) {
                toggleBanUser(user, false);
            } else {
                showConfirmBanDialog(user);
            }
        });
        rvReportedUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReportedUsers.setAdapter(adapter);
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
        setLoading(true);
        FirebaseManager.getInstance().getFirestore()
                .collection("reports")
                .whereEqualTo("type", "USER")
                .whereEqualTo("status", "UNPROCESSED")
                .get()
                .addOnSuccessListener(reportQuery -> {
                    int pendingCount = reportQuery.size();
                    notifyReportCount(pendingCount);
                    updatePendingReportsCard(pendingCount);
                    Map<String, Integer> reportCountMap = new HashMap<>();
                    reportQuery.getDocuments().forEach(doc -> {
                        String targetId = doc.getString("targetId");
                        if (targetId != null && !targetId.isEmpty()) {
                            int count = reportCountMap.containsKey(targetId) ? reportCountMap.get(targetId) : 0;
                            reportCountMap.put(targetId, count + 1);
                        }
                    });
                    if (reportCountMap.isEmpty()) {
                        setLoading(false);
                        adapter.setItems(new ArrayList<>());
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }
                    List<AdminReportedUserAdapter.ReportedUserItem> items = new ArrayList<>();
                    int[] remaining = {reportCountMap.size()};
                    reportCountMap.forEach((userId, count) -> FirebaseManager.getInstance().getFirestore()
                            .collection(FirebaseManager.COLLECTION_USERS)
                            .document(userId)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {
                                    User user = userDoc.toObject(User.class);
                                    if (user != null) {
                                        user.setId(userDoc.getId());
                                        items.add(new AdminReportedUserAdapter.ReportedUserItem(user, count));
                                    }
                                }
                                remaining[0]--;
                                if (remaining[0] == 0) {
                                    setLoading(false);
                                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                                    adapter.setItems(items);
                                }
                            })
                            .addOnFailureListener(e -> {
                                remaining[0]--;
                                if (remaining[0] == 0) {
                                    setLoading(false);
                                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                                    adapter.setItems(items);
                                }
                            }));
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    tvEmpty.setVisibility(View.VISIBLE);
                    notifyReportCount(0);
                    updatePendingReportsCard(0);
                    showToast("Không tải được danh sách report");
                });
    }

    private void loadProcessedReports() {
        setLoading(true);
        FirebaseManager.getInstance().getFirestore()
                .collection("reports")
                .whereEqualTo("type", "USER")
                .whereEqualTo("status", "PROCESSED")
                .get()
                .addOnSuccessListener(reportQuery -> {
                    Map<String, Integer> reportCountMap = new HashMap<>();
                    reportQuery.getDocuments().forEach(doc -> {
                        String targetId = doc.getString("targetId");
                        if (targetId != null && !targetId.isEmpty()) {
                            int count = reportCountMap.containsKey(targetId) ? reportCountMap.get(targetId) : 0;
                            reportCountMap.put(targetId, count + 1);
                        }
                    });
                    if (reportCountMap.isEmpty()) {
                        setLoading(false);
                        adapter.setItems(new ArrayList<>());
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }
                    List<AdminReportedUserAdapter.ReportedUserItem> items = new ArrayList<>();
                    int[] remaining = {reportCountMap.size()};
                    reportCountMap.forEach((userId, count) -> FirebaseManager.getInstance().getFirestore()
                            .collection(FirebaseManager.COLLECTION_USERS)
                            .document(userId)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {
                                    User user = userDoc.toObject(User.class);
                                    if (user != null) {
                                        user.setId(userDoc.getId());
                                        items.add(new AdminReportedUserAdapter.ReportedUserItem(user, count));
                                    }
                                }
                                remaining[0]--;
                                if (remaining[0] == 0) {
                                    setLoading(false);
                                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                                    adapter.setItems(items);
                                }
                            })
                            .addOnFailureListener(e -> {
                                remaining[0]--;
                                if (remaining[0] == 0) {
                                    setLoading(false);
                                    tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                                    adapter.setItems(items);
                                }
                            }));
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    tvEmpty.setVisibility(View.VISIBLE);
                    showToast("Không tải được danh sách đã xử lý");
                });
    }

    private void loadBannedUsers() {
        setLoading(true);
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
                    adapter.setItems(items);
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
                .whereEqualTo("type", "USER")
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
}
