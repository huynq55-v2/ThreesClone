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
import android.widget.ProgressBar;
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
    private ProgressBar targetProgress, currentProgress;
    private AppCompatTextView infoText, statusText, hintText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputMaxTurns, inputSwapCount;
    private AppCompatButton btnAutoPlay; // Nút để AI tự chơi

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
        root.setPadding(20, 30, 20, 20);

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

        // 2. AI CONTROL PANEL
        LinearLayout aiPanel = new LinearLayout(this);
        aiPanel.setGravity(Gravity.CENTER);
        aiPanel.setPadding(0, 10, 0, 10);

        btnAutoPlay = new AppCompatButton(this);
        btnAutoPlay.setText("🤖 AUTO PLAY");
        setupButtonStyle(btnAutoPlay, Color.rgb(0, 0, 150));
        btnAutoPlay.setOnClickListener(v -> toggleAutoPlay());

        AppCompatButton btnHint = new AppCompatButton(this);
        btnHint.setText("💡 GỢI Ý 1 BƯỚC");
        setupButtonStyle(btnHint, Color.rgb(150, 100, 0));
        btnHint.setOnClickListener(v -> askAiOnce());

        aiPanel.addView(btnAutoPlay);
        aiPanel.addView(btnHint);
        root.addView(aiPanel);

        // 3. BARS & INFO
        targetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        targetProgress.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(targetProgress);

        currentProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        currentProgress.getProgressDrawable().setColorFilter(Color.YELLOW, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(currentProgress);

        infoText = new AppCompatTextView(this);
        infoText.setTextColor(Color.WHITE);
        infoText.setGravity(Gravity.CENTER);
        root.addView(infoText);

        hintText = new AppCompatTextView(this);
        hintText.setTextColor(Color.YELLOW);
        hintText.setGravity(Gravity.CENTER);
        hintText.setTextSize(13);
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

        // Init Values
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
            updateButtonVisual(index);
            buttons[i].setOnClickListener(v -> onButtonClick(index, true)); // true = manual click
            grid.addView(buttons[i]);
        }
        updateUI();
    }

    private void onButtonClick(int index, boolean isManual) {
        if (isAutoPlaying && isManual) stopAutoPlay(); // Người can thiệp thì dừng Auto

        long oldSum = currentSum;
        boolean isTurningOn = !buttonStates[index];
        buttonStates[index] = isTurningOn;

        if (isTurningOn) currentTurn++;

        // Tính toán Sum mới
        currentSum = 0;
        for (int i = 0; i < numBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);

        // *** AI HỌC NGAY LẬP TỨC ***
        zenAI.observe(index, oldSum, currentSum);

        updateButtonVisual(index);

        if (currentSum == targetValue) {
            updateUI();
            stopAutoPlay();
            showWin();
            return;
        }

        if (currentTurn >= maxTurns) {
            triggerStealthSwap();
        }

        updateUI();
    }

    private void triggerStealthSwap() {
        Toast.makeText(this, "⚠ SWAP! (" + swapCount + " bit)", Toast.LENGTH_SHORT).show();

        // Logic Swap ngầm
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

        // *** AI BỊ XOÁ NÃO ***
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
            askAiOnce();
            // Tốc độ chơi của AI: 600ms mỗi nước
            autoPlayHandler.postDelayed(this, 600);
        }
    };

    private void askAiOnce() {
        // AI suy nghĩ
        int bestMove = zenAI.getBestMove(currentSum, targetValue, buttonStates, maxTurns - currentTurn);

        if (bestMove != -1) {
            // AI thực hiện bấm nút (giả lập click)
            onButtonClick(bestMove, false);
        } else {
            Toast.makeText(this, "AI bó tay (đang Swap?)", Toast.LENGTH_SHORT).show();
        }
    }

    // --- UI HELPERS ---

    private void updateUI() {
        targetProgress.setMax(1000); currentProgress.setMax(1000);
        targetProgress.setProgress((int) ((targetValue * 1000) / maxCapacity));
        currentProgress.setProgress((int) ((currentSum * 1000) / maxCapacity));

        infoText.setText("Target: " + targetValue + " | Current: " + currentSum);
        if (!statusText.getText().toString().equals("CẤU TRÚC ĐÃ ĐẢO LỘN!")) {
            statusText.setText("Lượt: " + currentTurn + " / " + maxTurns);
        }
        statusText.setTextColor(currentTurn >= maxTurns - 2 ? Color.RED : Color.CYAN);
        
        // Oracle Hint Text
        updateHintText();
    }
    
    private void updateHintText() {
        int needOn = 0, needOff = 0;
        for(int i=0; i<numBits; i++) {
            long val = realValues.get(i);
            boolean should = (targetValue & val) != 0;
            if(should && !buttonStates[i]) needOn++;
            else if(!should && buttonStates[i]) needOff++;
        }
        hintText.setText("Oracle: Bật " + needOn + " | Tắt " + needOff);
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
        btn.setPadding(20, 0, 20, 0);
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
        new AlertDialog.Builder(this).setTitle("WIN").setMessage("AI đã giải xong!")
                .setPositiveButton("OK", null).show();
    }

    // ==========================================
    // INNER CLASS: ZEN SMART AI (Logic Core)
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

        // 1. HỌC (Instant Mapping)
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

        // 2. SUY LUẬN (EXPECTIMAX 3-PLY)
        public int getBestMove(long currentSum, long target, boolean[] currentStates, int turnsLeft) {
            int bestMove = -1;
            double minDeviation = Double.MAX_VALUE;
            int depth = 3;

            // Tính tập giá trị còn ẩn để dùng cho Chance Node
            List<Long> hiddenValues = getHiddenValues();

            // Loop Max Node
            for (int i = 0; i < numBits; i++) {
                boolean isTurningOn = !currentStates[i];
                double expectedDev;

                // Branch 1: KNOWN NODE (Deterministic)
                if (knownMask[i]) {
                    long val = memory[i];
                    long nextSum = isTurningOn ? (currentSum + val) : (currentSum - val);
                    int nextTurns = isTurningOn ? turnsLeft - 1 : turnsLeft;
                    expectedDev = runExpectimax(nextSum, target, nextTurns, depth - 1, hiddenValues);
                }
                // Branch 2: UNKNOWN NODE (Chance Node)
                else {
                    expectedDev = calculateChanceNode(currentSum, target, isTurningOn, turnsLeft, depth, hiddenValues);
                }

                // Thêm một chút nhiễu cực nhỏ để break tie nếu các nước đi ngang nhau
                expectedDev += Math.random() * 0.1;

                if (expectedDev < minDeviation) {
                    minDeviation = expectedDev;
                    bestMove = i;
                }
            }
            return bestMove;
        }

        // Recursive Function
        private double runExpectimax(long currSum, long target, int turns, int depth, List<Long> hiddenValues) {
            // Base Cases
            if (depth == 0 || currSum == target) return Math.abs(target - currSum);
            if (turns <= 0) return Math.abs(target - currSum); // Swap boundary, return current state deviation

            double bestDev = Double.MAX_VALUE;
            boolean hasMoves = false;

            // Trong đệ quy, để tối ưu hiệu năng, AI chỉ giả định đi tiếp vào các nút ĐÃ BIẾT
            // Vì đi vào nút chưa biết ở tầng sâu (simulation) sẽ làm bùng nổ tính toán.
            // (Pruning: Chỉ xét Known Moves ở tầng sâu)
            for (int i = 0; i < numBits; i++) {
                if (knownMask[i]) {
                    hasMoves = true;
                    // Giả định trạng thái tối ưu: Nếu nút đang tắt thì bật để cộng, nếu bật thì tắt để trừ
                    // Ở đây đơn giản hóa: Giả sử luôn BẬT (cộng thêm) để check potential
                    // (Logic này có thể tinh chỉnh thêm nếu muốn chính xác tuyệt đối trạng thái ON/OFF)
                    long nextSum = currSum + memory[i]; 
                    double dev = runExpectimax(nextSum, target, turns - 1, depth - 1, hiddenValues);
                    bestDev = Math.min(bestDev, dev);
                }
            }

            if (!hasMoves) return Math.abs(target - currSum); // Hết đường biết -> Trả về hiện tại
            return bestDev;
        }

        // Chance Node Calculation
        private double calculateChanceNode(long currSum, long target, boolean isTurningOn, int turns, int depth, List<Long> hiddenValues) {
            if (hiddenValues.isEmpty()) return Math.abs(target - currSum);

            double totalDev = 0;
            // Duyệt qua tất cả khả năng giá trị của nút này
            for (Long val : hiddenValues) {
                long nextSum = isTurningOn ? (currSum + val) : (currSum - val);
                int nextTurns = isTurningOn ? turns - 1 : turns;

                // Để nhanh, ta không gọi đệ quy sâu ở Chance Node này mà tính độ lệch tức thì
                // (Depth = 0 approach for Chance Nodes to save CPU on mobile)
                totalDev += Math.abs(target - nextSum);
            }
            return totalDev / hiddenValues.size();
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
