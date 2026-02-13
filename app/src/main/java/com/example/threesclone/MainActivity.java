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
    private int maxTurns = 10; // Số lượt BẬT tối đa trước khi Swap
    private int swapCount = 3; // Số bit bị tráo mỗi lần Swap
    
    private long maxCapacity, targetValue, currentSum = 0;
    private int currentTurn = 0; // Đếm số lần đã BẬT

    private ArrayList<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;

    private ProgressBar targetProgress, currentProgress;
    private AppCompatTextView infoText, statusText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputMaxTurns, inputSwapCount;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(20, 30, 20, 20);

        // --- SETTINGS ---
        LinearLayout settings = new LinearLayout(this);
        settings.setGravity(Gravity.CENTER);
        
        inputBits = createInput("12");
        inputMaxTurns = createInput("8"); // Mặc định 8 lượt bật là Swap
        inputSwapCount = createInput("3");

        AppCompatButton btnStart = new AppCompatButton(this);
        btnStart.setText("CHƠI");
        ViewCompat.setBackgroundTintList(btnStart, ColorStateList.valueOf(Color.rgb(0, 100, 0)));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startNewGame());

        addSettingItem(settings, "Bit:", inputBits);
        addSettingItem(settings, "Turn:", inputMaxTurns);
        addSettingItem(settings, "Swap:", inputSwapCount);
        settings.addView(btnStart);
        root.addView(settings);

        // --- BARS ---
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
            maxTurns = Integer.parseInt(inputMaxTurns.getText().toString());
            swapCount = Integer.parseInt(inputSwapCount.getText().toString());
        } catch (Exception e) { numBits = 12; maxTurns = 8; swapCount = 3; }
        
        if (numBits > 31) numBits = 31;
        if (swapCount > numBits) swapCount = numBits;

        maxCapacity = (long) Math.pow(2, numBits) - 1;
        targetValue = Math.abs(random.nextLong()) % maxCapacity; if (targetValue == 0) targetValue = 1;

        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        buttonStates = new boolean[numBits];
        buttons = new AppCompatButton[numBits];
        currentTurn = 0;
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
        statusText.setText("Sẵn sàng. Chỉ tính lượt khi BẬT.");
    }

    private void onButtonClick(int index) {
        // Kiểm tra hành động: Bật hay Tắt?
        boolean isTurningOn = !buttonStates[index];
        buttonStates[index] = isTurningOn; // Cập nhật trạng thái

        // Tính toán Logic Turn
        if (isTurningOn) {
            currentTurn++; // CHỈ TĂNG LƯỢT KHI BẬT
        }
        // Nếu Tắt (isTurningOn == false) -> Không tăng turn, coi như sửa sai miễn phí.

        // Tính lại tổng
        currentSum = 0;
        for (int i = 0; i < numBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);

        updateButtonVisual(index);
        
        // Kiểm tra Win trước
        if (currentSum == targetValue) {
            updateUI();
            showWin();
            return;
        }

        // Kiểm tra Swap (Chỉ xảy ra nếu vừa Bật làm tràn lượt)
        if (currentTurn >= maxTurns) {
            triggerMultiSwap();
        } else {
            updateUI();
        }
    }

    private void triggerMultiSwap() {
        Toast.makeText(this, "⚠ HẾT LƯỢT - SWAP " + swapCount + " BIT!", Toast.LENGTH_SHORT).show();
        
        // Logic Swap: Xoay vòng giá trị của N bit ngẫu nhiên
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < numBits; i++) indices.add(i);
        Collections.shuffle(indices);
        List<Integer> swapIndices = indices.subList(0, swapCount);

        long firstValue = realValues.get(swapIndices.get(0));
        for (int i = 0; i < swapCount - 1; i++) {
            realValues.set(swapIndices.get(i), realValues.get(swapIndices.get(i + 1)));
        }
        realValues.set(swapIndices.get(swapCount - 1), firstValue);

        currentTurn = 0; // Reset lượt về 0
        
        // Tính lại tổng sau khi giá trị bị đổi
        currentSum = 0;
        for (int k = 0; k < numBits; k++) {
            if (buttonStates[k]) currentSum += realValues.get(k);
        }
        
        // Cập nhật giao diện
        for (int k = 0; k < numBits; k++) updateButtonVisual(k);
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
        
        infoText.setText("Target: " + targetValue + " | Current: " + currentSum);
        
        // Hiển thị số lượt
        String turnInfo = "Lượt: " + currentTurn + " / " + maxTurns;
        if (!statusText.getText().toString().equals("CẤU TRÚC ĐÃ ĐẢO LỘN!")) {
            statusText.setText(turnInfo);
            // Đổi màu cảnh báo nếu sắp hết lượt
            if (currentTurn >= maxTurns - 2) statusText.setTextColor(Color.RED);
            else statusText.setTextColor(Color.CYAN);
        }
    }

    private void showWin() {
        new AlertDialog.Builder(this).setTitle("GIÁC NGỘ").setMessage("Cân bằng hoàn hảo!")
            .setPositiveButton("Lại", (d, w) -> startNewGame()).setCancelable(false).show();
    }
}
