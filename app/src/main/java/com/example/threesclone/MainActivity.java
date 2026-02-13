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
import java.util.HashSet;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int numBits = 12;
    private int swapLimit = 3;
    private long maxCapacity, targetValue, currentSum = 0;
    private int wrongStreak = 0;

    private ArrayList<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;
    
    // TRACKING: Lưu những nút đã từng được bật (vấy bẩn)
    private HashSet<Integer> taintedButtons = new HashSet<>();

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

        // SETTINGS PANEL
        LinearLayout settings = new LinearLayout(this);
        settings.setGravity(Gravity.CENTER);
        inputBits = new AppCompatEditText(this); inputBits.setText("12"); inputBits.setInputType(InputType.TYPE_CLASS_NUMBER); inputBits.setTextColor(Color.WHITE);
        inputSwap = new AppCompatEditText(this); inputSwap.setText("3"); inputSwap.setInputType(InputType.TYPE_CLASS_NUMBER); inputSwap.setTextColor(Color.WHITE);
        AppCompatButton btnStart = new AppCompatButton(this); btnStart.setText("CHƠI MỚI");
        ViewCompat.setBackgroundTintList(btnStart, ColorStateList.valueOf(Color.rgb(0, 100, 0)));
        btnStart.setTextColor(Color.WHITE); btnStart.setOnClickListener(v -> startNewGame());

        settings.addView(new AppCompatTextView(this){{setText("Bits:"); setTextColor(Color.GRAY);}});
        settings.addView(inputBits);
        settings.addView(new AppCompatTextView(this){{setText("Lim:"); setTextColor(Color.GRAY);}});
        settings.addView(inputSwap);
        settings.addView(btnStart);
        root.addView(settings);

        // VISUAL BARS
        targetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        targetProgress.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(targetProgress);

        currentProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        currentProgress.getProgressDrawable().setColorFilter(Color.YELLOW, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(currentProgress);

        infoText = new AppCompatTextView(this); infoText.setTextColor(Color.WHITE); infoText.setGravity(Gravity.CENTER); root.addView(infoText);
        statusText = new AppCompatTextView(this); statusText.setTextColor(Color.GRAY); statusText.setGravity(Gravity.CENTER); root.addView(statusText);

        grid = new GridLayout(this); grid.setColumnCount(4);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        startNewGame();
    }

    private void startNewGame() {
        try {
            numBits = Integer.parseInt(inputBits.getText().toString());
            swapLimit = Integer.parseInt(inputSwap.getText().toString());
        } catch (Exception e) { numBits = 12; swapLimit = 3; }
        if (numBits > 31) numBits = 31;

        maxCapacity = (long) Math.pow(2, numBits) - 1;
        targetValue = Math.abs(random.nextLong()) % maxCapacity; if (targetValue == 0) targetValue = 1;

        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        buttonStates = new boolean[numBits];
        buttons = new AppCompatButton[numBits];
        wrongStreak = 0;
        currentSum = 0;
        taintedButtons.clear(); 

        grid.removeAllViews();
        grid.setColumnCount(numBits > 16 ? 6 : 4);
        for (int i = 0; i < numBits; i++) {
            final int index = i;
            buttons[i] = new AppCompatButton(this); buttons[i].setText("?");
            updateButtonVisual(index);
            buttons[i].setOnClickListener(v -> onButtonClick(index));
            grid.addView(buttons[i]);
        }
        updateUI();
    }

    private void onButtonClick(int index) {
        long oldDiff = Math.abs(targetValue - currentSum);
        boolean isTurningOn = !buttonStates[index];
        buttonStates[index] = isTurningOn;

        currentSum = 0;
        for (int i = 0; i < numBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);
        long newDiff = Math.abs(targetValue - currentSum);

        if (isTurningOn) {
            if (taintedButtons.contains(index)) {
                // Nút cũ bật lại -> Không reset nợ
                if (newDiff >= oldDiff) wrongStreak++;
                statusText.setText("Nút đã dùng! Phạt: " + wrongStreak);
                statusText.setTextColor(Color.rgb(200, 0, 200)); // Tím
            } else {
                // Nút mới hoàn toàn
                taintedButtons.add(index); 
                if (newDiff < oldDiff) {
                    wrongStreak = 0;
                    statusText.setText("Duyên mới - Đúng hướng! Xóa nợ.");
                    statusText.setTextColor(Color.GREEN);
                } else {
                    wrongStreak++;
                    statusText.setText("Duyên mới - Sai hướng! Phạt: " + wrongStreak);
                    statusText.setTextColor(Color.RED);
                }
            }
        } else {
            statusText.setText("Buông bỏ. Nợ vẫn còn: " + wrongStreak);
            statusText.setTextColor(Color.rgb(255, 165, 0)); // Cam
        }

        updateButtonVisual(index);
        updateUI();
        
        if (currentSum == targetValue) showWin();
        else if (wrongStreak >= swapLimit) triggerSwap();
    }

    private void updateButtonVisual(int index) {
        if (buttonStates[index]) {
            // Đang bật
            ViewCompat.setBackgroundTintList(buttons[index], ColorStateList.valueOf(Color.YELLOW));
            buttons[index].setTextColor(Color.BLACK);
            buttons[index].setText("ON");
        } else if (taintedButtons.contains(index)) {
            // Đã dùng và đang tắt -> Xám xịt
            ViewCompat.setBackgroundTintList(buttons[index], ColorStateList.valueOf(Color.rgb(15, 15, 15)));
            buttons[index].setTextColor(Color.rgb(60, 60, 60));
            buttons[index].setText("X");
        } else {
            // Chưa dùng
            ViewCompat.setBackgroundTintList(buttons[index], ColorStateList.valueOf(Color.rgb(40, 40, 40)));
            buttons[index].setTextColor(Color.GRAY);
            buttons[index].setText("?");
        }
    }

    private void triggerSwap() {
        Toast.makeText(this, "⚠ SWAP - TẨY TRẮNG NGHIỆP QUẢ!", Toast.LENGTH_SHORT).show();
        int i = random.nextInt(numBits), j = random.nextInt(numBits);
        while (i == j) j = random.nextInt(numBits);
        Collections.swap(realValues, i, j);
        
        wrongStreak = 0;
        taintedButtons.clear(); // Reset toàn bộ vấy bẩn
        
        currentSum = 0;
        for (int k = 0; k < numBits; k++) {
            if (buttonStates[k]) currentSum += realValues.get(k);
            updateButtonVisual(k); // Cập nhật lại màu sắc cho tất cả các nút
        }
        updateUI();
        statusText.setText("VŨ TRỤ ĐẢO LỘN - LÀM LẠI TỪ ĐẦU!");
        statusText.setTextColor(Color.CYAN);
    }

    private void updateUI() {
        targetProgress.setMax(1000); currentProgress.setMax(1000);
        targetProgress.setProgress((int) ((targetValue * 1000) / maxCapacity));
        currentProgress.setProgress((int) ((currentSum * 1000) / maxCapacity));
        infoText.setText("T: " + targetValue + " | C: " + currentSum);
    }

    private void showWin() {
        new AlertDialog.Builder(this).setTitle("GIÁC NGỘ").setMessage("Bạn đã tìm thấy sự cân bằng!")
            .setPositiveButton("Lại", (d, w) -> startNewGame()).setCancelable(false).show();
    }
}
