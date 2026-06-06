package com.cinema.jazz.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.model.UserProfile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {
    private EditText etEmail, etPass, etConfirm;
    private Button btnRegister;
    private TextView tvLogin, tvError;
    private ProgressBar progress;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_register);
        etEmail    = findViewById(R.id.et_email);
        etPass     = findViewById(R.id.et_password);
        etConfirm  = findViewById(R.id.et_confirm);
        btnRegister= findViewById(R.id.btn_register);
        tvLogin    = findViewById(R.id.tv_login);
        tvError    = findViewById(R.id.tv_error);
        progress   = findViewById(R.id.progress);

        // Load logo (img_bg not present in this layout — handled by background color)
        android.widget.ImageView imgLogo = findViewById(R.id.img_logo);
        if (imgLogo != null) Glide.with(this).load(Constants.LOGO_URL).circleCrop().into(imgLogo);

        btnRegister.setOnClickListener(v -> doRegister());
        tvLogin.setOnClickListener(v -> finish());
    }

    /** Returns true if this device has already registered an account. */
    private boolean deviceAlreadyRegistered() {
        SharedPreferences prefs = getSharedPreferences("jazz_device", MODE_PRIVATE);
        return prefs.contains("registered_device_id");
    }

    private void markDeviceRegistered() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        getSharedPreferences("jazz_device", MODE_PRIVATE)
            .edit().putString("registered_device_id", androidId).apply();
    }

    private void doRegister() {
        String email   = etEmail.getText().toString().trim();
        String pass    = etPass.getText().toString();
        String confirm = etConfirm.getText().toString();
        if (email.isEmpty() || pass.isEmpty()) { showErr("Please fill in all fields"); return; }
        if (!pass.equals(confirm))             { showErr("Passwords don't match"); return; }
        if (pass.length() < 6)                 { showErr("Password must be at least 6 characters"); return; }
        if (deviceAlreadyRegistered()) {
            showErr("This device already has a Jazz Cinema account. Please sign in.");
            return;
        }
        setLoading(true);
        exec.execute(() -> {
            try {
                UserProfile u = AuthManager.register(email, pass);
                u.save(this);
                markDeviceRegistered();
                main.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Account created! Status: Pending", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                });
            } catch (Exception e) {
                main.post(() -> { setLoading(false); showErr(e.getMessage()); });
            }
        });
    }

    private void showErr(String m) {
        if (tvError == null) return;
        tvError.setText(m);
        tvError.setVisibility(View.VISIBLE);
    }
    private void setLoading(boolean on) {
        if (progress   != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);
        if (btnRegister!= null) btnRegister.setEnabled(!on);
    }
}
