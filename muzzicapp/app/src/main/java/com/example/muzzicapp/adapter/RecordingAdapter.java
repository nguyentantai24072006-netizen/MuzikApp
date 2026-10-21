package com.example.muzzicapp.adapter;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.muzzicapp.R;
import com.example.muzzicapp.RecordingManager;
import com.example.muzzicapp.model.RecordingItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordingAdapter extends RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder> {

    private final List<RecordingItem> items = new ArrayList<>();
    private final Context context;

    private MediaPlayer mediaPlayer;
    private String currentlyPlayingId = null;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private RecordingViewHolder activeViewHolder;

    public RecordingAdapter(Context context, List<RecordingItem> initialItems) {
        this.context = context;
        if (initialItems != null) {
            this.items.addAll(initialItems);
        }
    }

    public void submitList(List<RecordingItem> newItems) {
        stopPlayback();
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recording, parent, false);
        return new RecordingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingViewHolder holder, int position) {
        RecordingItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void release() {
        stopPlayback();
    }

    private void stopPlayback() {
        progressHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        currentlyPlayingId = null;
        if (activeViewHolder != null) {
            activeViewHolder.btnPlay.setImageResource(R.drawable.ic_play);
            activeViewHolder.progressLayout.setVisibility(View.GONE);
            activeViewHolder = null;
        }
    }

    class RecordingViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvSubtitle;
        final ImageView btnPlay;
        final ImageView btnShare;
        final ImageView btnDelete;
        final LinearLayout progressLayout;
        final SeekBar seekBar;
        final TextView tvCurrentTime;
        final TextView tvTotalTime;

        RecordingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.item_rec_title);
            tvSubtitle = itemView.findViewById(R.id.item_rec_subtitle);
            btnPlay = itemView.findViewById(R.id.item_rec_btn_play);
            btnShare = itemView.findViewById(R.id.item_rec_btn_share);
            btnDelete = itemView.findViewById(R.id.item_rec_btn_delete);
            progressLayout = itemView.findViewById(R.id.item_rec_progress_layout);
            seekBar = itemView.findViewById(R.id.item_rec_seekbar);
            tvCurrentTime = itemView.findViewById(R.id.item_rec_current_time);
            tvTotalTime = itemView.findViewById(R.id.item_rec_total_time);
        }

        void bind(RecordingItem item) {
            tvTitle.setText(item.getTitle() != null ? item.getTitle() : "Bản thu âm");
            String info = item.getFormattedDuration() + " • " + item.getFormattedDate();
            tvSubtitle.setText(info);

            boolean isCurrent = item.getId().equals(currentlyPlayingId);
            if (isCurrent && mediaPlayer != null && mediaPlayer.isPlaying()) {
                btnPlay.setImageResource(R.drawable.ic_pause);
                progressLayout.setVisibility(View.VISIBLE);
                activeViewHolder = this;
            } else {
                btnPlay.setImageResource(R.drawable.ic_play);
                progressLayout.setVisibility(View.GONE);
            }

            btnPlay.setOnClickListener(v -> togglePlayback(item, this));
            itemView.setOnClickListener(v -> togglePlayback(item, this));

            btnShare.setOnClickListener(v -> RecordingManager.shareRecording(context, item));

            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Xóa bản thu âm")
                        .setMessage("Bạn có chắc chắn muốn xóa bản thu âm \"" + item.getTitle() + "\" này?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            if (item.getId().equals(currentlyPlayingId)) {
                                stopPlayback();
                            }
                            RecordingManager.getInstance(context).deleteRecording(item);
                            int pos = getAdapterPosition();
                            if (pos != RecyclerView.NO_POSITION && pos < items.size()) {
                                items.remove(pos);
                                notifyItemRemoved(pos);
                            }
                            Toast.makeText(context, "Đã xóa bản thu âm", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null && item.getId().equals(currentlyPlayingId)) {
                        mediaPlayer.seekTo(progress);
                        tvCurrentTime.setText(formatTime(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {}

                @Override
                public void onStopTrackingTouch(SeekBar sb) {}
            });
        }
    }

    private void togglePlayback(RecordingItem item, RecordingViewHolder holder) {
        if (item.getId().equals(currentlyPlayingId)) {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    holder.btnPlay.setImageResource(R.drawable.ic_play);
                } else {
                    mediaPlayer.start();
                    holder.btnPlay.setImageResource(R.drawable.ic_pause);
                    startProgressTracker(holder);
                }
            }
            return;
        }

        // Switching to new recording
        stopPlayback();

        File file = item.getFile();
        if (file == null || !file.exists()) {
            Toast.makeText(context, "File thu âm không tồn tại", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            currentlyPlayingId = item.getId();
            activeViewHolder = holder;

            holder.btnPlay.setImageResource(R.drawable.ic_pause);
            holder.progressLayout.setVisibility(View.VISIBLE);
            holder.seekBar.setMax(mediaPlayer.getDuration());
            holder.seekBar.setProgress(0);
            holder.tvCurrentTime.setText("00:00");
            holder.tvTotalTime.setText(formatTime(mediaPlayer.getDuration()));

            mediaPlayer.setOnCompletionListener(mp -> {
                holder.btnPlay.setImageResource(R.drawable.ic_play);
                holder.seekBar.setProgress(0);
                holder.tvCurrentTime.setText("00:00");
                stopPlayback();
            });

            startProgressTracker(holder);
        } catch (Exception e) {
            Toast.makeText(context, "Không thể phát bản thu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopPlayback();
        }
    }

    private void startProgressTracker(RecordingViewHolder holder) {
        progressHandler.removeCallbacksAndMessages(null);
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying() && holder == activeViewHolder) {
                    int pos = mediaPlayer.getCurrentPosition();
                    holder.seekBar.setProgress(pos);
                    holder.tvCurrentTime.setText(formatTime(pos));
                    progressHandler.postDelayed(this, 200);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private static String formatTime(int ms) {
        int sec = (ms / 1000) % 60;
        int min = (ms / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }
}
