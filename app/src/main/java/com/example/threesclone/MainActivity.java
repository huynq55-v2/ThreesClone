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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // --- CẤU HÌNH ---
    private int totalBits = 10;
    private int maxTurns = 20;
    private int swapCount = 3;
    private int aiDepth = 3;

    private long targetValue, currentSum = 0;
    private int currentTurn = 0;
    private List<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;
    private AppCompatTextView karmaText, statusText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputTurns, inputSwap, inputDepth;

    private boolean isAutoRunning = false;
    private Handler handler = new Handler();
    private Random rnd = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(20, 40, 20, 20);

        // --- BẢNG ĐIỀU KHIỂN ---
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        inputBits = createInput(String.valueOf(totalBits));
        inputTurns = createInput(String.valueOf(maxTurns));
        inputSwap = createInput(String.valueOf(swapCount));
        inputDepth = createInput(String.valueOf(aiDepth));

        AppCompatButton btnReset = new AppCompatButton(this);
        btnReset.setText("RESET");
        btnReset.setOnClickListener(v -> { stopAuto(); startNewGame(); });

        addControlItem(controls, "B:", inputBits);
        addControlItem(controls, "T:", inputTurns);
        addControlItem(controls, "S:", inputSwap);
        addControlItem(controls, "D:", inputDepth);
        controls.addView(btnReset);
        root.addView(controls);

        AppCompatButton btnAuto = new AppCompatButton(this);
        btnAuto.setText("🧘 EXPECTIMAX SOLVER");
        btnAuto.setOnClickListener(v -> toggleAuto());
        root.addView(btnAuto);

        karmaText = new AppCompatTextView(this);
        karmaText.setTextColor(Color.WHITE);
        karmaText.setTextSize(32);
        karmaText.setGravity(Gravity.CENTER);
        karmaText.setPadding(0, 20, 0, 20);
        root.addView(karmaText);

        statusText = new AppCompatTextView(this);
        statusText.setTextColor(Color.CYAN);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        grid = new GridLayout(this);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        startNewGame();
    }

    private void startNewGame() {
        try {
            totalBits = Integer.parseInt(inputBits.getText().toString());
            maxTurns = Integer.parseInt(inputTurns.getText().toString());
            swapCount = Integer.parseInt(inputSwap.getText().toString());
            aiDepth = Integer.parseInt(inputDepth.getText().toString());
        } catch (Exception e) { }

        // Khởi tạo Target Karma ngẫu nhiên < Total Bits
        int targetKarma = rnd.nextInt(totalBits - 1) + 1;
        realValues.clear();
        for (int i = 0; i < totalBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        targetValue = 0;
        for (int i = 0; i < targetKarma; i++) targetValue += realValues.get(i);
        Collections.shuffle(realValues);

        buttonStates = new boolean[totalBits];
        buttons = new AppCompatButton[totalBits];
        currentTurn = 0; currentSum = 0;

        grid.removeAllViews();
        grid.setColumnCount(4);
        for (int i = 0; i < totalBits; i++) {
            final int index = i;
            buttons[i] = new AppCompatButton(this);
            updateButtonVisual(i);
            buttons[i].setOnClickListener(v -> performMove(index));
            grid.addView(buttons[i]);
        }
        updateUI();
    }

    private void performMove(int index) {
        buttonStates[index] = !buttonStates[index];
        currentTurn++;
        currentSum = 0;
        for (int i = 0; i < totalBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);
        updateButtonVisual(index);
        updateUI();

        if (calculateKarma() == 0) {
            stopAuto();
            new AlertDialog.Builder(this).setTitle("GIÁC NGỘ").show();
        } else if (currentTurn >= maxTurns) {
            triggerSwap();
        }
    }

    private int calculateKarma() { return Long.bitCount(targetValue ^ currentSum); }

    private void triggerSwap() {
        Toast.makeText(this, "SWAP!", Toast.LENGTH_SHORT).show();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < totalBits; i++) idx.add(i);
        Collections.shuffle(idx);
        for (int i = 0; i < Math.min(swapCount, totalBits - 1); i++) {
            Collections.swap(realValues, idx.get(i), idx.get((i + 1) % swapCount));
        }
        currentTurn = 0;
        currentSum = 0;
        for (int i = 0; i < totalBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);
        updateUI();
    }

    private void toggleAuto() {
        isAutoRunning = !isAutoRunning;
        if (isAutoRunning) handler.post(autoRunnable);
        else stopAuto();
    }

    private void stopAuto() { isAutoRunning = false; handler.removeCallbacks(autoRunnable); }

    private Runnable autoRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoRunning) return;
            int move = getExpectimaxMove();
            if (move != -1) performMove(move);
            handler.postDelayed(this, 600);
        }
    };

    // --- LOGIC EXPECTIMAX THUẦN TÚY ---
    private int getExpectimaxMove() {
        int k = calculateKarma();
        List<Integer> onIdx = new ArrayList<>();
        List<Integer> offIdx = new ArrayList<>();
        for (int i = 0; i < totalBits; i++) {
            if (buttonStates[i]) onIdx.add(i); else offIdx.add(i);
        }

        // Khám phá 2 hướng: Bật (Switch OFF) hoặc Tắt (Switch ON)
        double evOn = Double.MAX_VALUE;
        double evOff = Double.MAX_VALUE;

        if (!onIdx.isEmpty()) {
            double pCorrect = calculateAverageP(k, onIdx.size(), offIdx.size(), false);
            evOn = simulate(k, onIdx.size(), offIdx.size(), pCorrect, aiDepth);
        }
        if (!offIdx.isEmpty()) {
            double pCorrect = calculateAverageP(k, onIdx.size(), offIdx.size(), true);
            evOff = simulate(k, onIdx.size(), offIdx.size(), pCorrect, aiDepth);
        }

        // Chọn hướng có EV thấp nhất và random nút trong hướng đó
        if (evOn < evOff && !onIdx.isEmpty()) {
            return onIdx.get(rnd.nextInt(onIdx.size()));
        } else if (!offIdx.isEmpty()) {
            return offIdx.get(rnd.nextInt(offIdx.size()));
        }
        return -1;
    }

    private double calculateAverageP(int k, int on, int off, boolean switchingOn) {
        double pTotal = 0; int scenarios = 0;
        for (int k_on = 0; k_on <= on; k_on++) {
            int k_off = k - k_on;
            if (k_off >= 0 && k_off <= off) {
                pTotal += switchingOn ? (double) k_off / off : (double) k_on / on;
                scenarios++;
            }
        }
        return (scenarios == 0) ? 0 : pTotal / scenarios;
    }

    // Giả lập cây Expectimax dựa trên chuỗi xác suất
    private double simulate(int k, int on, int off, double p, int depth) {
        if (k <= 0) return 0;
        if (depth == 0) return k;

        // Chance Node: Đúng (k-1) hoặc Sai (k+1)
        // Lưu ý: Sau mỗi bước, on/off thay đổi, k thay đổi -> p thay đổi ở tầng sau
        double nextP_Correct = (double)(k - 1) / totalBits; // Ước lượng nhanh cho tầng sâu
        double nextP_Wrong = (double)(k + 1) / totalBits;

        double branchCorrect = simulate(k - 1, on, off, nextP_Correct, depth - 1);
        double branchWrong = simulate(k + 1, on, off, nextP_Wrong, depth - 1);

        return (p * branchCorrect) + ((1 - p) * branchWrong);
    }

    private void updateUI() {
        karmaText.setText("KARMA: " + calculateKarma());
        statusText.setText("Turn: " + currentTurn + " / " + maxTurns);
    }

    private void updateButtonVisual(int i) {
        buttons[i].setBackgroundTintList(ColorStateList.valueOf(buttonStates[i] ? Color.YELLOW : Color.DKGRAY));
        buttons[i].setText(buttonStates[i] ? "ON" : "OFF");
    }

    private AppCompatEditText createInput(String def) {
        AppCompatEditText et = new AppCompatEditText(this);
        et.setText(def); et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(Color.WHITE); et.setMinWidth(60);
        return et;
    }

    private void addControlItem(LinearLayout p, String label, AppCompatEditText et) {
        AppCompatTextView tv = new AppCompatTextView(this);
        tv.setText(label); tv.setTextColor(Color.GRAY);
        p.addView(tv); p.addView(et);
    }
}
