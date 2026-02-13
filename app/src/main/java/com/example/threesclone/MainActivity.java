package com.example.threesclone;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // CẤU HÌNH
    private int totalBits = 12;
    private int maxTurns = 15; 
    private int swapIntensity = 2; // m=2 là sweet spot
    private int aiDepth = 3;

    // GAME STATE
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
    private ZenBeliefSolver solver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(30, 50, 30, 30);

        karmaText = new AppCompatTextView(this);
        karmaText.setTextColor(Color.WHITE);
        karmaText.setTextSize(32);
        karmaText.setGravity(Gravity.CENTER);
        root.addView(karmaText);

        statusText = new AppCompatTextView(this);
        statusText.setTextColor(Color.CYAN);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        AppCompatButton btnAuto = new AppCompatButton(this);
        btnAuto.setText("🔮 BELIEF AI");
        btnAuto.setOnClickListener(v -> toggleAuto());
        root.addView(btnAuto);

        grid = new GridLayout(this);
        grid.setColumnCount(4);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        startNewGame();
    }

    private void startNewGame() {
        solver = new ZenBeliefSolver(totalBits);
        
        realValues.clear();
        for (int i = 0; i < totalBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues);
        
        targetValue = 0;
        int bitsToOn = rnd.nextInt(totalBits - 1) + 1;
        for (int i = 0; i < bitsToOn; i++) targetValue += realValues.get(i);
        Collections.shuffle(realValues);

        buttonStates = new boolean[totalBits];
        buttons = new AppCompatButton[totalBits];
        currentTurn = 0; currentSum = 0;

        grid.removeAllViews();
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

        int[] k = calculateKarmaPair();
        if (k[0] + k[1] == 0) {
            stopAuto();
            new AlertDialog.Builder(this).setTitle("SOLVED").show();
        } else if (currentTurn >= maxTurns) {
            triggerPhysicalSwap();
        }
    }

    // --- PHYSICAL SWAP (GAME WORLD) ---
    private void triggerPhysicalSwap() {
        Toast.makeText(this, "ENTROPY INJECTION!", Toast.LENGTH_SHORT).show();
        // Thực hiện flip m bit ngẫu nhiên
        for (int i = 0; i < swapIntensity; i++) {
            int idx = rnd.nextInt(totalBits);
            buttonStates[idx] = !buttonStates[idx];
        }
        
        // Reset turn (Game rule: sau swap được chơi tiếp, nhưng AI không được biết trước để lợi dụng)
        currentTurn = 0;
        
        currentSum = 0;
        for (int i = 0; i < totalBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);
        updateUI();
        for(int i=0; i<totalBits; i++) updateButtonVisual(i);
    }

    private int[] calculateKarmaPair() {
        int k_on = 0; int k_off = 0;
        for (int i = 0; i < totalBits; i++) {
            long val = realValues.get(i);
            boolean t = (targetValue & val) != 0;
            boolean c = (currentSum & val) != 0;
            if (c && !t) k_on++;
            if (!c && t) k_off++;
        }
        return new int[]{k_on, k_off};
    }

    private void toggleAuto() {
        isAutoRunning = !isAutoRunning;
        if (isAutoRunning) handler.post(autoRunnable); else stopAuto();
    }
    private void stopAuto() { isAutoRunning = false; handler.removeCallbacks(autoRunnable); }

    private Runnable autoRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoRunning) return;
            int[] k = calculateKarmaPair();
            int on = 0; for(boolean b : buttonStates) if(b) on++;
            int off = totalBits - on;

            // AI gọi Solver
            int action = solver.decideAction(k[0], k[1], on, off, maxTurns - currentTurn, swapIntensity, aiDepth);
            
            List<Integer> c = new ArrayList<>();
            if (action == 0) { for(int i=0; i<totalBits; i++) if(buttonStates[i]) c.add(i); }
            else { for(int i=0; i<totalBits; i++) if(!buttonStates[i]) c.add(i); }

            if (!c.isEmpty()) {
                performMove(c.get(rnd.nextInt(c.size())));
                handler.postDelayed(this, 500);
            } else stopAuto();
        }
    };

    private void updateUI() {
        int[] k = calculateKarmaPair();
        karmaText.setText("K: " + (k[0]+k[1]) + " (" + k[0] + "|" + k[1] + ")");
        statusText.setText("Turn: " + currentTurn + "/" + maxTurns);
    }
    private void updateButtonVisual(int i) {
        buttons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(buttonStates[i] ? Color.YELLOW : Color.DKGRAY));
        buttons[i].setText(buttonStates[i] ? "ON" : "OFF");
    }

    // ==========================================
    // ZEN BELIEF SOLVER (CORRECT EXPECTIMAX)
    // ==========================================
    public static class ZenBeliefSolver {
        private int N;
        private Map<String, Double> memo = new HashMap<>();

        public ZenBeliefSolver(int n) { this.N = n; }

        public int decideAction(int k_on, int k_off, int on, int off, int turnsLeft, int m, int depth) {
            memo.clear();
            double ev_Off = (on > 0) ? getEV(k_on, k_off, on, off, 0, turnsLeft, m, depth) : 999;
            double ev_On = (off > 0) ? getEV(k_on, k_off, on, off, 1, turnsLeft, m, depth) : 999;
            return (ev_Off < ev_On) ? 0 : 1;
        }

        private double getEV(int k_on, int k_off, int on, int off, int action, int turnsLeft, int m, int depth) {
            // TRANSITION LOGIC
            double pSuccess;
            int s_k_on, s_k_off, s_on, s_off;
            int f_k_on, f_k_off, f_on, f_off;

            if (action == 0) { // TẮT
                pSuccess = (double) k_on / on;
                s_k_on = k_on - 1; s_k_off = k_off; s_on = on - 1; s_off = off + 1;
                f_k_on = k_on; f_k_off = k_off + 1; f_on = on - 1; f_off = off + 1;
            } else { // BẬT
                pSuccess = (double) k_off / off;
                s_k_on = k_on; s_k_off = k_off - 1; s_on = on + 1; s_off = off - 1;
                f_k_on = k_on + 1; f_k_off = k_off; f_on = on + 1; f_off = off - 1;
            }

            double valS = solve(s_k_on, s_k_off, s_on, s_off, turnsLeft - 1, m, depth - 1);
            double valF = solve(f_k_on, f_k_off, f_on, f_off, turnsLeft - 1, m, depth - 1);

            return (pSuccess * valS) + ((1.0 - pSuccess) * valF);
        }

        private double solve(int k_on, int k_off, int on, int off, int turnsLeft, int m, int depth) {
            // 1. Terminal: Win
            if (k_on + k_off == 0) return 0;

            // 2. Terminal: Depth Limit (Heuristic = Karma)
            if (depth == 0) return k_on + k_off;

            // 3. CHANCE NODE: SWAP HAPPENS
            if (turnsLeft <= 0) {
                // ĐÂY LÀ ĐIỂM SỬA QUAN TRỌNG:
                // Không reset turnsLeft.
                // Không đệ quy sâu.
                // Chỉ tính Expected Karma của trạng thái ngay sau khi Swap (Belief State Update).
                return calculatePostSwapBelief(k_on, k_off, on, off, m);
            }

            String key = k_on + "_" + k_off + "_" + on + "_" + turnsLeft + "_" + depth;
            if (memo.containsKey(key)) return memo.get(key);

            double ev_Off = (on > 0) ? getEV(k_on, k_off, on, off, 0, turnsLeft, m, depth) : 999;
            double ev_On = (off > 0) ? getEV(k_on, k_off, on, off, 1, turnsLeft, m, depth) : 999;

            double res = Math.min(ev_Off, ev_On);
            memo.put(key, res);
            return res;
        }

        // Tính Kỳ Vọng Karma sau khi bị tiêm nhiễu m lần
        // Đây là hàm Kernel xấp xỉ phân phối hậu nghiệm
        private double calculatePostSwapBelief(int k_on, int k_off, int on, int off, int m) {
            if (m == 0) return k_on + k_off; // Không còn nhiễu

            // Vì m nhỏ (1-3), ta có thể duyệt đệ quy 4 nhánh để tính chính xác
            // Nhưng không cần độ sâu turnsLeft, chỉ cần giá trị karma
            
            double ev = 0;
            
            // Case 1: Bad ON -> Good OFF (Giảm k_on)
            if (k_on > 0) {
                double p = (double) k_on / N;
                ev += p * calculatePostSwapBelief(k_on - 1, k_off, on - 1, off + 1, m - 1);
            }
            // Case 2: Good ON -> Bad OFF (Tăng k_off)
            if (on > k_on) {
                double p = (double) (on - k_on) / N;
                ev += p * calculatePostSwapBelief(k_on, k_off + 1, on - 1, off + 1, m - 1);
            }
            // Case 3: Bad OFF -> Good ON (Giảm k_off)
            if (k_off > 0) {
                double p = (double) k_off / N;
                ev += p * calculatePostSwapBelief(k_on, k_off - 1, on + 1, off - 1, m - 1);
            }
            // Case 4: Good OFF -> Bad ON (Tăng k_on)
            if (off > k_off) {
                double p = (double) (off - k_off) / N;
                ev += p * calculatePostSwapBelief(k_on + 1, k_off, on + 1, off - 1, m - 1);
            }
            
            return ev;
        }
    }
}
