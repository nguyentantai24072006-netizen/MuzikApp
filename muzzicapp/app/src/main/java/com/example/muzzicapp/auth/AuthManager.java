package com.example.muzzicapp.auth;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.ArrayList;
import java.util.List;

public class AuthManager {

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String errorMessage);
    }

    public interface AuthStateListener {
        void onAuthStateChanged(FirebaseUser user);
    }

    private static volatile AuthManager instance;
    private final FirebaseAuth firebaseAuth;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AuthStateListener> listeners = new ArrayList<>();

    private AuthManager() {
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.addAuthStateListener(auth -> {
            FirebaseUser user = auth.getCurrentUser();
            for (AuthStateListener listener : new ArrayList<>(listeners)) {
                if (listener != null) {
                    mainHandler.post(() -> listener.onAuthStateChanged(user));
                }
            }
        });
    }

    public static AuthManager getInstance() {
        if (instance == null) {
            synchronized (AuthManager.class) {
                if (instance == null) {
                    instance = new AuthManager();
                }
            }
        }
        return instance;
    }

    public boolean isLoggedIn() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null && !user.isAnonymous();
    }

    public boolean isAnonymous() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null && user.isAnonymous();
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    @NonNull
    public String getUserId() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user != null ? user.getUid() : "guest";
    }

    public String getUserEmail() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return (user != null && !user.isAnonymous()) ? user.getEmail() : "";
    }

    public String getDisplayName() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return "Chưa đăng nhập";
        if (user.isAnonymous()) return "Khách ẩn danh";
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName();
        }
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "Người dùng Muzzic";
    }

    public void signInWithEmail(String email, String password, AuthCallback callback) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            if (callback != null) callback.onError("Vui lòng nhập đầy đủ Email và Mật khẩu");
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(authResult -> {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(authResult.getUser()));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        String msg = translateAuthError(e.getMessage());
                        mainHandler.post(() -> callback.onError(msg));
                    }
                });
    }

    public void registerWithEmail(String email, String password, String displayName, AuthCallback callback) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            if (callback != null) callback.onError("Vui lòng nhập đầy đủ Email và Mật khẩu");
            return;
        }
        if (password.length() < 6) {
            if (callback != null) callback.onError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        }

        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null && displayName != null && !displayName.trim().isEmpty()) {
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(displayName.trim())
                                .build();
                        user.updateProfile(profileUpdates);
                    }
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(user));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        String msg = translateAuthError(e.getMessage());
                        mainHandler.post(() -> callback.onError(msg));
                    }
                });
    }

    public void signInAnonymously(AuthCallback callback) {
        firebaseAuth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(authResult.getUser()));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(e.getMessage()));
                    }
                });
    }

    public void signOut() {
        firebaseAuth.signOut();
    }

    public void deleteAccount(AuthCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onError("Không tìm thấy phiên đăng nhập");
            return;
        }

        user.delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(null));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        String msg = translateAuthError(e.getMessage());
                        if (e.getMessage() != null && e.getMessage().contains("CREDENTIAL_TOO_OLD_LOGIN_AGAIN")) {
                            msg = "Vì lý do bảo mật của Google, vui lòng đăng xuất và đăng nhập lại trước khi xóa vĩnh viễn tài khoản.";
                        }
                        final String finalMsg = msg;
                        mainHandler.post(() -> callback.onError(finalMsg));
                    }
                });
    }

    public void addAuthStateListener(AuthStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeAuthStateListener(AuthStateListener listener) {
        listeners.remove(listener);
    }

    private String translateAuthError(String raw) {
        if (raw == null) return "Lỗi xác thực không xác định";
        if (raw.contains("password is invalid") || raw.contains("wrong password") || raw.contains("INVALID_LOGIN_CREDENTIALS")) {
            return "Email hoặc mật khẩu không chính xác";
        }
        if (raw.contains("email address is already in use")) {
            return "Email này đã được đăng ký tài khoản";
        }
        if (raw.contains("badly formatted")) {
            return "Định dạng email không hợp lệ";
        }
        if (raw.contains("user was not found") || raw.contains("no user record")) {
            return "Tài khoản không tồn tại";
        }
        if (raw.contains("network error")) {
            return "Lỗi kết nối mạng, vui lòng thử lại";
        }
        return raw;
    }
}
