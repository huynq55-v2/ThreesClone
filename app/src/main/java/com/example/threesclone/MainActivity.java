package com.example.threesclone;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // --- CẤU HÌNH ---
    private int numBits = 12;
    private int maxTurns = 10;
    private int swapCount = 3;

    // --- TRẠNG THÁI GAME ---
    private long maxCapacity, targetValue, currentSum = 0;
    private int currentTurn = 0;
    private ArrayList<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;

    // --- UI ---
    private AppCompatButton[] buttons;
    private AppCompatTextView hintText, statusText; // Chỉ giữ lại Hint và Turn info
    private GridLayout grid;
    private AppCompatEditText inputBits, inputMaxTurns, inputSwapCount;
    private AppCompatButton btnAutoPlay;

    // --- AI ENGINE ---
    private ZenSmartAI zenAI;
    private Handler autoPlayHandler = new Handler();
    private boolean isAutoPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(30, 40, 30, 30);

        // 1. SETTINGS PANEL
        LinearLayout settings = new LinearLayout(this);
        settings.setGravity(Gravity.CENTER);
        inputBits = createInput("12");
        inputMaxTurns = createInput("10");
        inputSwapCount = createInput("3");

        AppCompatButton btnStart = new AppCompatButton(this);
        btnStart.setText("RESET");
        setupButtonStyle(btnStart, Color.rgb(0, 100, 0));
        btnStart.setOnClickListener(v -> {
            stopAutoPlay();
            startNewGame();
        });

        addSettingItem(settings, "Bit:", inputBits);
        addSettingItem(settings, "Turn:", inputMaxTurns);
        addSettingItem(settings, "Swap:", inputSwapCount);
        settings.addView(btnStart);
        root.addView(settings);

        // 2. AI CONTROL
        LinearLayout aiPanel = new LinearLayout(this);
        aiPanel.setGravity(Gravity.CENTER);
        aiPanel.setPadding(0, 20, 0, 20);

        btnAutoPlay = new AppCompatButton(this);
        btnAutoPlay.setText("🤖 AUTO PLAY");
        setupButtonStyle(btnAutoPlay, Color.rgb(0, 0, 150));
        btnAutoPlay.setOnClickListener(v -> toggleAutoPlay());

        aiPanel.addView(btnAutoPlay);
        root.addView(aiPanel);

        // 3. ORACLE HINT (TRUNG TÂM CỦA GAME)
        hintText = new AppCompatTextView(this);
        hintText.setTextColor(Color.YELLOW);
        hintText.setGravity(Gravity.CENTER);
        hintText.setTextSize(24); // To rõ ràng
        hintText.setTypeface(null, android.graphics.Typeface.BOLD);
        hintText.setPadding(0, 20, 0, 20);
        root.addView(hintText);

        statusText = new AppCompatTextView(this);
        statusText.setTextColor(Color.CYAN);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(16);
        root.addView(statusText);

        // 4. GRID
        grid = new GridLayout(this);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        startNewGame();
    }

    // --- GAME LOGIC ---

    private void startNewGame() {
        try {
            numBits = Integer.parseInt(inputBits.getText().toString());
            maxTurns = Integer.parseInt(inputMaxTurns.getText().toString());
            swapCount = Integer.parseInt(inputSwapCount.getText().toString());
        } catch (Exception e) { numBits = 12; maxTurns = 10; swapCount = 3; }

        if (numBits > 31) numBits = 31;
        if (swapCount > numBits) swapCount = numBits;

        // Init Logic
        maxCapacity = (long) Math.pow(2, numBits) - 1;
        targetValue = Math.abs(new Random().nextLong()) % maxCapacity;
        if (targetValue == 0) targetValue = 1;

        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        buttonStates = new boolean[numBits];
        buttons = new AppCompatButton[numBits];
        currentTurn = 0;
        currentSum = 0;

        // Init AI
        zenAI = new ZenSmartAI(numBits);

        // Setup UI
        grid.removeAllViews();
        grid.setColumnCount(numBits > 16 ? 6 : 4);
        for (int i = 0; i < numBits; i++) {
            final int index = i;
            buttons[i] = new AppCompatButton(this);
            buttons[i].setText("?");
            buttons[i].setTextSize(18);
            updateButtonVisual(index);
            buttons[i].setOnClickListener(v -> onButtonClick(index, true));
            grid.addView(buttons[i]);
        }
        updateUI();
    }

    private void onButtonClick(int index, boolean isManual) {
        if (isAutoPlaying && isManual) stopAutoPlay();

        long oldSum = currentSum;
        boolean isTurningOn = !buttonStates[index];
        buttonStates[index] = isTurningOn;

        if (isTurningOn) currentTurn++;

        // Tính toán lại tổng (ẩn)
        currentSum = 0;
        for (int i = 0; i < numBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);

        // *** AI HỌC ***
        zenAI.observe(index, oldSum, currentSum);

        updateButtonVisual(index);

        // Check Win Logic
        if (currentSum == targetValue) {
            updateUI();
            stopAutoPlay();
            showWin();
            return;
        }

        // Check Swap Logic
        if (currentTurn >= maxTurns) {
            triggerStealthSwap();
        }

        updateUI();
    }

    private void triggerStealthSwap() {
        Toast.makeText(this, "⚠ SWAP! Mọi thứ đã đảo lộn!", Toast.LENGTH_SHORT).show();

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < numBits; i++) indices.add(i);
        Collections.shuffle(indices);
        List<Integer> swapIndices = indices.subList(0, swapCount);

        long firstVal = realValues.get(swapIndices.get(0));
        for (int i = 0; i < swapCount - 1; i++) {
            realValues.set(swapIndices.get(i), realValues.get(swapIndices.get(i + 1)));
        }
        realValues.set(swapIndices.get(swapCount - 1), firstVal);

        // Reset
        currentTurn = 0;
        currentSum = 0;
        for (int i = 0; i < numBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);

        // AI Quên sạch
        zenAI.resetMemory();

        statusText.setText("CẤU TRÚC ĐÃ ĐẢO LỘN!");
        updateUI();
    }

    // --- AI INTERACTION ---

    private void toggleAutoPlay() {
        if (isAutoPlaying) stopAutoPlay();
        else startAutoPlay();
    }

    private void startAutoPlay() {
        isAutoPlaying = true;
        btnAutoPlay.setText("🛑 DỪNG AUTO");
        btnAutoPlay.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        autoPlayHandler.post(autoPlayRunnable);
    }

    private void stopAutoPlay() {
        isAutoPlaying = false;
        btnAutoPlay.setText("🤖 AUTO PLAY");
        btnAutoPlay.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(0, 0, 150)));
        autoPlayHandler.removeCallbacks(autoPlayRunnable);
    }

    private Runnable autoPlayRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoPlaying) return;
            
            // AI nghĩ
            int bestMove = zenAI.getBestMove(currentSum, targetValue, buttonStates, maxTurns - currentTurn);

            if (bestMove != -1) {
                onButtonClick(bestMove, false);
                autoPlayHandler.postDelayed(this, 500); // Tốc độ chơi
            } else {
                stopAutoPlay(); // Bó tay
            }
        }
    };

    // --- UI HELPERS ---

    private void updateUI() {
        // Chỉ hiện Manh mối Bit
        long diff = targetValue ^ currentSum; // XOR để tìm bit lệch
        int bitsNeeded = Long.bitCount(diff);
        
        // Phân tích chi tiết để hiển thị đẹp
        int needOn = 0, needOff = 0;
        for(int i=0; i<numBits; i++) {
             // Logic ẩn: Nếu target có bit này mà current chưa có -> Cần Bật
             // Nhưng user/AI không biết bit này nằm ở nút nào, chỉ biết tổng số lượng
        }
        
        // Vì ta không biết nút nào chứa bit nào (visual), ta chỉ đếm số lượng bit lệch của GIÁ TRỊ
        // Hàm bitCount(diff) cho biết chính xác tổng số hành động cần thiết nếu ta biết hết mọi thứ.
        // Tuy nhiên để User dễ chơi, ta hiển thị: Cần Bật bao nhiêu bit, Cần Tắt bao nhiêu bit
        
        long valCheck = 1;
        int onCount = 0;
        int offCount = 0;
        for(int i=0; i<numBits; i++) {
            boolean targetHas = (targetValue & valCheck) != 0;
            boolean currentHas = (currentSum & valCheck) != 0;
            
            if (targetHas && !currentHas) onCount++; // Cần bật 1 bit giá trị này
            if (!targetHas && currentHas) offCount++; // Cần tắt 1 bit giá trị này
            
            valCheck *= 2;
        }

        if (bitsNeeded == 0) {
            hintText.setText("CÂN BẰNG HOÀN HẢO!");
            hintText.setTextColor(Color.GREEN);
        } else {
            hintText.setText("Cần BẬT: " + onCount + " | Cần TẮT: " + offCount);
            hintText.setTextColor(Color.YELLOW);
        }

        if (!statusText.getText().toString().equals("CẤU TRÚC ĐÃ ĐẢO LỘN!")) {
            statusText.setText("Lượt: " + currentTurn + " / " + maxTurns);
        }
        statusText.setTextColor(currentTurn >= maxTurns - 2 ? Color.RED : Color.CYAN);
    }

    private void updateButtonVisual(int index) {
        if (buttonStates[index]) {
            buttons[index].setBackgroundTintList(ColorStateList.valueOf(Color.YELLOW));
            buttons[index].setTextColor(Color.BLACK); buttons[index].setText("ON");
        } else {
            buttons[index].setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
            buttons[index].setTextColor(Color.GRAY); buttons[index].setText("?");
        }
    }

    private void setupButtonStyle(AppCompatButton btn, int color) {
        ViewCompat.setBackgroundTintList(btn, ColorStateList.valueOf(color));
        btn.setTextColor(Color.WHITE);
        btn.setPadding(30, 0, 30, 0);
    }

    private AppCompatEditText createInput(String def) {
        AppCompatEditText et = new AppCompatEditText(this);
        et.setText(def); et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(Color.WHITE); et.setMinWidth(70);
        return et;
    }

    private void addSettingItem(LinearLayout parent, String label, AppCompatEditText et) {
        AppCompatTextView tv = new AppCompatTextView(this);
        tv.setText(label); tv.setTextColor(Color.GRAY);
        parent.addView(tv); parent.addView(et);
    }

    private void showWin() {
        new AlertDialog.Builder(this).setTitle("CHIẾN THẮNG").setMessage("Bạn đã giải mã thành công!")
                .setPositiveButton("Chơi Lại", (d,w) -> startNewGame()).setCancelable(false).show();
    }

    // ==========================================
    // AI LOGIC: HAMMING DISTANCE OPTIMIZER
    // ==========================================
    public static class ZenSmartAI {
        private int numBits;
        private long[] memory;       
        private boolean[] knownMask; 
        private List<Long> allPossibleValues;

        public ZenSmartAI(int numBits) {
            this.numBits = numBits;
            this.memory = new long[numBits];
            this.knownMask = new boolean[numBits];
            this.allPossibleValues = new ArrayList<>();
            long val = 1;
            for (int i = 0; i < numBits; i++) {
                allPossibleValues.add(val);
                val *= 2;
            }
        }

        public void observe(int index, long oldSum, long newSum) {
            long diff = Math.abs(newSum - oldSum);
            if (diff > 0) {
                memory[index] = diff;
                knownMask[index] = true;
            }
        }

        public void resetMemory() {
            Arrays.fill(memory, 0);
            Arrays.fill(knownMask, false);
        }

        public int getBestMove(long currentSum, long target, boolean[] currentStates, int turnsLeft) {
            int bestMove = -1;
            double bestScore = Double.NEGATIVE_INFINITY; 
            int depth = 3;

            List<Long> hiddenValues = getHiddenValues();

            for (int i = 0; i < numBits; i++) {
                boolean isTurningOn = !currentStates[i];
                double expectedScore;

                if (knownMask[i]) {
                    // Branch: Known (Deterministic)
                    long val = memory[i];
                    long nextSum = isTurningOn ? (currentSum + val) : (currentSum - val);
                    int nextTurns = isTurningOn ? turnsLeft - 1 : turnsLeft;
                    expectedScore = runRecursion(nextSum, target, nextTurns, depth - 1, hiddenValues);
                } else {
                    // Branch: Unknown (Chance Node)
                    expectedScore = calculateChanceNode(currentSum, target, isTurningOn, turnsLeft, hiddenValues);
                }

                // Noise để tránh bị kẹt nếu điểm bằng nhau
                expectedScore += Math.random() * 0.05;

                if (expectedScore > bestScore) {
                    bestScore = expectedScore;
                    bestMove = i;
                }
            }
            return bestMove;
        }

        // Đệ quy
        private double runRecursion(long currSum, long target, int turns, int depth, List<Long> hiddenValues) {
            // Mục tiêu: Score = 0 (Hamming distance = 0)
            if (currSum == target) return 0;
            if (depth == 0 || turns <= 0) return calculateHammingScore(currSum, target);

            double maxScore = Double.NEGATIVE_INFINITY;
            boolean hasKnownMoves = false;

            // Pruning: Chỉ xét các nhánh đã biết
            for (int i = 0; i < numBits; i++) {
                if (knownMask[i]) {
                    hasKnownMoves = true;
                    // Giả sử chỉ BẬT để test tiềm năng (Simplification)
                    long nextSum = currSum + memory[i]; 
                    double score = runRecursion(nextSum, target, turns - 1, depth - 1, hiddenValues);
                    if (score > maxScore) maxScore = score;
                }
            }

            if (!hasKnownMoves) return calculateHammingScore(currSum, target);
            return maxScore;
        }

        // Chance Node: Trung bình cộng các khả năng
        private double calculateChanceNode(long currSum, long target, boolean isTurningOn, int turns, List<Long> hiddenValues) {
            if (hiddenValues.isEmpty()) return calculateHammingScore(currSum, target);

            double totalScore = 0;
            for (Long val : hiddenValues) {
                long nextSum = isTurningOn ? (currSum + val) : (currSum - val);
                // Depth 0 evaluation for chance nodes
                totalScore += calculateHammingScore(nextSum, target);
            }
            return totalScore / hiddenValues.size();
        }

        // --- HÀM MỤC TIÊU: ÂM CỦA SỐ BIT LỆCH ---
        private double calculateHammingScore(long currentSum, long target) {
            long diff = currentSum ^ target;
            int bitsNeeded = Long.bitCount(diff);
            return -bitsNeeded; // Càng gần 0 càng tốt
        }

        private List<Long> getHiddenValues() {
            List<Long> list = new ArrayList<>(allPossibleValues);
            for (int i = 0; i < numBits; i++) {
                if (knownMask[i]) list.remove(memory[i]);
            }
            return list;
        }
    }
}
