package com.example.muzzicapp.auth;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.muzzicapp.PlaylistManager;
import com.example.muzzicapp.R;
import com.example.muzzicapp.sync.FirebaseSyncManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseUser;

public class AuthDialogHelper {

    public static void showAuthDialog(Context context, Runnable onAuthSuccess) {
        if (context == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_auth, null);
        dialog.setContentView(view);

        TextView tabLogin = view.findViewById(R.id.auth_tab_login);
        TextView tabRegister = view.findViewById(R.id.auth_tab_register);
        TextView tvError = view.findViewById(R.id.auth_tv_error);
        EditText inputName = view.findViewById(R.id.auth_input_name);
        EditText inputEmail = view.findViewById(R.id.auth_input_email);
        EditText inputPassword = view.findViewById(R.id.auth_input_password);
        ProgressBar progress = view.findViewById(R.id.auth_progress);
        TextView btnSubmit = view.findViewById(R.id.auth_btn_submit);
        TextView btnGuest = view.findViewById(R.id.auth_btn_guest);
        View btnClose = view.findViewById(R.id.auth_btn_close);

        final boolean[] isRegisterMode = {false};

        Runnable updateTabs = () -> {
            tvError.setVisibility(View.GONE);
            if (isRegisterMode[0]) {
                tabRegister.setBackgroundResource(R.drawable.bg_chip_selected);
                tabRegister.setTextColor(context.getResources().getColor(R.color.white, null));
                tabLogin.setBackgroundResource(0);
                tabLogin.setTextColor(context.getResources().getColor(R.color.text_secondary_adaptive, null));
                inputName.setVisibility(View.VISIBLE);
                btnSubmit.setText("Tạo tài khoản");
            } else {
                tabLogin.setBackgroundResource(R.drawable.bg_chip_selected);
                tabLogin.setTextColor(context.getResources().getColor(R.color.white, null));
                tabRegister.setBackgroundResource(0);
                tabRegister.setTextColor(context.getResources().getColor(R.color.text_secondary_adaptive, null));
                inputName.setVisibility(View.GONE);
                btnSubmit.setText("Đăng nhập");
            }
        };

        tabLogin.setOnClickListener(v -> {
            isRegisterMode[0] = false;
            updateTabs.run();
        });

        tabRegister.setOnClickListener(v -> {
            isRegisterMode[0] = true;
            updateTabs.run();
        });

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        btnGuest.setOnClickListener(v -> {
            progress.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);
            btnGuest.setEnabled(false);
            AuthManager.getInstance().signInAnonymously(new AuthManager.AuthCallback() {
                @Override
                public void onSuccess(FirebaseUser user) {
                    progress.setVisibility(View.GONE);
                    dialog.dismiss();
                    Toast.makeText(context, "Đang sử dụng chế độ Khách (Ẩn danh)", Toast.LENGTH_SHORT).show();
                    if (onAuthSuccess != null) onAuthSuccess.run();
                }

                @Override
                public void onError(String errorMessage) {
                    progress.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    btnGuest.setEnabled(true);
                    tvError.setText(errorMessage);
                    tvError.setVisibility(View.VISIBLE);
                }
            });
        });

        btnSubmit.setOnClickListener(v -> {
            String email = inputEmail.getText().toString().trim();
            String password = inputPassword.getText().toString().trim();
            String name = inputName.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                tvError.setText("Vui lòng nhập email và mật khẩu");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            progress.setVisibility(View.VISIBLE);
            tvError.setVisibility(View.GONE);
            btnSubmit.setEnabled(false);

            if (isRegisterMode[0]) {
                AuthManager.getInstance().registerWithEmail(email, password, name, new AuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        progress.setVisibility(View.GONE);
                        dialog.dismiss();
                        Toast.makeText(context, "Đăng ký thành công! Chào mừng " + AuthManager.getInstance().getDisplayName(), Toast.LENGTH_SHORT).show();
                        PlaylistManager.getInstance(context).syncWithCloud(null);
                        if (onAuthSuccess != null) onAuthSuccess.run();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progress.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        tvError.setText(errorMessage);
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                AuthManager.getInstance().signInWithEmail(email, password, new AuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(FirebaseUser user) {
                        progress.setVisibility(View.GONE);
                        dialog.dismiss();
                        Toast.makeText(context, "Đăng nhập thành công! Đang tải dữ liệu...", Toast.LENGTH_SHORT).show();
                        PlaylistManager.getInstance(context).syncWithCloud(() -> {
                            Toast.makeText(context, "Đã đồng bộ toàn bộ playlist & nhạc yêu thích!", Toast.LENGTH_SHORT).show();
                        });
                        if (onAuthSuccess != null) onAuthSuccess.run();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        progress.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        tvError.setText(errorMessage);
                        tvError.setVisibility(View.VISIBLE);
                    }
                });
            }
        });

        dialog.show();
    }

    public static void showUserProfileDialog(Context context, Runnable onLoggedOutCallback) {
        if (context == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_user_profile, null);
        dialog.setContentView(view);

        TextView tvAvatar = view.findViewById(R.id.profile_avatar_text);
        TextView tvName = view.findViewById(R.id.profile_tv_name);
        TextView tvEmail = view.findViewById(R.id.profile_tv_email);
        TextView tvSyncStatus = view.findViewById(R.id.profile_tv_sync_status);
        TextView btnSyncNow = view.findViewById(R.id.profile_btn_sync_now);
        TextView btnSignOut = view.findViewById(R.id.profile_btn_sign_out);
        TextView btnDeleteAccount = view.findViewById(R.id.profile_btn_delete_account);
        View btnClose = view.findViewById(R.id.profile_btn_close);

        String name = AuthManager.getInstance().getDisplayName();
        String email = AuthManager.getInstance().getUserEmail();
        tvName.setText(name);
        tvEmail.setText(email.isEmpty() ? "Tài khoản Khách" : email);
        if (name != null && !name.isEmpty()) {
            tvAvatar.setText(name.substring(0, 1).toUpperCase());
        }

        if (AuthManager.getInstance().isAnonymous()) {
            tvSyncStatus.setText("Đang dùng tài khoản Khách (Chưa đồng bộ Cloud)");
            tvSyncStatus.setTextColor(0xFFFF9500);
            btnSyncNow.setText("Đăng nhập để đồng bộ đám mây");
            btnSyncNow.setOnClickListener(v -> {
                dialog.dismiss();
                showAuthDialog(context, null);
            });
        } else {
            tvSyncStatus.setText("Đã kết nối với Google Firebase Firestore");
            tvSyncStatus.setTextColor(0xFF34C759);
            btnSyncNow.setOnClickListener(v -> {
                btnSyncNow.setText("Đang đồng bộ...");
                btnSyncNow.setEnabled(false);
                PlaylistManager.getInstance(context).syncWithCloud(() -> {
                    btnSyncNow.setText("Đã đồng bộ mới nhất!");
                    btnSyncNow.setEnabled(true);
                    Toast.makeText(context, "Đã đồng bộ toàn bộ dữ liệu với Cloud!", Toast.LENGTH_SHORT).show();
                });
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        btnSignOut.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản này?")
                    .setPositiveButton("Đăng xuất", (d, w) -> {
                        AuthManager.getInstance().signOut();
                        dialog.dismiss();
                        Toast.makeText(context, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
                        if (onLoggedOutCallback != null) onLoggedOutCallback.run();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        if (btnDeleteAccount != null) {
            if (AuthManager.getInstance().isAnonymous()) {
                btnDeleteAccount.setText("Xóa dữ liệu khách");
                btnDeleteAccount.setOnClickListener(v -> {
                    new AlertDialog.Builder(context)
                            .setTitle("Xóa dữ liệu khách")
                            .setMessage("Bạn có chắc chắn muốn xóa toàn bộ playlist và bài hát yêu thích trên thiết bị này không?")
                            .setPositiveButton("Xóa dữ liệu", (d, w) -> {
                                PlaylistManager.getInstance(context).deleteUserData("guest", () -> {
                                    AuthManager.getInstance().signOut();
                                    dialog.dismiss();
                                    Toast.makeText(context, "Đã xóa toàn bộ dữ liệu khách", Toast.LENGTH_SHORT).show();
                                    if (onLoggedOutCallback != null) onLoggedOutCallback.run();
                                });
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            } else {
                btnDeleteAccount.setText("Xóa tài khoản vĩnh viễn");
                btnDeleteAccount.setOnClickListener(v -> {
                    new AlertDialog.Builder(context)
                            .setTitle("Xác nhận xóa tài khoản")
                            .setMessage("CẢNH BÁO: Hành động này sẽ xóa vĩnh viễn tài khoản của bạn cùng toàn bộ Playlist và Bài hát yêu thích trên đám mây Firebase.\n\nDữ liệu sẽ bị xóa sạch và không thể khôi phục. Bạn có chắc chắn muốn tiếp tục?")
                            .setPositiveButton("Xóa vĩnh viễn", (d, w) -> {
                                AlertDialog progress = new AlertDialog.Builder(context)
                                        .setMessage("Đang xóa tài khoản và dọn dẹp dữ liệu...")
                                        .setCancelable(false)
                                        .create();
                                progress.show();

                                String userId = AuthManager.getInstance().getUserId();
                                // 1. Xóa toàn bộ dữ liệu người dùng trên Firebase Cloud Firestore
                                FirebaseSyncManager.getInstance().deleteAllUserDataFromCloud(userId, () -> {
                                    // 2. Xóa toàn bộ dữ liệu cục bộ trên máy
                                    PlaylistManager.getInstance(context).deleteUserData(userId, () -> {
                                        // 3. Xóa tài khoản đăng nhập trên Firebase Authentication
                                        AuthManager.getInstance().deleteAccount(new AuthManager.AuthCallback() {
                                            @Override
                                            public void onSuccess(FirebaseUser user) {
                                                progress.dismiss();
                                                dialog.dismiss();
                                                Toast.makeText(context, "Đã xóa vĩnh viễn tài khoản và toàn bộ dữ liệu", Toast.LENGTH_LONG).show();
                                                if (onLoggedOutCallback != null) onLoggedOutCallback.run();
                                            }

                                            @Override
                                            public void onError(String errorMessage) {
                                                progress.dismiss();
                                                new AlertDialog.Builder(context)
                                                        .setTitle("Không thể xóa tài khoản")
                                                        .setMessage(errorMessage)
                                                        .setPositiveButton("Đã hiểu", null)
                                                        .show();
                                            }
                                        });
                                    });
                                });
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            }
        }

        dialog.show();
    }

    public static void showGuestPrompt(Context context, String actionMessage, Runnable onLoginSuccess) {
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle("Yêu cầu đăng nhập")
                .setMessage(actionMessage != null ? actionMessage : "Vui lòng đăng nhập để lưu trữ bài hát và tự động đồng bộ khi chuyển sang máy khác.")
                .setPositiveButton("Đăng nhập ngay", (d, w) -> showAuthDialog(context, onLoginSuccess))
                .setNegativeButton("Để sau", null)
                .show();
    }
}
