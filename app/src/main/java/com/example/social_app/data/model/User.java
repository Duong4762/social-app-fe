package com.example.social_app.data.model;

import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Entity: User (Người dùng)
 * Firestore collection: users
 *
 * Lưu trữ thông tin tài khoản người dùng. Là trung tâm của hệ thống,
 * liên kết đến hầu hết các thực thể khác thông qua userId.
 *
 * Trường id được dùng làm Document ID trong Firestore (cũng là Firebase Auth UID).
 */
public class User {

    private String id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private String gender;
    private String dateOfBirth;   // lưu dạng "yyyy-MM-dd"
    private String role;          // "USER" | "ADMIN"
    private boolean isActive;
    private boolean isBanned; // false: chưa bị ban(allowed), true: đã bị ban(blocked)
    private long warningCount; // số lần cảnh báo

    @ServerTimestamp
    private Date createdAt;

    @ServerTimestamp
    private Date updatedAt;

    // -------------------------------------------------------
    // Constructor rỗng bắt buộc để Firestore có thể deserialize
    // -------------------------------------------------------
    public User() {}

    public User(String id, String username, String email, String fullName,
                String avatarUrl, String bio, String gender,
                String dateOfBirth, String role, boolean isActive) {
        this(id, username, email, fullName, avatarUrl, bio, gender, dateOfBirth, role, isActive, false);
    }

    public User(String id, String username, String email, String fullName,
                String avatarUrl, String bio, String gender,
                String dateOfBirth, String role, boolean isActive, boolean isBanned) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.role = role;
        this.isActive = isActive;
        this.isBanned = isBanned;
    }

    // -------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    @PropertyName("isActive")
    public boolean isActive() { return isActive; }

    @PropertyName("isActive")
    public void setActive(boolean active) { isActive = active; }

    @PropertyName("isBanned")
    public boolean isBanned() { return isBanned; }

    @PropertyName("isBanned")
    public void setBanned(boolean banned) { isBanned = banned; }

    public long getWarningCount() { return warningCount; }
    public void setWarningCount(long warningCount) { this.warningCount = warningCount; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
