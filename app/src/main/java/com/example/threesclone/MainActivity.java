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
import android.widget.ScrollView;
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

    // --- BIẾN CẤU HÌNH (Sẽ được lấy từ UI) ---
    private int totalBits = 12;      // Tổng số bit
    private int maxTurns = 15;       // Số turn trước khi Swap xảy ra
    private int swapIntensity = 2;   // Số bit bị lật khi Swap (m)
    private int aiDepth = 3;         // Độ sâu tìm kiếm Expectimax

    // --- GAME STATE ---
    private long targetValue, currentSum = 0;
    private int currentTurn = 0;
    private List<Long> realValues = new ArrayList<>();
    private boolean[] buttonStates;
    
    // --- UI ELEMENTS ---
    private AppCompatButton[] buttons;
    private AppCompatTextView karmaText, statusText;
    private GridLayout grid;
    private AppCompatEditText inputBits, inputTurns, inputSwapM, inputDepth;
    private AppCompatButton btnAuto;

    // --- SYSTEM ---
    private boolean isAutoRunning = false;
    private Handler handler = new Handler();
    private Random rnd = new Random();
    private ZenBeliefSolver solver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout chính có ScrollView để tránh bị che khi bàn phím hiện lên
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(20, 30, 20, 20);
        scrollView.addView(root);

        // 1. PANEL CẤU HÌNH (INPUTS)
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setBackgroundColor(Color.rgb(20, 20, 20));
        controls.setPadding(10, 10, 10, 10);

        // Hàng 1: Bits & Turns
        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        inputBits = createInput(String.valueOf(totalBits));
        inputTurns = createInput(String.valueOf(maxTurns));
        addControlItem(row1, "Bits:", inputBits);
        addControlItem(row1, "Turns:", inputTurns);
        controls.addView(row1);

        // Hàng 2: Swap M & Depth
        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);
        inputSwapM = createInput(String.valueOf(swapIntensity));
        inputDepth = createInput(String.valueOf(aiDepth));
        addControlItem(row2, "Swap(m):", inputSwapM);
        addControlItem(row2, "Depth:", inputDepth);
        controls.addView(row2);

        // Nút Reset
        AppCompatButton btnReset = new AppCompatButton(this);
        btnReset.setText("🔄 ÁP DỤNG & RESET GAME");
        btnReset.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
        btnReset.setTextColor(Color.WHITE);
        btnReset.setOnClickListener(v -> { stopAuto(); startNewGame(); });
        controls.addView(btnReset);

        root.addView(controls);

        // 2. NÚT AUTO
        btnAuto = new AppCompatButton(this);
        btnAuto.setText("🔮 START BELIEF AI");
        btnAuto.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(100, 50, 150)));
        btnAuto.setTextColor(Color.WHITE);
        btnAuto.setOnClickListener(v -> toggleAuto());
        root.addView(btnAuto);

        // 3. DISPLAY
        karmaText = new AppCompatTextView(this);
        karmaText.setTextColor(Color.WHITE);
        karmaText.setTextSize(24);
        karmaText.setGravity(Gravity.CENTER);
        karmaText.setPadding(0, 20, 0, 5);
        root.addView(karmaText);

        statusText = new AppCompatTextView(this);
        statusText.setTextColor(Color.CYAN);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        // 4. GRID
        grid = new GridLayout(this);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        root.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(scrollView);
        
        // Bắt đầu game lần đầu
        startNewGame();
    }

    private void startNewGame() {
        // 1. Đọc cấu hình từ UI
        try {
            totalBits = Integer.parseInt(inputBits.getText().toString());
            maxTurns = Integer.parseInt(inputTurns.getText().toString());
            swapIntensity = Integer.parseInt(inputSwapM.getText().toString());
            aiDepth = Integer.parseInt(inputDepth.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi nhập liệu! Dùng mặc định.", Toast.LENGTH_SHORT).show();
        }

        // 2. Khởi tạo Solver mới với totalBits mới
        solver = new ZenBeliefSolver(totalBits);

        // 3. Random cấu hình Target (State ẩn)
        realValues.clear();
        for (int i = 0; i < totalBits; i++) realValues.add((long) Math.pow(2, i));
        Collections.shuffle(realValues); // Xáo trộn vị trí giá trị
        
        targetValue = 0;
        // Random số lượng bit cần bật (đảm bảo < totalBits để luôn có lời giải thú vị)
        int bitsToOn = rnd.nextInt(totalBits - 1) + 1;
        
        // Chọn ngẫu nhiên bitsToOn phần tử để cộng vào target
        List<Long> tempShuffle = new ArrayList<>(realValues);
        Collections.shuffle(tempShuffle);
        for (int i = 0; i < bitsToOn; i++) targetValue += tempShuffle.get(i);

        // 4. Reset trạng thái nút
        buttonStates = new boolean[totalBits];
        buttons = new AppCompatButton[totalBits];
        currentTurn = 0; 
        currentSum = 0;

        // 5. Vẽ lại Grid
        grid.removeAllViews();
        int colCount = (totalBits > 16) ? 5 : 4; // Tự động chỉnh cột cho đẹp
        grid.setColumnCount(colCount);
        
        for (int i = 0; i < totalBits; i++) {
            final int index = i;
            buttons[i] = new AppCompatButton(this);
            buttons[i].setTextSize(14);
            // Layout params cho nút đẹp hơn
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 150; // Chiều cao cố định
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); // Weight 1
            params.setMargins(5, 5, 5, 5);
            buttons[i].setLayoutParams(params);
            
            updateButtonVisual(i);
            buttons[i].setOnClickListener(v -> performMove(index));
            grid.addView(buttons[i]);
        }
        updateUI();
    }

    private void performMove(int index) {
        // Game Logic: Flip trạng thái
        buttonStates[index] = !buttonStates[index];
        currentTurn++;
        
        // Tính tổng hiện tại
        currentSum = 0;
        for (int i = 0; i < totalBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);
        
        updateButtonVisual(index);
        updateUI();

        // Check Win
        int[] k = calculateKarmaPair();
        if (k[0] + k[1] == 0) {
            stopAuto();
            new AlertDialog.Builder(this)
                .setTitle("GIÁC NGỘ (SOLVED)")
                .setMessage("Tâm đã định, nghiệp đã tan.")
                .setPositiveButton("OK", null)
                .show();
        } 
        // Check Swap Trigger
        else if (currentTurn >= maxTurns) {
            triggerEntropySwap();
        }
    }

    // --- GAME EVENT: ENTROPY INJECTION (SWAP) ---
    private void triggerEntropySwap() {
        Toast.makeText(this, "🌀 ENTROPY INJECTION!", Toast.LENGTH_SHORT).show();
        
        // Thực hiện flip m bit ngẫu nhiên (Vật lý)
        for (int i = 0; i < swapIntensity; i++) {
            int idx = rnd.nextInt(totalBits);
            buttonStates[idx] = !buttonStates[idx];
        }
        
        // Reset turn về 0 (để game tiếp tục chơi)
        currentTurn = 0;
        
        // Tính lại tổng
        currentSum = 0;
        for (int i = 0; i < totalBits; i++) if (buttonStates[i]) currentSum += realValues.get(i);
        
        // Update UI toàn bộ
        updateUI();
        for(int i=0; i<totalBits; i++) updateButtonVisual(i);
    }

    // --- ORACLE: Cung cấp (k_on, k_off) cho UI và AI ---
    private int[] calculateKarmaPair() {
        int k_on = 0; int k_off = 0;
        for (int i = 0; i < totalBits; i++) {
            long val = realValues.get(i);
            boolean t = (targetValue & val) != 0;
            boolean c = (currentSum & val) != 0;
            if (c && !t) k_on++;   // Thừa (Bad ON)
            if (!c && t) k_off++;  // Thiếu (Bad OFF)
        }
        return new int[]{k_on, k_off};
    }

    // --- AUTO PLAY CONTROL ---
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
        btnAuto.setText("🔮 START BELIEF AI");
        btnAuto.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(100, 50, 150)));
        handler.removeCallbacks(autoRunnable); 
    }

    private Runnable autoRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAutoRunning) return;
            
            // 1. Lấy thông tin từ Oracle
            int[] k = calculateKarmaPair();
            int on = 0; for(boolean b : buttonStates) if(b) on++;
            int off = totalBits - on;

            // 2. AI suy nghĩ (Truyền tham số cấu hình hiện tại vào)
            int action = solver.decideAction(k[0], k[1], on, off, maxTurns - currentTurn, swapIntensity, aiDepth);
            
            // 3. Thực hiện hành động (Random trong nhóm target)
            List<Integer> candidates = new ArrayList<>();
            if (action == 0) { // AI muốn TẮT
                for(int i=0; i<totalBits; i++) if(buttonStates[i]) candidates.add(i);
            } else { // AI muốn BẬT
                for(int i=0; i<totalBits; i++) if(!buttonStates[i]) candidates.add(i);
            }

            if (!candidates.isEmpty()) {
                // Random 1 nút trong nhóm để thực hiện
                performMove(candidates.get(rnd.nextInt(candidates.size())));
                handler.postDelayed(this, 400); // Tốc độ chơi
            } else {
                // Trường hợp hiếm: AI muốn Tắt nhưng không còn nút nào On (hoặc ngược lại)
                stopAuto();
            }
        }
    };

    // --- UI HELPERS ---
    private void updateUI() {
        int[] k = calculateKarmaPair();
        karmaText.setText("K_ON: " + k[0] + " | K_OFF: " + k[1] + "\n(Total Karma: " + (k[0]+k[1]) + ")");
        statusText.setText("Turn: " + currentTurn + " / " + maxTurns);
        
        // Đổi màu cảnh báo sắp Swap
        if (currentTurn >= maxTurns - 3) statusText.setTextColor(Color.RED);
        else statusText.setTextColor(Color.CYAN);
    }

    private void updateButtonVisual(int i) {
        buttons[i].setBackgroundTintList(ColorStateList.valueOf(buttonStates[i] ? Color.YELLOW : Color.DKGRAY));
        buttons[i].setText(buttonStates[i] ? "ON" : "OFF");
        buttons[i].setTextColor(buttonStates[i] ? Color.BLACK : Color.WHITE);
    }

    private AppCompatEditText createInput(String def) {
        AppCompatEditText et = new AppCompatEditText(this);
        et.setText(def); 
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(Color.WHITE); 
        et.setGravity(Gravity.CENTER);
        et.setBackgroundColor(Color.rgb(40, 40, 40));
        et.setWidth(120);
        return et;
    }

    private void addControlItem(LinearLayout p, String label, AppCompatEditText et) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(10, 0, 10, 0);

        AppCompatTextView tv = new AppCompatTextView(this);
        tv.setText(label); 
        tv.setTextColor(Color.LTGRAY);
        tv.setTextSize(12);
        
        container.addView(tv); 
        container.addView(et);
        p.addView(container);
    }

    // ==========================================================
    // CLASS: ZEN BELIEF SOLVER (Option A + Entropy Injection)
    // ==========================================================
    public static class ZenBeliefSolver {
        private int N;
        private Map<String, Double> memo = new HashMap<>();

        public ZenBeliefSolver(int totalBits) { 
            this.N = totalBits; 
        }

        public int decideAction(int k_on, int k_off, int on, int off, int turnsLeft, int m, int depth) {
            memo.clear();
            
            // So sánh EV của 2 hành động
            // Nếu không có nút On thì gán EV cực lớn để không chọn
            double ev_Off = (on > 0) ? getEV(k_on, k_off, on, off, 0, turnsLeft, m, depth) : 9999;
            // Nếu không có nút Off thì gán EV cực lớn
            double ev_On = (off > 0) ? getEV(k_on, k_off, on, off, 1, turnsLeft, m, depth) : 9999;
            
            // 0: Tắt, 1: Bật
            return (ev_Off < ev_On) ? 0 : 1;
        }

        private double getEV(int k_on, int k_off, int on, int off, int action, int turnsLeft, int m, int depth) {
            double pSuccess;
            int s_k_on, s_k_off, s_on, s_off; // Trạng thái nếu thành công
            int f_k_on, f_k_off, f_on, f_off; // Trạng thái nếu thất bại

            if (action == 0) { // Action: TẮT (ON -> OFF)
                pSuccess = (double) k_on / on;
                
                // Success: Tắt trúng bit thừa -> K_on giảm
                s_k_on = k_on - 1; s_k_off = k_off; 
                s_on = on - 1; s_off = off + 1;
                
                // Fail: Tắt nhầm bit đúng -> Nó trở thành thiếu (K_off tăng)
                f_k_on = k_on; f_k_off = k_off + 1; 
                f_on = on - 1; f_off = off + 1;
                
            } else { // Action: BẬT (OFF -> ON)
                pSuccess = (double) k_off / off;
                
                // Success: Bật trúng bit thiếu -> K_off giảm
                s_k_on = k_on; s_k_off = k_off - 1; 
                s_on = on + 1; s_off = off - 1;
                
                // Fail: Bật nhầm bit đúng -> Nó trở thành thừa (K_on tăng)
                f_k_on = k_on + 1; f_k_off = k_off; 
                f_on = on + 1; f_off = off - 1;
            }

            double valS = solve(s_k_on, s_k_off, s_on, s_off, turnsLeft - 1, m, depth - 1);
            double valF = solve(f_k_on, f_k_off, f_on, f_off, turnsLeft - 1, m, depth - 1);

            return (pSuccess * valS) + ((1.0 - pSuccess) * valF);
        }

        private double solve(int k_on, int k_off, int on, int off, int turnsLeft, int m, int depth) {
            // 1. Terminal: Solved (Karma = 0)
            if (k_on + k_off == 0) return 0;

            // 2. Terminal: Depth Limit -> Return Heuristic (Total Karma)
            if (depth == 0) return k_on + k_off;

            // 3. CHANCE NODE: SWAP EVENT (Entropy Injection)
            if (turnsLeft <= 0) {
                // Bức tường sương mù: Tính kỳ vọng Karma sau khi bị nhiễu m lần
                // Không đệ quy depth, không reset turn.
                return calculatePostSwapBelief(k_on, k_off, on, off, m);
            }

            // Memoization
            String key = k_on + "_" + k_off + "_" + on + "_" + turnsLeft + "_" + depth;
            if (memo.containsKey(key)) return memo.get(key);

            // Max Node (AI choice)
            double ev_Off = (on > 0) ? getEV(k_on, k_off, on, off, 0, turnsLeft, m, depth) : 9999;
            double ev_On = (off > 0) ? getEV(k_on, k_off, on, off, 1, turnsLeft, m, depth) : 9999;

            double res = Math.min(ev_Off, ev_On);
            memo.put(key, res);
            return res;
        }

        // Hàm Kernel: Tính phân phối xác suất sau m lần lật ngẫu nhiên
        private double calculatePostSwapBelief(int k_on, int k_off, int on, int off, int m) {
            if (m == 0) return k_on + k_off; // Hết nhiễu

            double ev = 0;
            
            // 4 Trường hợp Flip Ngẫu Nhiên (xác suất dựa trên mật độ lỗi hiện tại)
            // Case 1: Flip Bad ON -> Good OFF (Giảm k_on)
            if (k_on > 0) {
                double p = (double) k_on / N;
                ev += p * calculatePostSwapBelief(k_on - 1, k_off, on - 1, off + 1, m - 1);
            }
            // Case 2: Flip Good ON -> Bad OFF (Tăng k_off)
            if (on > k_on) {
                double p = (double) (on - k_on) / N;
                ev += p * calculatePostSwapBelief(k_on, k_off + 1, on - 1, off + 1, m - 1);
            }
            // Case 3: Flip Bad OFF -> Good ON (Giảm k_off)
            if (k_off > 0) {
                double p = (double) k_off / N;
                ev += p * calculatePostSwapBelief(k_on, k_off - 1, on + 1, off - 1, m - 1);
            }
            // Case 4: Flip Good OFF -> Bad ON (Tăng k_on)
            if (off > k_off) {
                double p = (double) (off - k_off) / N;
                ev += p * calculatePostSwapBelief(k_on + 1, k_off, on + 1, off - 1, m - 1);
            }
            
            return ev;
        }
    }
}
