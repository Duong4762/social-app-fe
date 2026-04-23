package com.example.social_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
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

import java.util.Calendar;
import java.util.Locale;

public class RegisterActivity extends BaseActivity {

    private static final String TAG = "RegisterActivity";

    private EditText edtFullName, edtUsername, edtEmail, edtPassword, edtConfirmPassword;
    private ImageView ivTogglePassword, ivToggleConfirmPassword;
    private Button btnRegister;
    private TextView btnGoToLogin;
    private TextView txtDay, txtMonth, txtYear;
    private Spinner spGender;
    private ProgressBar progressBar;
    private String selectedDateOfBirth = "";
    private String selectedGender = "";
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

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
        edtFullName = findViewById(R.id.edtFullName);
        edtUsername = findViewById(R.id.edtUsername);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);
        txtDay = findViewById(R.id.txtDay);
        txtMonth = findViewById(R.id.txtMonth);
        txtYear = findViewById(R.id.txtYear);
        spGender = findViewById(R.id.spGender);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoToLogin = findViewById(R.id.btnGoToLogin);
        progressBar = findViewById(R.id.progressBar);

        edtPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
        edtConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
        setupGenderDropdown();
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        btnGoToLogin.setOnClickListener(v -> finish());
        txtDay.setOnClickListener(v -> showDatePicker());
        txtMonth.setOnClickListener(v -> showDatePicker());
        txtYear.setOnClickListener(v -> showDatePicker());
        ivTogglePassword.setOnClickListener(v -> togglePasswordVisibility());
        ivToggleConfirmPassword.setOnClickListener(v -> toggleConfirmPasswordVisibility());
    }

    private void attemptRegister() {
        String fullName = edtFullName.getText().toString().trim();
        String username = edtUsername.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();
        String confirmPassword = edtConfirmPassword.getText().toString().trim();
        String genderValue = spGender.getSelectedItem() != null
                ? spGender.getSelectedItem().toString()
                : "";
        selectedGender = genderValue;

        if (TextUtils.isEmpty(fullName)) {
            edtFullName.setError("Vui lòng nhập họ tên");
            return;
        }
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
        if (!password.equals(confirmPassword)) {
            edtConfirmPassword.setError("Mật khẩu xác nhận không khớp");
            return;
        }
        if (TextUtils.isEmpty(selectedDateOfBirth)) {
            Toast.makeText(this, "Vui lòng chọn ngày sinh", Toast.LENGTH_SHORT).show();
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
                    createUserDocument(firebaseUser.getUid(), username, email, fullName);
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
    private void createUserDocument(String uid, String username, String email, String fullName) {
        User newUser = new User(
                uid,
                username,
                email,
                fullName,
                "", // avatarUrl
                "", // bio
                selectedGender,
                selectedDateOfBirth,
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

    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    txtDay.setText(String.format(Locale.getDefault(), "%02d", dayOfMonth));
                    txtMonth.setText(String.format(Locale.getDefault(), "%02d", month + 1));
                    txtYear.setText(String.valueOf(year));
                    selectedDateOfBirth = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            dayOfMonth
                    );
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void showGenderPicker() {
        // Deprecated: replaced by inline Spinner dropdown.
    }

    private void setupGenderDropdown() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.gender_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGender.setAdapter(adapter);
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

    private void toggleConfirmPasswordVisibility() {
        isConfirmPasswordVisible = !isConfirmPasswordVisible;
        if (isConfirmPasswordVisible) {
            edtConfirmPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            ivToggleConfirmPassword.setImageResource(R.drawable.ic_eye_off);
        } else {
            edtConfirmPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ivToggleConfirmPassword.setImageResource(R.drawable.ic_eye);
        }
        edtConfirmPassword.setSelection(edtConfirmPassword.getText().length());
    }
}
