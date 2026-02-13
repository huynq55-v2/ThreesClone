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
import androidx.appcompat.widget.AppCompatTextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int numBits = 10; 
    private int maxTurns = 30;
    private long targetValue, currentSum = 0;
    private int currentTurn = 0;
    private List<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;
    private AppCompatTextView karmaText, statusText;
    private GridLayout grid;
    private boolean isAutoRunning = false;
    private Handler handler = new Handler();
    private Random rnd = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(30, 50, 30, 30);

        karmaText = new AppCompatTextView(this);
        karmaText.setTextColor(Color.WHITE);
        karmaText.setTextSize(35);
        karmaText.setGravity(Gravity.CENTER);
        root.addView(karmaText);

        statusText = new AppCompatTextView(this);
        statusText.setTextColor(Color.CYAN);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        AppCompatButton btnAuto = new AppCompatButton(this);
        btnAuto.setText("🤖 PURE PROBABILITY AI");
        btnAuto.setOnClickListener(v -> toggleAuto());
        root.addView(btnAuto);

        grid = new GridLayout(this);
        grid.setColumnCount(4);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        startNewGame();
    }

    private void startNewGame() {
        targetValue = Math.abs(rnd.nextLong() % ((long) Math.pow(2, numBits) - 1));
        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);
        buttonStates = new boolean[numBits];
        buttons = new AppCompatButton[numBits];
        currentTurn = 0; currentSum = 0;
        grid.removeAllViews();
        for (int i = 0; i < numBits; i++) {
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
        for (int i = 0; i < numBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);
        updateButtonVisual(index);
        updateUI();
        if (calculateKarma() == 0) {
            stopAuto();
            new AlertDialog.Builder(this).setTitle("SOLVED").show();
        } else if (currentTurn >= maxTurns) {
            startNewGame();
        }
    }

    private int calculateKarma() { return Long.bitCount(targetValue ^ currentSum); }

    private void updateUI() {
        karmaText.setText("KARMA: " + calculateKarma());
        statusText.setText("Turns: " + currentTurn + "/" + maxTurns);
    }

    private void updateButtonVisual(int i) {
        buttons[i].setBackgroundTintList(ColorStateList.valueOf(buttonStates[i] ? Color.YELLOW : Color.DKGRAY));
        buttons[i].setText(buttonStates[i] ? "ON" : "OFF");
    }

    private void toggleAuto() {
        isAutoRunning = !isAutoRunning;
        if (isAutoRunning) handler.post(autoRunnable);
    }

    private void stopAuto() { isAutoRunning = false; handler.removeCallbacks(autoRunnable); }

    private Runnable autoRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoRunning) return;
            int move = getPureProbabilityMove();
            if (move != -1) performMove(move);
            handler.postDelayed(this, 500);
        }
    };

    private int getPureProbabilityMove() {
        int k = calculateKarma();
        int onCount = 0;
        List<Integer> onIndices = new ArrayList<>();
        List<Integer> offIndices = new ArrayList<>();

        for (int i = 0; i < numBits; i++) {
            if (buttonStates[i]) {
                onCount++;
                onIndices.add(i);
            } else {
                offIndices.add(i);
            }
        }
        int offCount = numBits - onCount;

        // 1. TÍNH XÁC SUẤT TRUNG BÌNH CHO 2 HƯỚNG: BẬT VÀ TẮT
        double pCorrectOn = 0;  // Xác suất đúng khi TẮT một nút đang ON
        double pCorrectOff = 0; // Xác suất đúng khi BẬT một nút đang OFF
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

        // 2. QUYẾT ĐỊNH HƯỚNG VÀ RANDOM TRONG NHÓM ĐÓ
        if (pCorrectOn > pCorrectOff && !onIndices.isEmpty()) {
            return onIndices.get(rnd.nextInt(onIndices.size())); // Random trong đống đang ON
        } else if (!offIndices.isEmpty()) {
            return offIndices.get(rnd.nextInt(offIndices.size())); // Random trong đống đang OFF
        }
        
        return -1;
    }
}
