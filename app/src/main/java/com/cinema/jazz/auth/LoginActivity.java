package com.cinema.jazz.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.MainActivity;
import com.cinema.jazz.model.UserProfile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvError;
    private ProgressBar progress;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_login);
        etEmail    = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin   = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        tvError    = findViewById(R.id.tv_error);
        progress   = findViewById(R.id.progress);

        // Load logo (img_bg is not present in this layout — handled by background color)
        android.widget.ImageView imgLogo = findViewById(R.id.img_logo);
        if (imgLogo != null) Glide.with(this).load(Constants.LOGO_URL).circleCrop().into(imgLogo);

        btnLogin.setOnClickListener(v -> doLogin());
        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString();
        if (email.isEmpty() || pass.isEmpty()) { showErr("Please fill in all fields"); return; }
        setLoading(true);
        exec.execute(() -> {
            try {
                UserProfile u = AuthManager.login(this, email, pass);
                main.post(() -> {
                    setLoading(false);
                    if (u.isSuspended()) {
                        showErr("Account suspended. Please contact support.");
                    } else {
                        goHome();
                    }
                });
            } catch (Exception e) {
                main.post(() -> { setLoading(false); showErr(e.getMessage()); });
            }
        });
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
    private void showErr(String msg) {
        if (tvError == null) return;
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
    private void setLoading(boolean on) {
        if (progress != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);
        if (btnLogin != null) btnLogin.setEnabled(!on);
    }
}
