package com.example.threesclone;

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

    // Consts
    private static final int K_NUMBER_RANDOMNESS = 4;
    private static final int K_SPECIAL_RARENESS = 20;
    private static final int K_START_SPAWN_NUMBERS = 9;

    public Game() {
        initGame();
    }

    public void initGame() {
        score = 0;
        numMove = 0;
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
            calculateScore();
        }

        // 4. Rotate back
        rotateBoard(4 - rot);
        
        checkGameOver();
        return anyMoved;
    }

    private boolean processSingleRow(int r) {
        for (int c = 0; c < 3; c++) {
            int target = board[r][c].value;
            int source = board[r][c+1].value;

            if (source == 0) continue;

            int newVal = -1;
            if (target == 0) {
                newVal = source;
            } else if (target + source == 3) { // 1+2 or 2+1
                newVal = 3;
            } else if (target >= 3 && target == source) { // X+X
                newVal = target * 2;
            }

            if (newVal != -1) {
                // Move performed
                board[r][c] = new Tile(newVal);
                // Shift rest
                for (int k = c + 1; k < 3; k++) {
                    board[r][k] = board[r][k+1];
                }
                board[r][3] = new Tile(0);
                return true;
            }
        }
        return false;
    }
    
    // --- Helper Logic ---
    public boolean canMove(Direction dir) {
        int rot = getRotationsNeeded(dir);
        // Tile[][] tempBoard = cloneBoard(board); // Unused logic in provided snippet
        // Rotate temp board logic manually or reusing helpers (trickier without pointers)
        // Simplification: Just rotate the internal board, check, then rotate back? 
        // Safer: Logic check without modifying state.
        
        // Let's implement direct check to avoid state mutation issues
        for (int r=0; r<4; r++) {
            for (int c=0; c<4; c++) {
                // Map coords based on rotation needed for Left alignment
                // Actually easier: just map neighbors based on Dir
                int nextR = r, nextC = c;
                switch(dir) {
                    case UP: nextR = r + 1; break; // Check element below moving up
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
    
    private Tile[][] cloneBoard(Tile[][] src) {
        Tile[][] dest = new Tile[4][4];
        for(int r=0;r<4;r++)
             for(int c=0;c<4;c++)
                 dest[r][c] = new Tile(src[r][c].value);
        return dest;
    }

    // --- Hint & Spawn Logic ---
    private int getNextValue() {
        boolean isBonus = false;
        if (numMove > 21) {
            // Check logic, peek doesn't consume in original code? 
            // In original Rust: special.get_next() CONSUMES.
            // But logic was: "if special.get_next() == 1".
            int val = special.getNext();
            if (val == 1) isBonus = true;
        }

        if (isBonus) {
            int maxRank = getHighestRank();
            int num = Math.max(maxRank - 3, 0); // K_SPECIAL_DEMOTION = 3
            if (num < 2) {
                // fall back to normal
            } else {
                if (num < 4) return getValueFromRank(num);
                else {
                    // Random 4 to num (inclusive)
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
                // Logic hiển thị hint: rank - 1 xuống dần
                int rIdx = (rank - 1) - i;
                if (rIdx < 1) rIdx = 1; 
                list.add(getValueFromRank(rIdx + 1)); // Hint thường hiện rank+1 để user biết sắp có con to?
                // Theo logic Rust: clamped_rank + 1
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
        for(int r=0; r<4; r++)
            for(int c=0; c<4; c++) {
                int rk = board[r][c].getRank();
                if(board[r][c].value > 2 && rk > max) max = rk;
            }
        return max;
    }

    private int getValueFromRank(int rank) {
        return 3 * (int)Math.pow(2, rank - 1);
    }
    
    private int getRankFromValue(int val) {
         if (val <= 2) return 0; // Simplified
         return (int) (Math.log(val / 3.0) / Math.log(2)) + 1;
    }
    
    private void calculateScore() {
        score = 0;
        for(int r=0; r<4; r++)
            for(int c=0; c<4; c++) {
                if (board[r][c].value >= 3) {
                    score += Math.pow(3, getRankFromValue(board[r][c].value));
                }
            }
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
