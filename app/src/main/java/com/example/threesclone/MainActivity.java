package com.example.threesclone;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // --- CẤU HÌNH ---
    private int totalBits = 12;
    private int maxTurns = 20;
    private int aiDepth = 3;

    // --- TRẠNG THÁI ---
    private long targetValue, currentSum = 0;
    private int currentTurn = 0;
    private List<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    
    // --- UI ---
    private AppCompatButton[] buttons;
    private AppCompatTextView karmaText, statusText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputTurns, inputDepth;
    private AppCompatButton btnAuto;

    private boolean isAutoRunning = false;
    private Handler handler = new Handler();
    private Random rnd = new Random();
    private ZenOptionASolver solver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(20, 30, 20, 20);

        // --- CONTROLS ---
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        inputBits = createInput(String.valueOf(totalBits));
        inputTurns = createInput(String.valueOf(maxTurns));
        inputDepth = createInput(String.valueOf(aiDepth));

        AppCompatButton btnReset = new AppCompatButton(this);
        btnReset.setText("RESET");
        btnReset.setOnClickListener(v -> { stopAuto(); startNewGame(); });

        addControlItem(controls, "Bit:", inputBits);
        addControlItem(controls, "Turn:", inputTurns);
        addControlItem(controls, "Dep:", inputDepth);
        controls.addView(btnReset);
        root.addView(controls);

        btnAuto = new AppCompatButton(this);
        btnAuto.setText("🧠 OPTION A SOLVER");
        btnAuto.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(50, 50, 150)));
        btnAuto.setOnClickListener(v -> toggleAuto());
        root.addView(btnAuto);

        // --- DISPLAY ---
        karmaText = new AppCompatTextView(this);
        karmaText.setTextColor(Color.WHITE);
        karmaText.setTextSize(24);
        karmaText.setGravity(Gravity.CENTER);
        karmaText.setPadding(0, 20, 0, 10);
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
            aiDepth = Integer.parseInt(inputDepth.getText().toString());
        } catch (Exception e) { }

        // Setup Solver
        solver = new ZenOptionASolver();

        // Random Target
        realValues.clear();
        for (int i = 0; i < totalBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);
        
        targetValue = 0;
        int bitsToOn = rnd.nextInt(totalBits - 1) + 1;
        for (int i = 0; i < bitsToOn; i++) targetValue += realValues.get(i);
        Collections.shuffle(realValues); // Shuffle lại để giấu vị trí

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

        if (calculateKarmaPair()[0] + calculateKarmaPair()[1] == 0) {
            stopAuto();
            new AlertDialog.Builder(this).setTitle("SOLVED").show();
        } else if (currentTurn >= maxTurns) {
            startNewGame();
            Toast.makeText(this, "FAIL - RESET", Toast.LENGTH_SHORT).show();
        }
    }

    // --- ORACLE MỚI: TRẢ VỀ CẶP (K_ON, K_OFF) ---
    private int[] calculateKarmaPair() {
        int k_on = 0;  // Số bit đang ON nhưng SAI (thừa)
        int k_off = 0; // Số bit đang OFF nhưng SAI (thiếu)
        
        // Vì đây là giả lập game engine, ta được phép nhìn trộm đáp án để cung cấp State cho AI
        // AI không biết vị trí cụ thể, chỉ biết tổng số k_on, k_off
        for (int i = 0; i < totalBits; i++) {
            long val = realValues.get(i);
            boolean isTargetHas = (targetValue & val) != 0;
            boolean isCurrentHas = (currentSum & val) != 0;
            
            if (isCurrentHas && !isTargetHas) k_on++;   // Thừa
            if (!isCurrentHas && isTargetHas) k_off++;  // Thiếu
        }
        return new int[]{k_on, k_off};
    }

    private void toggleAuto() {
        isAutoRunning = !isAutoRunning;
        if (isAutoRunning) {
            btnAuto.setText("⏹ STOP AI");
            btnAuto.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
            handler.post(autoRunnable);
        } else stopAuto();
    }

    private void stopAuto() { 
        isAutoRunning = false; 
        btnAuto.setText("🧠 OPTION A SOLVER");
        btnAuto.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(50, 50, 150)));
        handler.removeCallbacks(autoRunnable); 
    }

    private Runnable autoRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoRunning) return;
            
            // 1. Lấy State chuẩn từ Oracle
            int[] karma = calculateKarmaPair(); // [k_on, k_off]
            
            int onCount = 0;
            for(boolean b : buttonStates) if(b) onCount++;
            int offCount = totalBits - onCount;

            // 2. AI quyết định: Tắt (Action 0) hay Bật (Action 1)?
            int action = solver.decideAction(karma[0], karma[1], onCount, offCount, aiDepth);
            
            // 3. Thực hiện Action (Random trong nhóm đã chọn)
            int moveIndex = -1;
            List<Integer> candidates = new ArrayList<>();
            
            if (action == 0) { // Muốn TẮT một nút ON
                for(int i=0; i<totalBits; i++) if(buttonStates[i]) candidates.add(i);
            } else { // Muốn BẬT một nút OFF
                for(int i=0; i<totalBits; i++) if(!buttonStates[i]) candidates.add(i);
            }

            if (!candidates.isEmpty()) {
                moveIndex = candidates.get(rnd.nextInt(candidates.size()));
                performMove(moveIndex);
                handler.postDelayed(this, 500);
            } else {
                stopAuto(); // Không còn nước đi hợp lệ (Should not happen)
            }
        }
    };

    private void updateUI() {
        int[] k = calculateKarmaPair();
        karmaText.setText("K_ON: " + k[0] + " | K_OFF: " + k[1] + " (Σ=" + (k[0]+k[1]) + ")");
        statusText.setText("Turn: " + currentTurn + "/" + maxTurns);
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

    // ==========================================
    // CLASS: ZEN OPTION A SOLVER (PURE MATH)
    // ==========================================
    public static class ZenOptionASolver {
        
        // Cache để Memoization: Key="kon_koff_on_off_depth" -> Value=ExpectedKarma
        private Map<String, Double> memo = new HashMap<>();

        public int decideAction(int k_on, int k_off, int on, int off, int depth) {
            memo.clear(); // Clear cache mỗi lượt mới để đảm bảo sạch sẽ
            
            // Tính EV cho hành động TẮT (Flip ON -> OFF)
            double ev_TurnOff = Double.MAX_VALUE;
            if (on > 0) {
                ev_TurnOff = getExpectedValue(k_on, k_off, on, off, 0, depth); // Action 0
            }

            // Tính EV cho hành động BẬT (Flip OFF -> ON)
            double ev_TurnOn = Double.MAX_VALUE;
            if (off > 0) {
                ev_TurnOn = getExpectedValue(k_on, k_off, on, off, 1, depth); // Action 1
            }

            // So sánh và trả về Action tối ưu (0: Tắt, 1: Bật)
            if (ev_TurnOff < ev_TurnOn) return 0;
            return 1;
        }

        // Action: 0 = Tắt 1 nút đang ON, 1 = Bật 1 nút đang OFF
        private double getExpectedValue(int k_on, int k_off, int on, int off, int action, int depth) {
            // Xử lý Transition Logic ngay tại đây để đệ quy
            
            double probSuccess;
            // Next States
            int s_k_on, s_k_off, s_on, s_off; // Success Case
            int f_k_on, f_k_off, f_on, f_off; // Fail Case

            if (action == 0) { // ACTION: TẮT (ON -> OFF)
                // P(Success) = Chọn đúng bit thừa để tắt
                probSuccess = (double) k_on / on;
                
                // Success: K_on giảm 1, On giảm 1, Off tăng 1
                s_k_on = k_on - 1; s_k_off = k_off; 
                s_on = on - 1; s_off = off + 1;
                
                // Fail: Chọn nhầm bit đúng -> Nó trở thành thiếu (K_off tăng 1)
                f_k_on = k_on; f_k_off = k_off + 1;
                f_on = on - 1; f_off = off + 1;
                
            } else { // ACTION: BẬT (OFF -> ON)
                // P(Success) = Chọn đúng bit thiếu để bật
                probSuccess = (double) k_off / off;
                
                // Success: K_off giảm 1, Off giảm 1, On tăng 1
                s_k_on = k_on; s_k_off = k_off - 1;
                s_on = on + 1; s_off = off - 1;
                
                // Fail: Chọn nhầm bit đúng -> Nó trở thành thừa (K_on tăng 1)
                f_k_on = k_on + 1; f_k_off = k_off;
                f_on = on + 1; f_off = off - 1;
            }

            // Gọi đệ quy cho 2 nhánh kết quả
            double valSuccess = solve(s_k_on, s_k_off, s_on, s_off, depth - 1);
            double valFail    = solve(f_k_on, f_k_off, f_on, f_off, depth - 1);

            return (probSuccess * valSuccess) + ((1.0 - probSuccess) * valFail);
        }

        // Hàm đệ quy Expectimax chính (State -> Value)
        private double solve(int k_on, int k_off, int on, int off, int depth) {
            // 1. Base Case: Leaf Node
            if (k_on + k_off == 0) return 0; // Solved
            if (depth == 0) return k_on + k_off; // Heuristic = Total Karma

            // Memoization Key
            String key = k_on + "_" + k_off + "_" + on + "_" + depth;
            if (memo.containsKey(key)) return memo.get(key);

            // 2. Max Node: AI chọn Min(EV_TurnOff, EV_TurnOn)
            double ev_TurnOff = Double.MAX_VALUE;
            if (on > 0) {
                // Tái sử dụng logic transition
                ev_TurnOff = getExpectedValue(k_on, k_off, on, off, 0, depth);
            }

            double ev_TurnOn = Double.MAX_VALUE;
            if (off > 0) {
                ev_TurnOn = getExpectedValue(k_on, k_off, on, off, 1, depth);
            }

            double result = Math.min(ev_TurnOff, ev_TurnOn);
            memo.put(key, result);
            return result;
        }
    }
}
