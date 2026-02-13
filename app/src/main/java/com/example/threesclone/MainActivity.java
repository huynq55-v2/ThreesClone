package com.example.threesclone;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.InputType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // --- CẤU HÌNH MẶC ĐỊNH ---
    private int totalBits = 12;
    private int maxTurnsBeforeSwap = 15;
    private int swapCount = 3;

    private long targetValue, currentSum = 0;
    private int currentTurn = 0;
    private List<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;
    private AppCompatTextView karmaText, statusText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputTurns, inputSwap;
    
    private boolean isAutoRunning = false;
    private Handler handler = new Handler();
    private Random rnd = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(30, 40, 30, 30);

        // --- PANEL ĐIỀU KHIỂN ---
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        inputBits = createInput(String.valueOf(totalBits));
        inputTurns = createInput(String.valueOf(maxTurnsBeforeSwap));
        inputSwap = createInput(String.valueOf(swapCount));

        AppCompatButton btnReset = new AppCompatButton(this);
        btnReset.setText("RESET");
        btnReset.setOnClickListener(v -> { stopAuto(); startNewGame(); });

        addControlItem(controls, "Bits:", inputBits);
        addControlItem(controls, "Turns:", inputTurns);
        addControlItem(controls, "Swap:", inputSwap);
        controls.addView(btnReset);
        root.addView(controls);

        AppCompatButton btnAuto = new AppCompatButton(this);
        btnAuto.setText("🤖 AUTO PROBABILITY");
        btnAuto.setOnClickListener(v -> toggleAuto());
        root.addView(btnAuto);

        // --- HIỂN THỊ ---
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
            maxTurnsBeforeSwap = Integer.parseInt(inputTurns.getText().toString());
            swapCount = Integer.parseInt(inputSwap.getText().toString());
        } catch (Exception e) { }

        // 1. Random số bit cần bật ban đầu (phải bé hơn Total Bits)
        int initialBitsToOn = rnd.nextInt(totalBits - 1) + 1; 

        realValues.clear();
        for (int i = 0; i < totalBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        // Tạo targetValue dựa trên số bit cần bật đã chọn
        targetValue = 0;
        for (int i = 0; i < initialBitsToOn; i++) {
            targetValue += realValues.get(i);
        }
        // Xáo trộn lại realValues để AI không biết vị trí các bit target
        Collections.shuffle(realValues);

        buttonStates = new boolean[totalBits];
        buttons = new AppCompatButton[totalBits];
        currentTurn = 0;
        currentSum = 0;

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
            new AlertDialog.Builder(this).setTitle("SOLVED").setMessage("Nghiệp đã hết!").show();
        } else if (currentTurn >= maxTurnsBeforeSwap) {
            triggerSwap();
        }
    }

    private int calculateKarma() {
        return Long.bitCount(targetValue ^ currentSum);
    }

    private void triggerSwap() {
        Toast.makeText(this, "SWAP!", Toast.LENGTH_SHORT).show();
        // Swap giá trị vật lý của các nút
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < totalBits; i++) idx.add(i);
        Collections.shuffle(idx);
        
        for (int i = 0; i < Math.min(swapCount, totalBits - 1); i++) {
            int a = idx.get(i);
            int b = idx.get((i + 1) % swapCount);
            Collections.swap(realValues, a, b);
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
            int move = getAiDecision();
            if (move != -1) performMove(move);
            handler.postDelayed(this, 500);
        }
    };

    private int getAiDecision() {
        int k = calculateKarma();
        List<Integer> onIdx = new ArrayList<>();
        List<Integer> offIdx = new ArrayList<>();
        for (int i = 0; i < totalBits; i++) {
            if (buttonStates[i]) onIdx.add(i); else offIdx.add(i);
        }

        int onCount = onIdx.size();
        int offCount = offIdx.size();

        // TÍNH XÁC SUẤT THUẦN TÚY (Dựa trên trung bình kịch bản)
        double pCorrectOn = 0; double pCorrectOff = 0;
        int scenarios = 0;

        for (int k_on = 0; k_on <= onCount; k_on++) {
            int k_off = k - k_on;
            if (k_off >= 0 && k_off <= offCount) {
                if (onCount > 0) pCorrectOn += (double) k_on / onCount;
                if (offCount > 0) pCorrectOff += (double) k_off / offCount;
                scenarios++;
            }
        }

        if (scenarios == 0) return -1;
        pCorrectOn /= scenarios;
        pCorrectOff /= scenarios;

        // CHỌN HƯỚNG VÀ RANDOM TRONG NHÓM
        if (pCorrectOn > pCorrectOff && !onIdx.isEmpty()) {
            return onIdx.get(rnd.nextInt(onIdx.size()));
        } else if (!offIdx.isEmpty()) {
            return offIdx.get(rnd.nextInt(offIdx.size()));
        }
        return -1;
    }

    private void updateUI() {
        karmaText.setText("KARMA: " + calculateKarma());
        statusText.setText("Turn: " + currentTurn + " / " + maxTurnsBeforeSwap);
    }

    private void updateButtonVisual(int i) {
        buttons[i].setBackgroundTintList(ColorStateList.valueOf(buttonStates[i] ? Color.YELLOW : Color.DKGRAY));
        buttons[i].setText(buttonStates[i] ? "ON" : "OFF");
    }

    private AppCompatEditText createInput(String def) {
        AppCompatEditText et = new AppCompatEditText(this);
        et.setText(def); et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(Color.WHITE); et.setMinWidth(80);
        return et;
    }

    private void addControlItem(LinearLayout p, String label, AppCompatEditText et) {
        AppCompatTextView tv = new AppCompatTextView(this);
        tv.setText(label); tv.setTextColor(Color.GRAY);
        p.addView(tv); p.addView(et);
    }
}
