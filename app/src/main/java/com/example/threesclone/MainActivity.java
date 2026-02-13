package com.example.threesclone;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
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
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int numBits = 12;
    private int stepsPerSwap = 10; // Cố định sau N bước thì swap
    private int swapCount = 3; 
    private long maxCapacity, targetValue, currentSum = 0;
    private int turnCounter = 0; // Đếm số bước đã đi

    private ArrayList<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;

    private ProgressBar targetProgress, currentProgress;
    private AppCompatTextView infoText, statusText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputSteps, inputSwapCount;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(20, 30, 20, 20);

        // --- BẢNG ĐIỀU KHIỂN ---
        LinearLayout settings = new LinearLayout(this);
        settings.setGravity(Gravity.CENTER);
        
        inputBits = createInput("12");
        inputSteps = createInput("10"); // Ô nhập số bước cố định
        inputSwapCount = createInput("3"); 

        AppCompatButton btnStart = new AppCompatButton(this);
        btnStart.setText("CHƠI");
        ViewCompat.setBackgroundTintList(btnStart, ColorStateList.valueOf(Color.rgb(0, 100, 0)));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startNewGame());

        addSettingItem(settings, "B:", inputBits);
        addSettingItem(settings, "Step:", inputSteps);
        addSettingItem(settings, "S:", inputSwapCount);
        settings.addView(btnStart);
        root.addView(settings);

        // --- THANH NĂNG LƯỢNG ---
        targetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        targetProgress.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(targetProgress);

        currentProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        currentProgress.getProgressDrawable().setColorFilter(Color.YELLOW, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(currentProgress);

        infoText = new AppCompatTextView(this); infoText.setTextColor(Color.WHITE); infoText.setGravity(Gravity.CENTER); root.addView(infoText);
        statusText = new AppCompatTextView(this); statusText.setTextColor(Color.CYAN); statusText.setGravity(Gravity.CENTER); root.addView(statusText);

        grid = new GridLayout(this); grid.setColumnCount(4);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        startNewGame();
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

    private void startNewGame() {
        try {
            numBits = Integer.parseInt(inputBits.getText().toString());
            stepsPerSwap = Integer.parseInt(inputSteps.getText().toString());
            swapCount = Integer.parseInt(inputSwapCount.getText().toString());
        } catch (Exception e) { numBits = 12; stepsPerSwap = 10; swapCount = 3; }
        
        if (numBits > 31) numBits = 31;
        if (swapCount > numBits) swapCount = numBits;

        maxCapacity = (long) Math.pow(2, numBits) - 1;
        targetValue = Math.abs(random.nextLong()) % maxCapacity; if (targetValue == 0) targetValue = 1;

        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        buttonStates = new boolean[numBits];
        buttons = new AppCompatButton[numBits];
        turnCounter = 0; // Reset đếm bước
        currentSum = 0;

        grid.removeAllViews();
        grid.setColumnCount(numBits > 16 ? 6 : 4);
        for (int i = 0; i < numBits; i++) {
            final int index = i;
            buttons[i] = new AppCompatButton(this);
            updateButtonVisual(index);
            buttons[i].setOnClickListener(v -> onButtonClick(index));
            grid.addView(buttons[i]);
        }
        updateUI();
    }

    private void onButtonClick(int index) {
        // Hành động
        buttonStates[index] = !buttonStates[index];
        turnCounter++; // Tăng số bước sau mỗi lần bấm

        currentSum = 0;
        for (int i = 0; i < numBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);

        updateButtonVisual(index);
        updateUI();
        
        if (currentSum == targetValue) {
            showWin();
        } else if (turnCounter >= stepsPerSwap) {
            triggerMultiSwap();
        } else {
            statusText.setText("Bước: " + turnCounter + " / " + stepsPerSwap);
            statusText.setTextColor(Color.WHITE);
        }
    }

    private void triggerMultiSwap() {
        Toast.makeText(this, "⚠ ĐẾN GIỜ VÔ THƯỜNG - SWAP!", Toast.LENGTH_SHORT).show();
        
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < numBits; i++) indices.add(i);
        Collections.shuffle(indices);
        List<Integer> swapIndices = indices.subList(0, swapCount);

        long firstValue = realValues.get(swapIndices.get(0));
        for (int i = 0; i < swapCount - 1; i++) {
            realValues.set(swapIndices.get(i), realValues.get(swapIndices.get(i + 1)));
        }
        realValues.set(swapIndices.get(swapCount - 1), firstValue);

        turnCounter = 0; // Reset đếm bước sau khi swap
        
        currentSum = 0;
        for (int k = 0; k < numBits; k++) {
            if (buttonStates[k]) currentSum += realValues.get(k);
            updateButtonVisual(k);
        }
        updateUI();
        statusText.setText("CẤU TRÚC ĐÃ ĐẢO LỘN!");
        statusText.setTextColor(Color.MAGENTA);
    }

    private void updateButtonVisual(int index) {
        if (buttonStates[index]) {
            ViewCompat.setBackgroundTintList(buttons[index], ColorStateList.valueOf(Color.YELLOW));
            buttons[index].setTextColor(Color.BLACK); buttons[index].setText("ON");
        } else {
            ViewCompat.setBackgroundTintList(buttons[index], ColorStateList.valueOf(Color.rgb(50, 50, 50)));
            buttons[index].setTextColor(Color.GRAY); buttons[index].setText("?");
        }
    }

    private void updateUI() {
        targetProgress.setMax(1000); currentProgress.setMax(1000);
        targetProgress.setProgress((int) ((targetValue * 1000) / maxCapacity));
        currentProgress.setProgress((int) ((currentSum * 1000) / maxCapacity));
        infoText.setText("T: " + targetValue + " | C: " + currentSum);
    }

    private void showWin() {
        new AlertDialog.Builder(this).setTitle("GIÁC NGỘ").setMessage("Cân bằng hoàn hảo sau " + turnCounter + " bước!")
            .setPositiveButton("Lại", (d, w) -> startNewGame()).setCancelable(false).show();
    }
}
