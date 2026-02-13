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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int numBits = 10; // Giảm xuống 10 cho đúng ví dụ của anh
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
        btnAuto.setText("🧮 PURE MATH AI");
        btnAuto.setOnClickListener(v -> toggleAuto());
        root.addView(btnAuto);

        grid = new GridLayout(this);
        grid.setColumnCount(4);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        startNewGame();
    }

    private void startNewGame() {
        targetValue = Math.abs(new Random().nextLong() % ((long) Math.pow(2, numBits) - 1));
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
        statusText.setText("Sinh mệnh: " + currentTurn + "/" + maxTurns);
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
            int bestMove = findBestExpectimaxMove();
            if (bestMove != -1) performMove(bestMove);
            handler.postDelayed(this, 500);
        }
    };

    private int findBestExpectimaxMove() {
        int k = calculateKarma();
        double minEV = Double.MAX_VALUE;
        int bestIdx = -1;

        int onCount = 0;
        for (boolean s : buttonStates) if (s) onCount++;
        int offCount = numBits - onCount;

        for (int i = 0; i < numBits; i++) {
            boolean switchingOn = !buttonStates[i];
            
            // TÍNH XÁC SUẤT KHÔNG GIẢ ĐỊNH
            double pCorrectTotal = 0;
            int scenarios = 0;

            // Duyệt mọi kịch bản (k_on + k_off = k)
            for (int k_on = 0; k_on <= onCount; k_on++) {
                int k_off = k - k_on;
                if (k_off >= 0 && k_off <= offCount) {
                    double p;
                    if (switchingOn) p = (double) k_off / offCount;
                    else p = (double) k_on / onCount;
                    
                    pCorrectTotal += p;
                    scenarios++;
                }
            }
            
            double pCorrect = (scenarios > 0) ? pCorrectTotal / scenarios : 0;
            double ev = simulate(k, switchingOn, onCount, offCount, pCorrect, 3);

            if (ev < minEV) {
                minEV = ev;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private double simulate(int k, boolean lastOn, int on, int off, double p, int depth) {
        if (k == 0) return 0;
        if (depth == 0) return k;

        int nextOn = lastOn ? on + 1 : on - 1;
        int nextOff = numBits - nextOn;

        // Chance Node với p được tính trung bình từ các kịch bản
        double evCorrect = simulate(k - 1, !lastOn, nextOn, nextOff, (double)(k-1.0)/numBits, depth - 1);
        double evWrong = simulate(k + 1, !lastOn, nextOn, nextOff, (double)(k+1.0)/numBits, depth - 1);

        return (p * evCorrect) + ((1 - p) * evWrong);
    }
}
