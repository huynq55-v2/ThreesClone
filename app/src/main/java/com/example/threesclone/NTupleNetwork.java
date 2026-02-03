package com.example.threesclone;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * N-Tuple Network for value approximation.
 * Uses a list of patterns (tuples) and their corresponding weight tables.
 */
public class NTupleNetwork implements Serializable {
    private static final long serialVersionUID = 1L;

    // Max value code 0-14 (covers up to tile 6144)
    private static final int MAX_VAL_CODE = 15;
    
    // Patterns and weights
    public List<int[]> tuples = new ArrayList<>();
    public List<float[]> weights = new ArrayList<>();

    public NTupleNetwork() {
        // 1. Define Patterns (Tuples)
        addRows();
        addCols();
        addSquares2x2();
        addSnakes();
        addRectangles2x3(); // 6-cell patterns for chain recognition
        
        // 2. Initialize Weight Tables
        initWeights();
    }

    private void addRows() {
        for (int r = 0; r < 4; r++) {
            tuples.add(new int[]{r * 4 + 0, r * 4 + 1, r * 4 + 2, r * 4 + 3});
        }
    }

    private void addCols() {
        for (int c = 0; c < 4; c++) {
            tuples.add(new int[]{0 * 4 + c, 1 * 4 + c, 2 * 4 + c, 3 * 4 + c});
        }
    }

    private void addSquares2x2() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                tuples.add(new int[]{
                    r * 4 + c, r * 4 + (c + 1),
                    (r + 1) * 4 + c, (r + 1) * 4 + (c + 1)
                });
            }
        }
    }

    private void addSnakes() {
        // Snake zigzag patterns in 4 corners
        tuples.add(new int[]{0, 1, 5, 4}); 
        tuples.add(new int[]{3, 2, 6, 7});
        tuples.add(new int[]{12, 13, 9, 8});
        tuples.add(new int[]{15, 14, 10, 11});
    }

    private void addRectangles2x3() {
        // Horizontal rectangles: 2 rows x 3 cols (6 positions each)
        // Creates 6 patterns: rows 0-1, 1-2, 2-3 × cols 0-2, 1-3
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 2; c++) {
                tuples.add(new int[]{
                    r * 4 + c, r * 4 + (c + 1), r * 4 + (c + 2),
                    (r + 1) * 4 + c, (r + 1) * 4 + (c + 1), (r + 1) * 4 + (c + 2)
                });
            }
        }
        
        // Vertical rectangles: 3 rows x 2 cols (6 positions each)
        // Creates 6 patterns: rows 0-2, 1-3 × cols 0-1, 1-2, 2-3
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                tuples.add(new int[]{
                    r * 4 + c, r * 4 + (c + 1),
                    (r + 1) * 4 + c, (r + 1) * 4 + (c + 1),
                    (r + 2) * 4 + c, (r + 2) * 4 + (c + 1)
                });
            }
        }
    }

    private void initWeights() {
        for (int[] t : tuples) {
            int tableSize = (int) Math.pow(MAX_VAL_CODE, t.length);
            weights.add(new float[tableSize]);
        }
    }

    // Encoding: 0 -> 0, 1 -> 1, 2 -> 2, 3+ -> log2(v/3)+3
    private int encodeTile(int value) {
        if (value == 0) return 0;
        if (value == 1) return 1;
        if (value == 2) return 2;
        int code = (int)(Math.log(value / 3.0) / Math.log(2)) + 3;
        return Math.min(code, MAX_VAL_CODE - 1);
    }

    // Compute index for a tuple (Base 15)
    private int getIndex(int[] tuple, int[] codes) {
        int index = 0;
        for (int pos : tuple) {
            index = index * MAX_VAL_CODE + codes[pos];
        }
        return index;
    }

    public float predict(Tile[][] board) {
        float sum = 0;
        int[] codes = new int[16];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                codes[r * 4 + c] = encodeTile(board[r][c].value);
            }
        }

        for (int i = 0; i < tuples.size(); i++) {
            int idx = getIndex(tuples.get(i), codes);
            sum += weights.get(i)[idx];
        }
        return sum;
    }

    public void train(Tile[][] board, float targetG, float learningRate) {
        float currentPred = predict(board);
        float error = targetG - currentPred;
        float delta = error * learningRate;
        float splitDelta = delta / tuples.size();

        int[] codes = new int[16];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                codes[r * 4 + c] = encodeTile(board[r][c].value);
            }
        }

        for (int i = 0; i < tuples.size(); i++) {
            int idx = getIndex(tuples.get(i), codes);
            weights.get(i)[idx] += splitDelta;
        }
    }
}
