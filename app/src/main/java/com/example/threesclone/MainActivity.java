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

    // Tham số mặc định
    private int numBits = 12;
    private int maxTurns = 8; 
    private int swapCount = 3; 
    
    // Biến vận hành
    private long maxCapacity, targetValue, currentSum = 0;
    private int currentTurn = 0;

    private ArrayList<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private AppCompatButton[] buttons;

    // UI Elements
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

        // --- SETTINGS PANEL (Cho phép edit tham số) ---
        LinearLayout settings = new LinearLayout(this);
        settings.setGravity(Gravity.CENTER);
        
        inputBits = createInput("12");
        inputMaxTurns = createInput("10"); 
        inputSwapCount = createInput("3");

        AppCompatButton btnStart = new AppCompatButton(this);
        btnStart.setText("CHƠI");
        ViewCompat.setBackgroundTintList(btnStart, ColorStateList.valueOf(Color.rgb(0, 100, 0)));
        btnStart.setTextColor(Color.WHITE);
        // Khi bấm Start sẽ đọc lại tham số từ ô nhập
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
        
        // Status Text: Chỉ hiện số lượt, không bị đè bởi thông báo lỗi
        statusText = new AppCompatTextView(this); 
        statusText.setTextColor(Color.CYAN); 
        statusText.setGravity(Gravity.CENTER); 
        statusText.setTextSize(16);
        root.addView(statusText);

        grid = new GridLayout(this); grid.setColumnCount(4);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        
        // Khởi động lần đầu
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
        // ĐỌC THAM SỐ MỚI
        try {
            numBits = Integer.parseInt(inputBits.getText().toString());
            maxTurns = Integer.parseInt(inputMaxTurns.getText().toString());
            swapCount = Integer.parseInt(inputSwapCount.getText().toString());
        } catch (Exception e) { 
            // Fallback nếu nhập sai
            numBits = 12; maxTurns = 10; swapCount = 3; 
        }
        
        // Validate logic
        if (numBits > 31) numBits = 31;
        if (swapCount > numBits) swapCount = numBits;
        if (swapCount < 2) swapCount = 2;

        // Setup Game Logic
        maxCapacity = (long) Math.pow(2, numBits) - 1;
        targetValue = Math.abs(random.nextLong()) % maxCapacity; 
        if (targetValue == 0) targetValue = 1;

        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        buttonStates = new boolean[numBits];
        buttons = new AppCompatButton[numBits];
        currentTurn = 0; // Reset lượt
        currentSum = 0;

        // Render Grid
        grid.removeAllViews();
        grid.setColumnCount(numBits > 16 ? 6 : 4);
        for (int i = 0; i < numBits; i++) {
            final int index = i;
            buttons[i] = new AppCompatButton(this);
            buttons[i].setText("?");
            updateButtonVisual(index);
            buttons[i].setOnClickListener(v -> onButtonClick(index));
            grid.addView(buttons[i]);
        }
        
        updateUI();
    }

    private void onButtonClick(int index) {
        boolean isTurningOn = !buttonStates[index];
        buttonStates[index] = isTurningOn;

        // CHỈ TĂNG LƯỢT KHI BẬT
        if (isTurningOn) {
            currentTurn++;
        }

        // Tính lại tổng
        calculateSum();

        // Check Win
        if (currentSum == targetValue) {
            updateUI();
            showWin();
            return;
        }

        // Check Swap: Nếu vừa bật mà vượt quá số lượt -> Swap ngay
        if (currentTurn >= maxTurns) {
            triggerMultiSwap();
        } 
        
        updateButtonVisual(index);
        updateUI();
    }

    private void calculateSum() {
        currentSum = 0;
        for (int i = 0; i < numBits; i++) {
            if (buttonStates[i]) currentSum += realValues.get(i);
        }
    }

    private void triggerMultiSwap() {
        // 1. THÔNG BÁO NHẸ NHÀNG (TOAST)
        Toast.makeText(this, "⚠ SWAP! (" + swapCount + " bit)", Toast.LENGTH_SHORT).show();
        
        // 2. LOGIC SWAP (Xoay vòng giá trị)
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < numBits; i++) indices.add(i);
        Collections.shuffle(indices);
        List<Integer> swapIndices = indices.subList(0, swapCount);

        long firstValue = realValues.get(swapIndices.get(0));
        for (int i = 0; i < swapCount - 1; i++) {
            realValues.set(swapIndices.get(i), realValues.get(swapIndices.get(i + 1)));
        }
        realValues.set(swapIndices.get(swapCount - 1), firstValue);

        // 3. RESET LƯỢT VỀ 0
        currentTurn = 0;
        
        // 4. TÍNH LẠI TỔNG (Vì giá trị bên dưới nút đã đổi)
        calculateSum();
        
        // Cập nhật visual lại toàn bộ (đề phòng)
        for(int k=0; k<numBits; k++) updateButtonVisual(k);
    }

    private void updateButtonVisual(int index) {
        if (buttonStates[index]) {
            ViewCompat.setBackgroundTintList(buttons[index], ColorStateList.valueOf(Color.YELLOW));
            buttons[index].setTextColor(Color.BLACK); 
            buttons[index].setText("ON");
        } else {
            ViewCompat.setBackgroundTintList(buttons[index], ColorStateList.valueOf(Color.rgb(50, 50, 50)));
            buttons[index].setTextColor(Color.GRAY); 
            buttons[index].setText("?");
        }
    }

    private void updateUI() {
        targetProgress.setMax(1000); 
        currentProgress.setMax(1000);
        targetProgress.setProgress((int) ((targetValue * 1000) / maxCapacity));
        currentProgress.setProgress((int) ((currentSum * 1000) / maxCapacity));
        
        infoText.setText("Target: " + targetValue + " | Current: " + currentSum);
        
        // Status Text chỉ hiển thị số lượt
        statusText.setText("Lượt: " + currentTurn + " / " + maxTurns);
        
        // Đổi màu cảnh báo khi sắp hết lượt
        if (currentTurn >= maxTurns - 2) {
            statusText.setTextColor(Color.RED);
        } else {
            statusText.setTextColor(Color.CYAN);
        }
    }

    private void showWin() {
        new AlertDialog.Builder(this)
            .setTitle("GIÁC NGỘ")
            .setMessage("Cân bằng hoàn hảo!")
            .setPositiveButton("Lại", (d, w) -> startNewGame())
            .setCancelable(false)
            .show();
    }
}
