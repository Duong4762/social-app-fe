package com.example.social_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText edtUsername, edtEmail, edtPassword;
    private Button btnRegister, btnGoToLogin;
    private ProgressBar progressBar;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupListeners();
    }

    private void initViews() {
        edtUsername = findViewById(R.id.edtUsername);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoToLogin = findViewById(R.id.btnGoToLogin);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        btnGoToLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String username = edtUsername.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            edtUsername.setError("Vui lòng nhập tên người dùng");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            edtEmail.setError("Vui lòng nhập email");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            edtPassword.setError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        }

        setLoading(true);

        // Bước 1: Tạo tài khoản Firebase Auth
        firebaseManager.getAuth()
                .createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        setLoading(false);
                        Toast.makeText(this, "Tạo tài khoản thất bại", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Bước 2: Tạo document User trong Firestore
                    createUserDocument(firebaseUser.getUid(), username, email);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Register failed", e);
                    setLoading(false);
                    Toast.makeText(this, "Đăng ký thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Tạo document trong Firestore collection "users" sau khi Firebase Auth tạo tài
     * khoản thành công.
     * Document ID = Firebase Auth UID để dễ dàng tra cứu.
     */
    private void createUserDocument(String uid, String username, String email) {
        User newUser = new User(
                uid,
                username,
                email,
                "", // fullName (chưa có)
                "", // avatarUrl
                "", // bio
                "", // gender
                "", // dateOfBirth
                "USER", // role mặc định
                true // isActive
        );

        FirebaseFirestore db = firebaseManager.getFirestore();
        db.collection(FirebaseManager.COLLECTION_USERS)
                .document(uid)
                .set(newUser)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "User document created: " + uid);
                    setLoading(false);
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                    navigateToLogin();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create user document", e);
                    setLoading(false);
                    Toast.makeText(this, "Tạo hồ sơ thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }
}