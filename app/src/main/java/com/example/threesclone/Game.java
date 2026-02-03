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

    // AI & Training
    public NTupleNetwork brain; // Value Net
    public PolicyNTuple policyBrain; // Policy Net (4 heads for each direction)
    private Context context;
    
    // Cấu trúc lưu lịch sử để train
    public static class MoveRecord {
        Tile[][] boardState;
        int reward;
        
        public MoveRecord(Tile[][] src, int r) {
            this.boardState = new Tile[4][4];
            for(int i=0; i<4; i++) 
                for(int j=0; j<4; j++) 
                    this.boardState[i][j] = new Tile(src[i][j].value);
            this.reward = r;
        }
    }
    public List<MoveRecord> history = new ArrayList<>();

    // Consts
    private static final int K_NUMBER_RANDOMNESS = 4;
    private static final int K_SPECIAL_RARENESS = 20;
    private static final int K_START_SPAWN_NUMBERS = 9;

    public Game(Context context) {
        this.context = context;
        loadBrain();
        loadPolicyBrain(); // Load Policy Brain too
        initGame();
    }

    public void initGame() {
        score = 0;
        numMove = 0;
        gameOver = false;
        history.clear(); // Xóa lịch sử ván cũ

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
        
        // Lưu trạng thái đầu tiên
        history.add(new MoveRecord(board, 0));
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
            history.add(new MoveRecord(board, rewardThisStep));
            
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

    // --- AI Logic & Training ---

    // Hàm tính Reward thay cho PBRS cũ -> Dùng N-Tuple Brain
    public float calculatePotential() {
        if (brain == null) return 0;
        return brain.predict(board);
    }

    public float calculateMoveReward(float phiOld, float phiNew) {
        // Công thức PBRS: Reward = Gamma * Phi_New - Phi_Old
        return (0.99f * phiNew) - phiOld;
    }

    // Lấy giá trị Potential hiện tại cho UI
    public float getCurrentPotential() {
        if (brain == null) return 0f;
        return brain.predict(board);
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
        
        // 1. Clone and rotate board to align with LEFT
        Tile[][] tempBoard = rotateBoardCopy(board, rot);
        
        // 2. Simulate shift (get moved rows)
        List<Integer> movedRows = simulateShiftOnBoard(tempBoard);
        if (movedRows.isEmpty()) return -Float.MAX_VALUE;
        
        // 3. Use hints list (fair play - no peeking at futureValue)
        List<Integer> possibleValues = hints.isEmpty() ? 
            java.util.Arrays.asList(1, 2, 3) : hints;
        
        float totalPotential = 0f;
        int count = 0;
        
        // 4. Expectimax: Average over all spawn positions × all hint values
        for (int row : movedRows) {
            for (int hintVal : possibleValues) {
                // Clone the shifted board
                Tile[][] evalBoard = cloneBoard(tempBoard);
                
                // Place the tile at spawn position (column 3 for LEFT shift)
                evalBoard[row][3] = new Tile(hintVal);
                
                // Rotate back to original orientation before prediction
                Tile[][] finalBoard = rotateBoardCopy(evalBoard, 4 - rot);
                
                // Predict potential
                totalPotential += brain.predict(finalBoard);
                count++;
            }
        }
        
        return count > 0 ? totalPotential / count : 0f;
    }
    
    /**
     * Get the best move direction using Policy N-Tuple Network.
     * Falls back to Expectimax if policy brain is not trained.
     */
    public Direction getBestMove() {
        if (policyBrain != null) {
            boolean[] legalMoves = new boolean[4];
            legalMoves[0] = canMove(Direction.UP);
            legalMoves[1] = canMove(Direction.DOWN);
            legalMoves[2] = canMove(Direction.LEFT);
            legalMoves[3] = canMove(Direction.RIGHT);
            
            // Use Policy Network - filtered by valid moves
            Direction policyChoice = policyBrain.getBestAction(board, legalMoves);
            if (policyChoice != null) {
                return policyChoice;
            }
        }
        
        // Fallback to Expectimax
        Direction bestDir = null;
        float bestValue = -Float.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            float value = evaluateMove(dir);
            if (value > bestValue) {
                bestValue = value;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    // GỌI HÀM NÀY KHI BẤM NÚT "TRAIN"
    public void trainOnHistory() {
        if (history.isEmpty()) return;

        float G = 0; // Actual Return tích lũy
        float gamma = 0.99f;
        float learningRate = 0.0025f; // Tốc độ học

        // DUYỆT NGƯỢC (Monte Carlo)
        for (int i = history.size() - 1; i >= 0; i--) {
            MoveRecord step = history.get(i);
            
            // Công thức: G = Reward tại bước này + (Gamma * G tương lai)
            G = step.reward + (gamma * G);
            
            // Dạy AI: "Với thế cờ này (step.boardState), giá trị thực tế là G"
            brain.train(step.boardState, G, learningRate);
        }

        saveBrain(); // Lưu ngay vào bộ nhớ máy
        history.clear(); // Xóa lịch sử sau khi học
    }

    // --- KNOWLEDGE DISTILLATION: Load PPO Data ---
    // Format: "board|return|action" where action is 0:UP,1:DOWN,2:LEFT,3:RIGHT
    public int trainFromLogData(String logData) {
        String[] lines = logData.split("\n");
        float valueLR = 0.001f;   // Learning rate for Value Net
        float policyLR = 0.01f;   // Learning rate for Policy Net (higher)
        int count = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            try {
                String[] parts = line.split("\\|");
                if (parts.length < 2) continue;
                
                String[] boardStr = parts[0].trim().split(",");
                if (boardStr.length != 16) continue;
                
                float targetG = Float.parseFloat(parts[1].trim());
                
                // Tái tạo bàn cờ ảo
                Tile[][] dummyBoard = new Tile[4][4];
                for (int i = 0; i < 16; i++) {
                    int r = i / 4;
                    int c = i % 4;
                    int val = Integer.parseInt(boardStr[i].trim());
                    dummyBoard[r][c] = new Tile(val);
                }
                
                // Train Value Net
                brain.train(dummyBoard, targetG, valueLR);
                
                // Train Policy Net (if action column exists)
                if (parts.length >= 3) {
                    int action = Integer.parseInt(parts[2].trim());
                    policyBrain.train(dummyBoard, action, policyLR);
                }
                
                count++;
            } catch (Exception e) {
                // Skip invalid lines
            }
        }

        if (count > 0) {
            saveBrain();
            savePolicyBrain();
        }
        return count;
    }

    // --- Brain Management (Public) ---
    public void saveBrain() {
        try {
            FileOutputStream fos = context.openFileOutput("brain.dat", Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(brain);
            oos.close(); fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void loadBrain() {
        try {
            FileInputStream fis = context.openFileInput("brain.dat");
            ObjectInputStream ois = new ObjectInputStream(fis);
            brain = (NTupleNetwork) ois.readObject();
            ois.close();
        } catch (Exception e) {
            brain = new NTupleNetwork(); // Chưa có thì tạo não mới
        }
    }

    public void resetBrain() {
        brain = new NTupleNetwork();
        saveBrain();
    }

    // --- Policy Brain Management ---
    public void savePolicyBrain() {
        try {
            FileOutputStream fos = context.openFileOutput("policy_brain.dat", Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(policyBrain);
            oos.close(); fos.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void loadPolicyBrain() {
        try {
            FileInputStream fis = context.openFileInput("policy_brain.dat");
            ObjectInputStream ois = new ObjectInputStream(fis);
            policyBrain = (PolicyNTuple) ois.readObject();
            ois.close();
        } catch (Exception e) {
            policyBrain = new PolicyNTuple();
        }
    }

    public void resetPolicyBrain() {
        policyBrain = new PolicyNTuple();
        savePolicyBrain();
    }

    public void resetAllBrains() {
        resetBrain();
        resetPolicyBrain();
    }

    private void checkGameOver() {
        if (canMove(Direction.UP) || canMove(Direction.DOWN) ||
            canMove(Direction.LEFT) || canMove(Direction.RIGHT)) {
            gameOver = false;
        } else {
            gameOver = true;
        }
    }
}
