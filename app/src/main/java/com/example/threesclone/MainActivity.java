package com.example.threesclone;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.io.OutputStream;
import android.widget.EditText;
import android.os.Handler;

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
    
    private int cellSize;
    private boolean hasTrainedThisGame = false;
    
    // Brain management
    private ActivityResultLauncher<String> brainSaveLauncher;
    private ActivityResultLauncher<String[]> brainLoadLauncher;
    
    // AI Auto-Solve Mode
    private boolean aiModeEnabled = false;
    private Handler aiHandler = new Handler();
    private Button btnAI;
    private static final int AI_MOVE_DELAY_MS = 300;
    private static final int AUTO_RESET_DELAY_MS = 3000;

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

        // Calculate Cell Size
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        int paddingRootPx = (int) (16 * 2 * density);
        int paddingGridPx = (int) (8 * 2 * density);
        int marginPx = 8 * 2 * 4; 
        cellSize = (screenWidth - paddingRootPx - paddingGridPx - marginPx) / 4; 

        gestureDetector = new GestureDetector(this, new SwipeListener());
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        btnReset.setOnClickListener(v -> startNewGame());

        // --- AI MODE TOGGLE ---
        btnAI = findViewById(R.id.btnAI);
        btnAI.setOnClickListener(v -> toggleAIMode());

        // --- BRAIN MANAGEMENT BUTTONS ---
        setupBrainButtons();

        // --- HINT CLICK: Show Best Move ---
        layoutHints.setOnClickListener(v -> showBestMoveHint());

        startNewGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop AI execution and remove all pending messages/callbacks to prevent memory leaks
        if (aiHandler != null) {
            aiHandler.removeCallbacksAndMessages(null);
        }
    }

    private void toggleAIMode() {
        aiModeEnabled = !aiModeEnabled;
        if (aiModeEnabled) {
            btnAI.setText("🤖 AI ON");
            btnAI.setBackgroundColor(Color.parseColor("#4CAF50"));
            scheduleNextAIMove();
        } else {
            btnAI.setText("🤖 AI");
            btnAI.setBackgroundColor(Color.LTGRAY);
            aiHandler.removeCallbacksAndMessages(null);
        }
    }

    private void scheduleNextAIMove() {
        if (!aiModeEnabled || game.gameOver) return;
        aiHandler.postDelayed(this::performAIMove, AI_MOVE_DELAY_MS);
    }

    private void performAIMove() {
        if (!aiModeEnabled || game.gameOver) return;
        
        Direction bestDir = game.getBestMove();
        if (bestDir != null) {
            float phiOld = game.calculatePotential();
            boolean moved = game.move(bestDir);
            if (moved) {
                float phiNew = game.calculatePotential();
                float reward = game.calculateMoveReward(phiOld, phiNew);
                showReward(reward);
                updateUI();
            }
        }
        
        if (game.gameOver) {
            // Auto-reset after 3 seconds
            aiHandler.postDelayed(this::autoResetForAI, AUTO_RESET_DELAY_MS);
        } else {
            scheduleNextAIMove();
        }
    }

    private void autoResetForAI() {
        if (aiModeEnabled) {
            startNewGame();
            scheduleNextAIMove();
        }
    }

    private void showBestMoveHint() {
        if (game.gameOver) return;
        
        Direction best = game.getBestMove();
        if (best == null) {
            Toast.makeText(this, "No valid moves!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String arrow;
        switch (best) {
            case UP: arrow = "⬆️ UP"; break;
            case DOWN: arrow = "⬇️ DOWN"; break;
            case LEFT: arrow = "⬅️ LEFT"; break;
            case RIGHT: arrow = "➡️ RIGHT"; break;
            default: arrow = "?"; break;
        }
        
        Toast.makeText(this, "Best: " + arrow, Toast.LENGTH_SHORT).show();
    }

    private void setupBrainButtons() {
        Button btnSaveModel = findViewById(R.id.btnSaveModel);
        Button btnLoadModel = findViewById(R.id.btnLoadModel);
        Button btnResetModel = findViewById(R.id.btnResetModel);

        // --- BRAIN SAVE LAUNCHER (Export to file) ---
        brainSaveLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),
            uri -> {
                if (uri != null) {
                    saveBrainToUri(uri);
                }
            }
        );

        btnSaveModel.setOnClickListener(v -> {
            brainSaveLauncher.launch("brain_" + System.currentTimeMillis() + ".dat");
        });

        // --- BRAIN LOAD LAUNCHER (Import from file) ---
        brainLoadLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    loadBrainFromUri(uri);
                }
            }
        );

        btnLoadModel.setOnClickListener(v -> {
            brainLoadLauncher.launch(new String[]{"application/octet-stream", "*/*"});
        });

        btnResetModel.setOnClickListener(v -> showResetConfirmationDialog());
    }

    private void saveBrainToUri(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            game.brain.exportToBinary(os);
            os.close();
            Toast.makeText(this, "💾 Brain exported (Rust format)!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadBrainFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            game.brain.loadFromBinary(is);
            is.close();
            game.saveBrain(); // Save to internal storage as well
            updateUI();
            Toast.makeText(this, "📂 Brain imported (Rust format)!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showResetConfirmationDialog() {
        EditText input = new EditText(this);
        input.setHint("Type 'accept' to confirm");
        
        new AlertDialog.Builder(this)
            .setTitle("⚠️ Reset Brain?")
            .setMessage("This will DELETE ALL learned knowledge from the AI.\n\nType 'accept' to confirm:")
            .setView(input)
            .setPositiveButton("Confirm", (dialog, which) -> {
                String text = input.getText().toString().trim();
                if (text.equalsIgnoreCase("accept")) {
                    game.resetAllBrains(); // Reset both Value and Policy brains
                    updateUI();
                    Toast.makeText(this, "🗑️ All brains reset!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "❌ Cancelled - did not type 'accept'", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startNewGame() {
        game = new Game(this);
        hasTrainedThisGame = false;
        
        tvGameOver.setVisibility(View.GONE);
        tvGameOver.setTextColor(Color.RED);
        tvReward.setVisibility(View.GONE);
        updateUI();
    }

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
            // 1. Hỏi AI xem bàn cờ cũ ngon cỡ nào
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
                // 2. Hỏi AI xem bàn cờ mới ngon cỡ nào
                float phiNew = game.calculatePotential();
                
                // 3. Tính reward chênh lệch để hiển thị
                float reward = game.calculateMoveReward(phiOld, phiNew);
                
                showReward(reward);
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
                
                cell.setWidth(cellSize);
                cell.setHeight(cellSize);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(24);
                cell.setTypeface(null, android.graphics.Typeface.BOLD);
                
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.setMargins(8, 8, 8, 8);
                cell.setLayoutParams(params);

                if (!tile.isEmpty()) {
                    cell.setText(String.valueOf(tile.value));
                    cell.setBackground(createTileBackground(tile.value));
                    cell.setTextColor(getTileTextColor(tile.value));
                } else {
                    cell.setBackgroundColor(Color.parseColor("#CDC1B4"));
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
            if (!hasTrainedThisGame) {
                hasTrainedThisGame = true;
                game.trainOnHistory();
            }
            tvGameOver.setText("GAME OVER\nYour data has been integrated into the brain");
            tvGameOver.setVisibility(View.VISIBLE);
        }
    }

    // --- Helpers (Giữ nguyên) ---
    private GradientDrawable createTileBackground(int value) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(15);
        int color;
        switch (value) {
            // Special tiles
            case 1: color = Color.parseColor("#5AC8FA"); break;  // Blue
            case 2: color = Color.parseColor("#FF6B6B"); break;  // Red-pink
            
            // Gradient: Yellow → Orange → Red → Purple (warm palette)
            case 3: color = Color.parseColor("#F5F5DC"); break;     // Cream white
            case 6: color = Color.parseColor("#FFE066"); break;     // Light yellow
            case 12: color = Color.parseColor("#FFD43B"); break;    // Yellow-orange
            case 24: color = Color.parseColor("#FFA94D"); break;    // Light orange
            case 48: color = Color.parseColor("#FF922B"); break;    // Orange
            case 96: color = Color.parseColor("#FF6B35"); break;    // Red-orange
            case 192: color = Color.parseColor("#F94144"); break;   // Red
            case 384: color = Color.parseColor("#E63946"); break;   // Dark red
            case 768: color = Color.parseColor("#D62828"); break;   // Deep red
            case 1536: color = Color.parseColor("#9D0208"); break;  // Crimson
            case 3072: color = Color.parseColor("#6A040F"); break;  // Dark crimson
            case 6144: color = Color.parseColor("#370617"); break;  // Near black
            default: color = Color.parseColor("#03071E"); break;    // Boss black
        }
        shape.setColor(color);
        if (value == 3) shape.setStroke(2, Color.LTGRAY);
        return shape;
    }
    
    private int getTileTextColor(int value) {
        // Dark text for light tiles, white for dark tiles
        if (value == 3 || value == 6 || value == 12) return Color.parseColor("#333333");
        return Color.WHITE;
    }

    private void showReward(float reward) {
        // Chỉ hiện nếu reward đáng kể để đỡ rối mắt
        if (Math.abs(reward) < 0.1) {
            tvReward.setVisibility(View.GONE);
            return;
        }
        tvReward.setText(String.format("%+.1f", reward));
        if (reward >= 0) tvReward.setTextColor(Color.parseColor("#4CAF50"));
        else tvReward.setTextColor(Color.parseColor("#F44336"));
        tvReward.setVisibility(View.VISIBLE);
    }

    // --- Audio & Haptics ---
    public float calculateFrequency(float reward) {
        if (reward <= 0) return 150f;
        float freq = BASE_FREQ + (150.0f * (float)Math.log(reward + 1));
        return Math.min(freq, MAX_FREQ);
    }
    
    private void playSound(float freq) {
        new Thread(() -> {
            try {
                int durationMs = 150; // Ngắn gọn hơn
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
                try { Thread.sleep(durationMs + 50); } catch (InterruptedException e) {}
                audioTrack.release();
            } catch (Exception e) {}
        }).start();
    }
}
