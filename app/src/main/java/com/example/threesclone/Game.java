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
    
    // Evaluation Mode: ALWAYS EXPECTIMAX (Q = R + gamma * V)
    public double gamma = 0.995; // Default if brain not loaded

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
    public double calculateMoveReward(double phiOld, double phiNew) {
        double gamma = (brain != null) ? brain.gamma : 0.995;
        return (gamma * phiNew) - phiOld;
    }
    
    /**
     * Calculate Move Quality for a given board state
     * Q(s,a) = Base Reward + gamma * TotalValue(s')
     * This is what we display to teach the player
     */
    public double getMoveQuality(double scoreGain, Tile[][] newBoard) {
        if (brain == null) return scoreGain;
        double gamma = brain.gamma;
        double futureValue = brain.getTotalValue(newBoard);
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
    
    private double getTileScore(int val) {
        if (val < 3) return 0.0;
        int rank = (int) (Math.log(val / 3.0) / Math.log(2)) + 1;
        return Math.pow(3, rank);
    }

    // Process single row on a board copy, returns SCORE GAIN from merges (double)
    // Returns Double.NaN if no movement possible
    private double processSingleRowOnBoard(Tile[][] board, int r) {
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
                // Calculate Reward: score(new) - [score(target) + score(source)]
                double gain = getTileScore(newVal) - (getTileScore(target) + getTileScore(source));
                
                board[r][c] = new Tile(newVal);
                for (int k = c + 1; k < 3; k++) {
                    board[r][k] = board[r][k+1];
                }
                board[r][3] = new Tile(0);
                return gain;
            }
        }
        return Double.NaN; // NaN means no movement possible
    }
    
    // Result holder for simulation
    private static class SimulationResult {
        double totalScoreGain = 0.0;
        List<Integer> movedRows = new ArrayList<>();
    }
    
    // Simulate shift on board copy, returns score gain and moved rows
    private SimulationResult simulateShiftOnBoard(Tile[][] board) {
        SimulationResult result = new SimulationResult();
        for (int r = 0; r < 4; r++) {
            double gain = processSingleRowOnBoard(board, r);
            if (!Double.isNaN(gain)) {
                result.totalScoreGain += gain;
                result.movedRows.add(r);
            }
        }
        return result;
    }
    
    /**
     * Evaluate a move using Expectimax.
     * Formula: Q(s,a) = Immediate Reward (R) + gamma * Expected Future Value (V)
     * @param dir Direction to evaluate
     * @return Move Quality (Q), or -Double.MAX_VALUE if move is invalid
     */
    // Toggle for Minimax (Safe) vs Expectimax (Avg)
    public boolean useSafeMinimax = false;

    public double evaluateMove(Direction dir) {
        if (brain == null) return 0.0;
        if (useSafeMinimax) {
            return evaluateMoveSafe(dir);
        } else {
            return evaluateMoveExpectimax(dir);
        }
    }

    /**
     * Minimax Safe Policy: Q = R + gamma * min(V_outcomes)
     * Robustness against worst-case spawn.
     * Uses brain.predict() directly (No Potential), as requested.
     */
    public double evaluateMoveSafe(Direction dir) {
        if (!canMove(dir)) return -Double.MAX_VALUE;
        
        int rot = getRotationsNeeded(dir);
        Tile[][] tempBoard = rotateBoardCopy(board, rot);
        
        SimulationResult sim = simulateShiftOnBoard(tempBoard);
        if (sim.movedRows.isEmpty()) return -Double.MAX_VALUE;
        
        double R = sim.totalScoreGain;
        double currentGamma = brain.gamma;
        
        List<Integer> possibleValues = hints.isEmpty() ? 
            java.util.Arrays.asList(1, 2, 3) : hints;
            
        double minQuality = Double.MAX_VALUE;
        boolean hasOutcomes = false;

        for (int row : sim.movedRows) {
            for (int hintVal : possibleValues) {
                Tile[][] evalBoard = cloneBoard(tempBoard);
                evalBoard[row][3] = new Tile(hintVal);
                Tile[][] finalBoard = rotateBoardCopy(evalBoard, 4 - rot);
                
                // CORE LOGIC: min( R + gamma * V_predict )
                // Note: Using predict() directly to exclude Potential, per safe policy requirements
                double futureV = brain.predict(finalBoard);
                double quality = R + (currentGamma * futureV);
                
                if (quality < minQuality) {
                    minQuality = quality;
                }
                hasOutcomes = true;
            }
        }
        
        return hasOutcomes ? minQuality : -Double.MAX_VALUE;
    }

    /**
     * Expectimax: Q = R + gamma * avg(V_outcomes)
     * Original logic, preserving potential + predict
     */
    public double evaluateMoveExpectimax(Direction dir) {
        if (!canMove(dir)) return -Double.MAX_VALUE;
        
        int rot = getRotationsNeeded(dir);
        Tile[][] tempBoard = rotateBoardCopy(board, rot);
        
        // 1. Get Immediate Reward (R) through simulation
        SimulationResult sim = simulateShiftOnBoard(tempBoard);
        if (sim.movedRows.isEmpty()) return -Double.MAX_VALUE;
        
        double R = sim.totalScoreGain;
        
        // 2. Calculate Expected Future Value (V)
        List<Integer> possibleValues = hints.isEmpty() ? 
            java.util.Arrays.asList(1, 2, 3) : hints;
        
        double totalFutureV = 0.0;
        int count = 0;
        
        for (int row : sim.movedRows) {
            for (int hintVal : possibleValues) {
                Tile[][] evalBoard = cloneBoard(tempBoard);
                evalBoard[row][3] = new Tile(hintVal);
                Tile[][] finalBoard = rotateBoardCopy(evalBoard, 4 - rot);
                
                // Original: used getV() which includes Potential
                totalFutureV += getV(finalBoard); 
                count++;
            }
        }
        
        double expectedV = (count > 0) ? (totalFutureV / count) : 0.0;
        double currentGamma = brain.gamma;
        
        // 3. Return Q = R + gamma * V
        return R + (currentGamma * expectedV);
    }
    
    public Direction getBestMove() {
        Direction bestDir = null;
        double bestValue = -Double.MAX_VALUE;
        
        for (Direction dir : Direction.values()) {
            double value = evaluateMove(dir);
            if (value > bestValue) {
                bestValue = value;
                bestDir = dir;
            }
        }
        return bestDir;
    }
    
    public String getEvalModeName() {
        return "📊 EXPECTIMAX (R+γV)";
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

    public double getMoveConfidence(Direction chosenDir) {
        double[] qValues = new double[4];
        Direction[] dirs = Direction.values();
        double maxQ = -Double.MAX_VALUE;
        double minQ = Double.MAX_VALUE;

        // 1. Tính Q cho 4 hướng
        for (int i = 0; i < 4; i++) {
            qValues[i] = evaluateMove(dirs[i]);
            if (qValues[i] != -Double.MAX_VALUE) {
                if (qValues[i] > maxQ) maxQ = qValues[i];
                if (qValues[i] < minQ) minQ = qValues[i];
            }
        }

        // 2. Tính xem hướng đã chọn chiếm bao nhiêu % trong tổng "độ tốt"
        double chosenQ = qValues[chosenDir.ordinal()];
        if (chosenQ == -Double.MAX_VALUE) return 0.0;

        double sumDiff = 0.0;
        for (double q : qValues) {
            if (q != -Double.MAX_VALUE) sumDiff += (q - minQ + 1);
        }

        return (sumDiff > 0) ? (chosenQ - minQ + 1) / sumDiff : 0.0;
    }

    // --- BỔ SUNG LOGIC POTENTIAL (EMPTY & SNAKE) ---
    // Các hàm này đã được chuyển sang NTupleNetwork
    // Giữ lại để backward compatibility nếu cần

    public double calculateEmpty(Tile[][] boardState) {
        return brain != null ? brain.calculateEmpty(boardState) : 0.0;
    }

    public double calculateSnake(Tile[][] boardState) {
        return brain != null ? brain.calculateSnake(boardState) : 0.0;
    }

    /**
     * HÀM TỔNG HỢP V(s): Sử dụng công thức từ Rust
     * V(s) = Brain Prediction + Composite Potential
     * Composite Potential = (w_empty * phi_empty) + (w_snake * phi_snake) 
     *                     + (w_merge * phi_merge) - (w_disorder * phi_disorder)
     */
    public double getV(Tile[][] boardState) {
        if (brain == null) return 0.0;
        
        // Delegate to NTupleNetwork which has the complete formula
        return brain.getTotalValue(boardState);
    }
}
