package com.example.social_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.social_app.R;
import com.example.social_app.firebase.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText edtIdentifier, edtPassword;
    private Button btnLogin;
    private TextView btnGoToRegister;
    private ProgressBar progressBar;

    private final FirebaseManager firebaseManager = FirebaseManager.getInstance();

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

        // Nếu đã đăng nhập rồi, bỏ qua màn hình login
        if (firebaseManager.getAuth().getCurrentUser() != null) {
            navigateToMain();
        }
    }

    private void initViews() {
        edtIdentifier = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
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
                    setLoading(false);
                    navigateToMain();
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

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}