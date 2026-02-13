package com.example.threesclone; // Đã đổi theo package trong log của anh

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity; // Dùng AppCompatActivity cho chuẩn
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int numBits = 12;
    private int swapLimit = 3;
    private long maxCapacity;
    private long targetValue;
    private long currentSum = 0;
    private int wrongStreak = 0;

    private ArrayList<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;

    private ProgressBar targetProgress, currentProgress;
    private AppCompatTextView infoText, statusText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputSwap;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(30, 40, 30, 30);

        // --- CÀI ĐẶT (Settings Panel) ---
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        settings.setGravity(Gravity.CENTER);
        
        inputBits = new AppCompatEditText(this);
        inputBits.setHint("Bits");
        inputBits.setText("12");
        inputBits.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputBits.setTextColor(Color.WHITE);
        
        inputSwap = new AppCompatEditText(this);
        inputSwap.setHint("Limit");
        inputSwap.setText("3");
        inputSwap.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputSwap.setTextColor(Color.WHITE);

        AppCompatButton btnStart = new AppCompatButton(this);
        btnStart.setText("CHƠI MỚI");
        btnStart.setSupportBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0, 100, 0)));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startNewGame());

        // Fix lỗi lint bằng cách dùng AppCompatTextView
        AppCompatTextView tvBits = new AppCompatTextView(this);
        tvBits.setText("Bits:"); tvBits.setTextColor(Color.GRAY);
        settings.addView(tvBits);
        settings.addView(inputBits);

        AppCompatTextView tvLimit = new AppCompatTextView(this);
        tvLimit.setText("Limit:"); tvLimit.setTextColor(Color.GRAY);
        settings.addView(tvLimit);
        settings.addView(inputSwap);
        settings.addView(btnStart);
        root.addView(settings);

        // --- THANH HIỂN THỊ ---
        targetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        targetProgress.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
        
        AppCompatTextView tvT = new AppCompatTextView(this);
        tvT.setText("TARGET"); tvT.setTextColor(Color.GREEN); tvT.setTextSize(10);
        root.addView(tvT);
        root.addView(targetProgress);

        currentProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        currentProgress.getProgressDrawable().setColorFilter(Color.YELLOW, android.graphics.PorterDuff.Mode.SRC_IN);
        
        AppCompatTextView tvC = new AppCompatTextView(this);
        tvC.setText("CURRENT"); tvC.setTextColor(Color.YELLOW); tvC.setTextSize(10); tvC.setPadding(0,10,0,0);
        root.addView(tvC);
        root.addView(currentProgress);

        infoText = new AppCompatTextView(this);
        infoText.setTextColor(Color.WHITE);
        infoText.setGravity(Gravity.CENTER);
        infoText.setPadding(0, 20, 0, 5);
        root.addView(infoText);

        statusText = new AppCompatTextView(this);
        statusText.setTextColor(Color.GRAY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(12);
        root.addView(statusText);

        // --- GRID NÚT BẤM ---
        grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        grid.setLayoutParams(params);
        root.addView(grid);

        setContentView(root);
        startNewGame();
    }

    private void startNewGame() {
        try {
            numBits = Integer.parseInt(inputBits.getText().toString());
            swapLimit = Integer.parseInt(inputSwap.getText().toString());
        } catch (Exception e) {
            numBits = 12; swapLimit = 3;
        }
        if (numBits > 31) numBits = 31; // Giới hạn bit để tránh tràn số

        maxCapacity = (long) Math.pow(2, numBits) - 1;
        targetValue = Math.abs(random.nextLong()) % maxCapacity;
        if (targetValue == 0) targetValue = 1;

        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        buttonStates = new boolean[numBits];
        buttons = new AppCompatButton[numBits];
        wrongStreak = 0;
        currentSum = 0;

        grid.removeAllViews();
        grid.setColumnCount(numBits > 16 ? 6 : 4);

        for (int i = 0; i < numBits; i++) {
            final int index = i;
            buttons[i] = new AppCompatButton(this);
            buttons[i].setText("?");
            buttons[i].setTextSize(10);
            buttons[i].setSupportBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(40, 40, 40)));
            buttons[i].setTextColor(Color.GRAY);
            buttons[i].setOnClickListener(v -> onButtonClick(index));
            grid.addView(buttons[i]);
        }

        updateUI();
        statusText.setText("Logic: Chỉ xét đúng/sai khi BẬT.");
        statusText.setTextColor(Color.GRAY);
    }

    private void onButtonClick(int index) {
        long oldDiff = Math.abs(targetValue - currentSum);
        boolean isTurningOn = !buttonStates[index];
        buttonStates[index] = isTurningOn;

        currentSum = 0;
        for (int i = 0; i < numBits; i++) {
            if (buttonStates[i]) currentSum += realValues.get(i);
        }

        long newDiff = Math.abs(targetValue - currentSum);

        if (buttonStates[index]) {
            buttons[index].setSupportBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.YELLOW));
            buttons[index].setTextColor(Color.BLACK);
            buttons[index].setText("ON");
        } else {
            buttons[index].setSupportBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(40, 40, 40)));
            buttons[index].setTextColor(Color.GRAY);
            buttons[index].setText("?");
        }

        if (isTurningOn) {
            if (newDiff < oldDiff) {
                wrongStreak = 0;
                statusText.setText("Tốt! Nợ đã xóa.");
                statusText.setTextColor(Color.GREEN);
            } else {
                wrongStreak++;
                statusText.setText("Sai! Phạt: " + wrongStreak + "/" + swapLimit);
                statusText.setTextColor(Color.RED);
            }
        } else {
            statusText.setText("Tắt bit. Nợ giữ nguyên: " + wrongStreak);
            statusText.setTextColor(Color.rgb(255, 165, 0));
        }

        updateUI();
        if (currentSum == targetValue) showWin();
        else if (wrongStreak >= swapLimit) triggerSwap();
    }

    private void triggerSwap() {
        Toast.makeText(this, "⚠ SWAP!", Toast.LENGTH_SHORT).show();
        int i = random.nextInt(numBits), j = random.nextInt(numBits);
        while (i == j) j = random.nextInt(numBits);
        Collections.swap(realValues, i, j);
        wrongStreak = 0;
        
        currentSum = 0;
        for (int k = 0; k < numBits; k++) if (buttonStates[k]) currentSum += realValues.get(k);
        
        updateUI();
        statusText.setText("VŨ TRỤ ĐẢO LỘN!");
        statusText.setTextColor(Color.MAGENTA);
    }

    private void updateUI() {
        targetProgress.setMax(1000);
        currentProgress.setMax(1000);
        targetProgress.setProgress((int) ((targetValue * 1000) / maxCapacity));
        currentProgress.setProgress((int) ((currentSum * 1000) / maxCapacity));
        infoText.setText("Target: " + targetValue + " | Hiện tại: " + currentSum);
    }

    private void showWin() {
        new AlertDialog.Builder(this).setTitle("GIÁC NGỘ").setMessage("Cân bằng thành công!")
            .setPositiveButton("Lại", (d, w) -> startNewGame()).setCancelable(false).show();
    }
}
