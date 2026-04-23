package com.example.social_app.data.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: Report (Báo cáo)
 * Firestore collection: reports
 *
 * - reporterId: ID của người dùng thực hiện report
 * - targetId: ID đối tượng bị report (post hoặc user)
 * - type: "POST" | "USER"
 * - status: "PENDING" | "REVIEWED" | "RESOLVED" | "REJECTED"
 * - createdAt: thời gian report
 */
public class Report {

    private String id;
    private String reporterId;
    private String targetId;
    private String type; // POST | USER
    private String status; // UNPROCESSED | PROCESSED
    private String reason;

    @ServerTimestamp
    private Date createdAt;

    public Report() {}

    public Report(String id, String reporterId, String targetId, String type, String status) {
        this.id = id;
        this.reporterId = reporterId;
        this.targetId = targetId;
        this.type = type;
        this.status = status;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
