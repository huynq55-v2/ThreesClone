package com.huy.zenlogic;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends Activity {

    private int numBits = 12;
    private int swapLimit = 3; // Mặc định là 3 cho dễ thở hơn
    private long maxCapacity;
    private long targetValue;
    private long currentSum = 0;
    private int wrongStreak = 0;

    private ArrayList<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    private Button[] buttons;

    private ProgressBar targetProgress, currentProgress;
    private TextView infoText, statusText;
    private GridLayout grid;
    private EditText inputBits, inputSwap;
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
        
        inputBits = new EditText(this);
        inputBits.setHint("Bits");
        inputBits.setText("12");
        inputBits.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputBits.setTextColor(Color.WHITE);
        
        inputSwap = new EditText(this);
        inputSwap.setHint("Limit");
        inputSwap.setText("3");
        inputSwap.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputSwap.setTextColor(Color.WHITE);

        Button btnStart = new Button(this);
        btnStart.setText("CHƠI MỚI");
        btnStart.setBackgroundColor(Color.rgb(0, 100, 0));
        btnStart.setTextColor(Color.WHITE);
        btnStart.setOnClickListener(v -> startNewGame());

        settings.addView(new TextView(this){{setText("Bits:"); setTextColor(Color.GRAY);}});
        settings.addView(inputBits);
        settings.addView(new TextView(this){{setText("Limit:"); setTextColor(Color.GRAY);}});
        settings.addView(inputSwap);
        settings.addView(btnStart);
        root.addView(settings);

        // --- THANH HIỂN THỊ ---
        targetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        targetProgress.getProgressDrawable().setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(new TextView(this){{setText("TARGET"); setTextColor(Color.GREEN); setTextSize(10);}});
        root.addView(targetProgress);

        currentProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        currentProgress.getProgressDrawable().setColorFilter(Color.YELLOW, android.graphics.PorterDuff.Mode.SRC_IN);
        root.addView(new TextView(this){{setText("CURRENT"); setTextColor(Color.YELLOW); setTextSize(10); setPadding(0,10,0,0);}});
        root.addView(currentProgress);

        infoText = new TextView(this);
        infoText.setTextColor(Color.WHITE);
        infoText.setGravity(Gravity.CENTER);
        infoText.setPadding(0, 20, 0, 5);
        root.addView(infoText);

        statusText = new TextView(this);
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
        if (numBits > 32) numBits = 32; // Giới hạn bit để tránh tràn số Long

        maxCapacity = (long) Math.pow(2, numBits) - 1;
        targetValue = Math.abs(random.nextLong()) % maxCapacity;
        if (targetValue == 0) targetValue = 1;

        realValues.clear();
        for (int i = 0; i < numBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);

        buttonStates = new boolean[numBits];
        buttons = new Button[numBits];
        wrongStreak = 0;
        currentSum = 0;

        grid.removeAllViews();
        grid.setColumnCount(numBits > 16 ? 6 : 4);

        for (int i = 0; i < numBits; i++) {
            final int index = i;
            buttons[i] = new Button(this);
            buttons[i].setText("?");
            buttons[i].setTextSize(10);
            buttons[i].setBackgroundColor(Color.rgb(40, 40, 40));
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
            buttons[index].setBackgroundColor(Color.YELLOW);
            buttons[index].setTextColor(Color.BLACK);
            buttons[index].setText("ON");
        } else {
            buttons[index].setBackgroundColor(Color.rgb(40, 40, 40));
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
