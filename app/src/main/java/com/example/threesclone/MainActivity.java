package com.example.threesclone;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Game game;
    private GridLayout gridLayout;
    private TextView tvScore, tvGameOver, tvReward;
    private LinearLayout layoutHints;
    private GestureDetector gestureDetector;
    private Vibrator vibrator;
    
    // Audio Consts
    private static final float MAX_FREQ = 2000f;
    private static final float BASE_FREQ = 300f;
    
    // Kích thước ô trên màn hình (px)
    private int cellSize; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init Views
        gridLayout = findViewById(R.id.gridLayoutBoard);
        tvScore = findViewById(R.id.tvScore);
        tvGameOver = findViewById(R.id.tvGameOver);
        tvReward = findViewById(R.id.tvReward);
        layoutHints = findViewById(R.id.layoutHints);
        Button btnReset = findViewById(R.id.btnReset);

        // Tính toán kích thước ô dựa trên màn hình và padding thực tế
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        
        // Tổng khoảng cách ngang:
        // Root Padding: 16dp * 2
        // Grid Padding: 8dp * 2
        // Cell Margins: 8px * 2 * 4 (8px mỗi bên, 4 cột)
        int paddingRootPx = (int) (16 * 2 * density);
        int paddingGridPx = (int) (8 * 2 * density);
        int marginPx = 8 * 2 * 4; // 8px margin cứng
        
        cellSize = (screenWidth - paddingRootPx - paddingGridPx - marginPx) / 4; 

        // Gesture Detector cho Swipe
        gestureDetector = new GestureDetector(this, new SwipeListener());
        
        // Init Vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        btnReset.setOnClickListener(v -> startNewGame());

        startNewGame();
    }

    private void startNewGame() {
        game = new Game();
        tvGameOver.setVisibility(View.GONE);
        tvReward.setVisibility(View.GONE);
        updateUI();
    }

    // Nhận cảm ứng chạm để xử lý swipe
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        private static final int THRESHOLD = 100;
        private static final int VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diffY = e2.getY() - e1.getY();
            float diffX = e2.getX() - e1.getX();
            
            if (game.gameOver) return false;

            boolean moved = false;
            float phiOld = game.calculatePotential();

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > THRESHOLD && Math.abs(velocityX) > VELOCITY_THRESHOLD) {
                    if (diffX > 0) moved = game.move(Direction.RIGHT);
                    else moved = game.move(Direction.LEFT);
                }
            } else {
                if (Math.abs(diffY) > THRESHOLD && Math.abs(velocityY) > VELOCITY_THRESHOLD) {
                    if (diffY > 0) moved = game.move(Direction.DOWN);
                    else moved = game.move(Direction.UP);
                }
            }

            if (moved) {
                float phiNew = game.calculatePotential();
                float reward = game.calculateMoveReward(phiOld, phiNew);
                showReward(reward);
                
                // Feedback
                triggerHaptic(reward);
                float freq = calculateFrequency(reward);
                playSound(freq);
                
                updateUI();
            }
            return true;
        }
    }

    private void updateUI() {
        tvScore.setText("SCORE: " + game.score);

        // 1. Render Board
        gridLayout.removeAllViews();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                Tile tile = game.board[r][c];
                TextView cell = new TextView(this);
                
                // Style cho ô
                cell.setWidth(cellSize);
                cell.setHeight(cellSize);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(24);
                cell.setTypeface(null, android.graphics.Typeface.BOLD);
                
                // Margin
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.setMargins(8, 8, 8, 8);
                cell.setLayoutParams(params);

                if (!tile.isEmpty()) {
                    cell.setText(String.valueOf(tile.value));
                    cell.setBackground(createTileBackground(tile.value));
                    cell.setTextColor(getTileTextColor(tile.value));
                } else {
                    cell.setBackgroundColor(Color.parseColor("#CDC1B4")); // Màu ô trống
                }
                
                gridLayout.addView(cell);
            }
        }

        // 2. Render Hints
        layoutHints.removeAllViews();
        for (int val : game.hints) {
            TextView hintCell = new TextView(this);
            hintCell.setWidth(80);
            hintCell.setHeight(80);
            hintCell.setGravity(Gravity.CENTER);
            hintCell.setTextSize(14);
            hintCell.setText(String.valueOf(val));
            hintCell.setBackground(createTileBackground(val));
            hintCell.setTextColor(getTileTextColor(val));
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(80, 80);
            lp.setMargins(10, 0, 10, 0);
            hintCell.setLayoutParams(lp);
            
            layoutHints.addView(hintCell);
        }

        // 3. Check Game Over
        if (game.gameOver) {
            tvGameOver.setVisibility(View.VISIBLE);
        }
    }

    // --- Coloring Helpers ---
    private GradientDrawable createTileBackground(int value) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(15);
        
        int color;
        switch (value) {
            case 1: color = Color.parseColor("#66CCFF"); break; // Xanh dương
            case 2: color = Color.parseColor("#FF6666"); break; // Đỏ
            case 3: color = Color.parseColor("#FFFFFF"); break; // Trắng
            case 6: color = Color.parseColor("#F2B179"); break;
            case 12: color = Color.parseColor("#F59563"); break;
            case 24: color = Color.parseColor("#F67C5F"); break;
            case 48: color = Color.parseColor("#F65E3B"); break;
            case 96: color = Color.parseColor("#EDCF72"); break;
            case 192: color = Color.parseColor("#EDCC61"); break;
            case 384: color = Color.parseColor("#EDC850"); break;
            default: color = Color.parseColor("#EDC22E"); break; // Vàng đậm cho số to
        }
        shape.setColor(color);
        
        // Border cho Tile 3 trắng để dễ nhìn
        if (value == 3) {
            shape.setStroke(2, Color.LTGRAY);
        }
        return shape;
    }
    
    private int getTileTextColor(int value) {
        if (value == 3) return Color.BLACK;
        if (value <= 2) return Color.WHITE;
        return Color.WHITE;
    }
    private void showReward(float reward) {
        tvReward.setText(String.format("%+.2f", reward));
        if (reward >= 0) {
            tvReward.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            tvReward.setTextColor(Color.parseColor("#F44336")); // Red
        }
        tvReward.setVisibility(View.VISIBLE);
    }

    // --- Audio & Haptic Logic ---
    public float calculateFrequency(float reward) {
        if (reward <= 0) return 150f; // Reward âm/bằng 0: Tiếng "bụp" trầm thấp
        
        float freq = BASE_FREQ + (150.0f * (float)Math.log(reward + 1));
        return Math.min(freq, MAX_FREQ);
    }
    
    private void playSound(float freq) {
        new Thread(() -> {
            try {
                int durationMs = 200;
                int sampleRate = 44100;
                int numSamples = durationMs * sampleRate / 1000;
                double[] sample = new double[numSamples];
                byte[] generatedSnd = new byte[2 * numSamples];

                for (int i = 0; i < numSamples; ++i) {
                    sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freq));
                }

                int idx = 0;
                for (final double dVal : sample) {
                    final short val = (short) ((dVal * 32767));
                    generatedSnd[idx++] = (byte) (val & 0x00ff);
                    generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
                }

                AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                        AudioTrack.MODE_STATIC);
                audioTrack.write(generatedSnd, 0, generatedSnd.length);
                audioTrack.play();
                
                // Release after play
                try { Thread.sleep(durationMs + 100); } catch (InterruptedException e) {}
                audioTrack.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void triggerHaptic(float reward) {
        if (reward <= 0) return; 

        // 1. Tính thời lượng (tối đa 400ms)
        long duration = (long) (20 + 5 * Math.sqrt(reward));
        if (duration > 400) duration = 400;

        // 2. Tính cường độ (tối đa 255)
        int amplitude = (int) (50 + 30 * Math.log(reward + 1));
        if (amplitude > 255) amplitude = 255;

        // Thực hiện rung
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
        } else {
            vibrator.vibrate(duration);
        }
    }
}
