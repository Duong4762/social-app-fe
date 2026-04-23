package com.example.social_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.social_app.R;
import com.example.social_app.data.model.User;
import com.example.social_app.firebase.AdminUserInitializer;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText edtIdentifier, edtPassword;
    private ImageView ivTogglePassword;
    private Button btnLogin;
    private TextView btnGoToRegister;
    private ProgressBar progressBar;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupListeners();
        initializeDefaultAdminUser();

        // Nếu đã đăng nhập rồi, bỏ qua màn hình login
        FirebaseUser currentUser = firebaseManager.getAuth().getCurrentUser();
        if (currentUser != null) {
            resolveLoginDestination(currentUser);
        }
    }

    private void initViews() {
        edtIdentifier = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);
        progressBar = findViewById(R.id.progressBar);

        // Password is hidden by default.
        edtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        if (isPasswordVisible) {
            edtPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            ivTogglePassword.setImageResource(R.drawable.ic_eye_off);
        } else {
            edtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ivTogglePassword.setImageResource(R.drawable.ic_eye);
        }
        edtPassword.setSelection(edtPassword.getText().length());
    }

    private void attemptLogin() {
        String email = edtIdentifier.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            edtIdentifier.setError("Vui lòng nhập username hoặc email");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            edtPassword.setError("Vui lòng nhập mật khẩu");
            return;
        }

        setLoading(true);

        firebaseManager.getAuth()
                .signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    Log.d(TAG, "Login success: " + (firebaseUser != null ? firebaseUser.getUid() : "null"));
                    if (firebaseUser == null) {
                        setLoading(false);
                        Toast.makeText(this, "Đăng nhập thất bại: thiếu thông tin user", Toast.LENGTH_LONG).show();
                        return;
                    }
                    resolveLoginDestination(firebaseUser);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Login failed", e);
                    setLoading(false);
                    Toast.makeText(this, "Đăng nhập thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToAdmin() {
        Intent intent = new Intent(this, AdminActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void resolveLoginDestination(FirebaseUser firebaseUser) {
        firebaseManager.getFirestore()
                .collection(FirebaseManager.COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    setLoading(false);
                    if (!snapshot.exists()) {
                        navigateToMain();
                        return;
                    }

                    User user = snapshot.toObject(User.class);
                    if (user != null && user.isBanned()) {
                        firebaseManager.getAuth().signOut();
                        Toast.makeText(this, "Tài khoản của bạn đã bị quản lý khóa", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String role = user != null ? user.getRole() : null;
                    if ("ADMIN".equalsIgnoreCase(role)) {
                        navigateToAdmin();
                    } else {
                        navigateToMain();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Failed to resolve login destination", e);
                    navigateToMain();
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }

    private void initializeDefaultAdminUser() {
        new AdminUserInitializer(firebaseManager.getFirestore()).ensureAdminUserExists();
    }
}