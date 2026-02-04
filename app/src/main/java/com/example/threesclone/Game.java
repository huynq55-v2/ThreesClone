package com.example.threesclone;

import android.content.Context;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Game {
    public Tile[][] board = new Tile[4][4];
    public boolean gameOver = false;
    public int score = 0;
    public int numMove = 0;

    // Hint system
    public int futureValue = 0;
    public List<Integer> hints = new ArrayList<>();

    private PseudoList numbers;
    private PseudoList special;
    private Random rng = new Random();

    // AI Brain (Read-Only)
    public NTupleNetwork brain;
    private Context context;
    
    // Evaluation Mode: EXPECTIMAX (average) or SAFE (worst-case)
    public enum EvalMode { EXPECTIMAX, SAFE }
    public EvalMode evalMode = EvalMode.EXPECTIMAX;

    // Consts
    private static final int K_NUMBER_RANDOMNESS = 4;
    private static final int K_SPECIAL_RARENESS = 20;
    private static final int K_START_SPAWN_NUMBERS = 9;

    public Game(Context context) {
        this.context = context;
        loadBrain(); // Load saved brain if exists
        initGame();
    }

    public void initGame() {
        score = 0;
        numMove = 0;
        gameOver = false;
        gameOver = false;

        // Init Decks
        numbers = new PseudoList(K_NUMBER_RANDOMNESS);
        numbers.add(1); numbers.add(2); numbers.add(3);
        numbers.generateList(); numbers.shuffle();

        special = new PseudoList(1);
        special.add(1);
        for(int i=0; i<K_SPECIAL_RARENESS; i++) special.add(0);
        special.generateList(); special.shuffle();

        // Init Empty Board
        for(int r=0; r<4; r++)
            for(int c=0; c<4; c++)
                board[r][c] = new Tile(0);

        // Spawn initial tiles
        List<Integer> indices = new ArrayList<>();
        for(int i=0; i<16; i++) indices.add(i);
        Collections.shuffle(indices);

        for(int i=0; i<K_START_SPAWN_NUMBERS; i++) {
            int idx = indices.get(i);
            int r = idx / 4;
            int c = idx % 4;
            board[r][c] = new Tile(numbers.getNext());
        }

        // Setup initial future
        futureValue = getNextValue();
        hints = predictFuture();
    }

    // --- Core Logic Move ---
    public boolean move(Direction dir) {
        if (gameOver) return false;
        if (!canMove(dir)) return false;

        int scoreBefore = score; // Lưu điểm trước khi đi
        int rot = getRotationsNeeded(dir);

        // 1. Rotate to align with LEFT
        rotateBoard(rot);

        // 2. Shift Left Logic
        List<Integer> movedRows = new ArrayList<>();
        boolean anyMoved = false;

        for (int r = 0; r < 4; r++) {
            if (processSingleRow(r)) {
                movedRows.add(r);
                anyMoved = true;
            }
        }

        // 3. Spawn Logic
        if (anyMoved) {
            int targetRow = movedRows.get(rng.nextInt(movedRows.size()));
            int valToSpawn = getActualSpawnValue();
            board[targetRow][3] = new Tile(valToSpawn);

            numMove++;
            futureValue = getNextValue();
            hints = predictFuture();
            calculateScore(); // Cập nhật điểm số mới
            
            // --- GHI LỊCH SỬ ---
            int rewardThisStep = score - scoreBefore; // Điểm vừa kiếm được
            
            // Rotate back trước khi lưu để hình ảnh bàn cờ đúng chiều
            rotateBoard(4 - rot);
            
            checkGameOver();
            return true;
        } else {
            // Nếu không move được thì xoay về cũ
            rotateBoard(4 - rot);
            return false;
        }
    }

    private boolean processSingleRow(int r) {
        for (int c = 0; c < 3; c++) {
            int target = board[r][c].value;
            int source = board[r][c+1].value;

            if (source == 0) continue;

            int newVal = -1;
            if (target == 0) {
                newVal = source;
            } else if (target + source == 3) {
                newVal = 3;
            } else if (target >= 3 && target == source) {
                newVal = target * 2;
            }

            if (newVal != -1) {
                board[r][c] = new Tile(newVal);
                for (int k = c + 1; k < 3; k++) {
                    board[r][k] = board[r][k+1];
                }
                board[r][3] = new Tile(0);
                return true;
            }
        }
        return false;
    }

    public boolean canMove(Direction dir) {
        for (int r=0; r<4; r++) {
            for (int c=0; c<4; c++) {
                int nextR = r, nextC = c;
                switch(dir) {
                    case UP: nextR = r + 1; break;
                    case DOWN: nextR = r - 1; break;
                    case LEFT: nextC = c + 1; break;
                    case RIGHT: nextC = c - 1; break;
                }
                if (nextR < 0 || nextR >= 4 || nextC < 0 || nextC >= 4) continue;
                int target = board[r][c].value;
                int source = board[nextR][nextC].value;
                if (source == 0) continue;
                if (target == 0 || (target+source==3) || (target>=3 && target==source)) return true;
            }
        }
        return false;
    }

    private int getRotationsNeeded(Direction dir) {
        switch (dir) {
            case LEFT: return 0;
            case DOWN: return 1;
            case RIGHT: return 2;
            case UP: return 3;
        }
        return 0;
    }

    private void rotateBoard(int times) {
        times = times % 4;
        for (int k = 0; k < times; k++) {
            Tile[][] newBoard = new Tile[4][4];
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    newBoard[c][3 - r] = board[r][c];
                }
            }
            board = newBoard;
        }
    }

    // --- Hint & Spawn Logic ---
    private int getNextValue() {
        boolean isBonus = false;
        if (numMove > 21) {
            int val = special.getNext();
            if (val == 1) isBonus = true;
        }
        if (isBonus) {
            int maxRank = getHighestRank();
            int num = Math.max(maxRank - 3, 0);
            if (num < 2) { } 
            else {
                if (num < 4) return getValueFromRank(num);
                else {
                    int r = 4 + rng.nextInt(num - 4 + 1);
                    return getValueFromRank(r);
                }
            }
        }
        return numbers.getNext();
    }

    public List<Integer> predictFuture() {
        List<Integer> list = new ArrayList<>();
        if (futureValue <= 3) {
            list.add(futureValue);
        } else {
            int rank = getRankFromValue(futureValue);
            int num = Math.min(rank - 1, 3);
            for (int i = 0; i < num; i++) {
                int rIdx = (rank - 1) - i;
                if (rIdx < 1) rIdx = 1;
                list.add(getValueFromRank(rIdx + 1));
            }
            Collections.sort(list);
        }
        return list;
    }

    private int getActualSpawnValue() {
        if (futureValue > 3) {
            int rank = getRankFromValue(futureValue);
            int minR = Math.max(2, rank - 2);
            int actualRank = minR + rng.nextInt(rank - minR + 1);
            return getValueFromRank(actualRank);
        }
        return futureValue;
    }

    // --- Utils ---
    private int getHighestRank() {
        int max = 0;
        for(int r=0; r<4; r++) for(int c=0; c<4; c++) {
            int rk = board[r][c].getRank();
            if(board[r][c].value > 2 && rk > max) max = rk;
        }
        return max;
    }
    private int getValueFromRank(int rank) { return 3 * (int)Math.pow(2, rank - 1); }
    private int getRankFromValue(int val) {
        if (val <= 2) return 0;
        return (int) (Math.log(val / 3.0) / Math.log(2)) + 1;
    }
    private void calculateScore() {
        score = 0;
        for(int r=0; r<4; r++) for(int c=0; c<4; c++)
            if (board[r][c].value >= 3) score += Math.pow(3, getRankFromValue(board[r][c].value));
    }

    // --- AI Logic & PBRS ---

    /**
     * Calculate Shaping Reward using PBRS formula
     * F(s,s') = gamma * TotalValue(s') - TotalValue(s)
     */
    public float calculateMoveReward(float phiOld, float phiNew) {
        float gamma = (brain != null) ? brain.gamma : 0.995f;
        return (gamma * phiNew) - phiOld;
    }
    
    /**
     * Calculate Move Quality for a given board state
     * Q(s,a) = Base Reward + gamma * TotalValue(s')
     * This is what we display to teach the player
     */
    public float getMoveQuality(int scoreGain, Tile[][] newBoard) {
        if (brain == null) return (float) scoreGain;
        float gamma = brain.gamma;
        float futureValue = brain.getTotalValue(newBoard);
        return scoreGain + (gamma * futureValue);
    }

    // --- EXPECTIMAX MOVE EVALUATION (Fair AI - No Peeking) ---
    
    // Deep copy board
    private Tile[][] cloneBoard(Tile[][] src) {
        Tile[][] copy = new Tile[4][4];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                copy[r][c] = new Tile(src[r][c].value);
            }
        }
        return copy;
    }
    
    // Rotate a board copy (not the main board)
    private Tile[][] rotateBoardCopy(Tile[][] src, int times) {
        Tile[][] result = cloneBoard(src);
        times = times % 4;
        for (int k = 0; k < times; k++) {
            Tile[][] newBoard = new Tile[4][4];
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    newBoard[c][3 - r] = result[r][c];
                }
            }
            result = newBoard;
        }
        return result;
    }
    
    // Process single row on a board copy, returns true if moved
    private boolean processSingleRowOnBoard(Tile[][] board, int r) {
        for (int c = 0; c < 3; c++) {
            int target = board[r][c].value;
            int source = board[r][c+1].value;
            if (source == 0) continue;
            
            int newVal = -1;
            if (target == 0) {
                newVal = source;
            } else if (target + source == 3) {
                newVal = 3;
            } else if (target >= 3 && target == source) {
                newVal = target * 2;
            }
            
            if (newVal != -1) {
                board[r][c] = new Tile(newVal);
                for (int k = c + 1; k < 3; k++) {
                    board[r][k] = board[r][k+1];
                }
                board[r][3] = new Tile(0);
                return true;
            }
        }
        return false;
    }
    
    // Simulate shift on board copy, returns list of moved rows (aligned to LEFT)
    private List<Integer> simulateShiftOnBoard(Tile[][] board) {
        List<Integer> movedRows = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            if (processSingleRowOnBoard(board, r)) {
                movedRows.add(r);
            }
        }
        return movedRows;
    }
    
    /**
     * Evaluate a move using Expectimax (without cheating).
     * Uses hints list and all possible spawn positions to calculate expected potential.
     * @param dir Direction to evaluate
     * @return Expected potential after the move, or -Float.MAX_VALUE if move is invalid
     */
    public float evaluateMove(Direction dir) {
        if (brain == null) return 0f;
        if (!canMove(dir)) return -Float.MAX_VALUE;
        
        int rot = getRotationsNeeded(dir);
        Tile[][] tempBoard = rotateBoardCopy(board, rot);
        List<Integer> movedRows = simulateShiftOnBoard(tempBoard);
        
        if (movedRows.isEmpty()) return -Float.MAX_VALUE;
        
        List<Integer> possibleValues = hints.isEmpty() ? 
            java.util.Arrays.asList(1, 2, 3) : hints;
        
        float totalV = 0f;
        int count = 0;
        
        for (int row : movedRows) {
            for (int hintVal : possibleValues) {
                Tile[][] evalBoard = cloneBoard(tempBoard);
                evalBoard[row][3] = new Tile(hintVal);
                Tile[][] finalBoard = rotateBoardCopy(evalBoard, 4 - rot);
                
                // SỬA TẠI ĐÂY: Dùng hàm getV thống nhất
                totalV += getV(finalBoard); 
                count++;
            }
        }
        return count > 0 ? totalV / count : 0f;
    }

    public float evaluateSafeMove(Direction dir) {
        if (brain == null) return 0f;
        if (!canMove(dir)) return -Float.MAX_VALUE;
        
        int rot = getRotationsNeeded(dir);
        Tile[][] tempBoard = rotateBoardCopy(board, rot);
        List<Integer> movedRows = simulateShiftOnBoard(tempBoard);
        
        if (movedRows.isEmpty()) return -Float.MAX_VALUE;
        
        List<Integer> possibleValues = hints.isEmpty() ? 
            java.util.Arrays.asList(1, 2, 3) : hints;
        
        // Bắt đầu với một giá trị cực lớn để tìm Min
        float worstCaseV = Float.MAX_VALUE; 
        
        // Duyệt qua mọi vị trí rơi và mọi giá trị Tile có thể
        for (int row : movedRows) {
            for (int hintVal : possibleValues) {
                Tile[][] evalBoard = cloneBoard(tempBoard);
                evalBoard[row][3] = new Tile(hintVal);
                Tile[][] finalBoard = rotateBoardCopy(evalBoard, 4 - rot);
                
                // Lấy giá trị của bàn cờ này
                float currentV = getV(finalBoard); 
                
                // CHANCE NODE: Thay vì cộng dồn, ta lấy cái Tệ nhất (Min)
                if (currentV < worstCaseV) {
                    worstCaseV = currentV;
                }
            }
        }
        
        // Trả về kịch bản đen tối nhất của hướng đi này
        return worstCaseV;
    }
    
    /**
     * Get the best move direction using Expectimax (depth=1).
     * Uses Value N-Tuple to evaluate board state after each move.
     */
    public Direction getBestMove() {
        Direction bestDir = null;
        float bestValue = -Float.MAX_VALUE;
        
        for (Direction dir : Direction.values()) {
            // Dùng hàm evaluate tương ứng với mode đang chọn
            float value = (evalMode == EvalMode.SAFE) 
                ? evaluateSafeMove(dir) 
                : evaluateMove(dir);
            if (value > bestValue) {
                bestValue = value;
                bestDir = dir;
            }
        }
        return bestDir;
    }
    
    // Toggle chế độ đánh giá
    public void toggleEvalMode() {
        evalMode = (evalMode == EvalMode.EXPECTIMAX) ? EvalMode.SAFE : EvalMode.EXPECTIMAX;
    }
    
    public String getEvalModeName() {
        return (evalMode == EvalMode.SAFE) ? "🛡️ SAFE" : "📊 AVG";
    }

    // GỌI HÀM NÀY KHI BẤM NÚT "TRAIN"
    // GỌI HÀM NÀY KHI BẤM NÚT "TRAIN" - Chỉ train User Brain
    // --- Brain Management (Read-Only) ---
    
    public void loadBrain() {
        try {
            // Priority: Load externally provided brain file (brain.dat)
            FileInputStream fis = context.openFileInput("brain.dat");
            brain = new NTupleNetwork();
            brain.loadFromBinary(fis);
            fis.close();
        } catch (Exception e) {
            brain = new NTupleNetwork(); // Create empty brain if no file found
        }
    }
    
    // No saveBrain exposed publicly - Brain is read-only
    public void saveBrain() {
        try {
            FileOutputStream fos = context.openFileOutput("brain.dat", Context.MODE_PRIVATE);
            brain.exportToBinary(fos);
            fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void checkGameOver() {
        if (canMove(Direction.UP) || canMove(Direction.DOWN) ||
            canMove(Direction.LEFT) || canMove(Direction.RIGHT)) {
            gameOver = false;
        } else {
            gameOver = true;
        }
    }

    public float getMoveConfidence(Direction chosenDir) {
        float[] qValues = new float[4];
        Direction[] dirs = Direction.values();
        float maxQ = -Float.MAX_VALUE;
        float minQ = Float.MAX_VALUE;

        // 1. Tính Q cho 4 hướng (sử dụng cùng mode đang chọn)
        for (int i = 0; i < 4; i++) {
            qValues[i] = (evalMode == EvalMode.SAFE) 
                ? evaluateSafeMove(dirs[i]) 
                : evaluateMove(dirs[i]);
            if (qValues[i] != -Float.MAX_VALUE) {
                if (qValues[i] > maxQ) maxQ = qValues[i];
                if (qValues[i] < minQ) minQ = qValues[i];
            }
        }

        // 2. Tính xem hướng đã chọn chiếm bao nhiêu % trong tổng "độ tốt"
        float chosenQ = qValues[chosenDir.ordinal()];
        if (chosenQ == -Float.MAX_VALUE) return 0f;

        float sumDiff = 0;
        for (float q : qValues) {
            if (q != -Float.MAX_VALUE) sumDiff += (q - minQ + 1);
        }

        return (sumDiff > 0) ? (chosenQ - minQ + 1) / sumDiff : 0f;
    }

    // --- BỔ SUNG LOGIC POTENTIAL (EMPTY & SNAKE) ---
    // Để khớp hoàn toàn với bản Rust bác đang train

    public float calculateEmpty(Tile[][] boardState) {
        int count = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if (boardState[r][c].value == 0) count++;
            }
        }
        return (float) count;
    }

    public float calculateSnake(Tile[][] boardState) {
        // Pattern ZigZag chuẩn bác đã chốt
        float[] SNAKE_WEIGHTS = {
            1073741824.0f, 268435456.0f, 67108864.0f, 16777216.0f, 
            65536.0f,      262144.0f,    1048576.0f,  4194304.0f,  
            16384.0f,      4096.0f,      1024.0f,     256.0f,      
            1.0f,          4.0f,         16.0f,       64.0f        
        };

        float maxScore = 0;
        // Kiểm tra 4 góc (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
        // 1. Top-Left
        maxScore = Math.max(maxScore, getSnakeScore(boardState, SNAKE_WEIGHTS, 0));
        // 2. Top-Right (Mirror H)
        maxScore = Math.max(maxScore, getSnakeScore(boardState, SNAKE_WEIGHTS, 1));
        // 3. Bottom-Left (Mirror V)
        maxScore = Math.max(maxScore, getSnakeScore(boardState, SNAKE_WEIGHTS, 2));
        // 4. Bottom-Right (Mirror Both)
        maxScore = Math.max(maxScore, getSnakeScore(boardState, SNAKE_WEIGHTS, 3));

        return maxScore / 1073741824.0f; // CHỐT: Normalize về 1.0 như bản Rust
    }

    private float getSnakeScore(Tile[][] b, float[] weights, int mode) {
        float score = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int tr = r, tc = c;
                if (mode == 1) tc = 3 - c;
                else if (mode == 2) tr = 3 - r;
                else if (mode == 3) { tr = 3 - r; tc = 3 - c; }
                
                float rank = getRankFromValue(b[tr][tc].value);
                score += rank * weights[r * 4 + c];
            }
        }
        return score;
    }

    /**
     * HÀM TỔNG HỢP V(s): Thay thế cho calculatePotential và getCurrentPotential bị trùng lặp
     */
    public float getV(Tile[][] boardState) {
        if (brain == null) return 0;
        
        // V(s) = Neural Network Prediction + (w_empty * Phi_empty) + (w_snake * Phi_snake)
        float networkPredict = brain.predict(boardState);
        float phiEmpty = calculateEmpty(boardState);
        float phiSnake = calculateSnake(boardState);
        
        return networkPredict + (brain.wEmpty * phiEmpty) + (brain.wSnake * phiSnake);
    }
}
