package com.example.threesclone;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * N-Tuple Network for value approximation.
 * Compatible with Rust binary format (Little Endian).
 */
public class NTupleNetwork implements Serializable {
    private static final long serialVersionUID = 2L;

    // Max value code 0-14 (covers up to tile 6144)
    private static final int MAX_VAL_CODE = 15;
    
    // Patterns and weights
    public List<int[]> tuples = new ArrayList<>();
    public List<float[]> weights = new ArrayList<>();

    public NTupleNetwork() {
        // 1. Define Patterns (Tuples) - Must match Rust exactly!
        addRows();
        addCols();
        addSquares2x2();
        addSnakes();
        
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
        // Snake zigzag patterns in 4 corners - Must match Rust!
        tuples.add(new int[]{0, 1, 5, 4}); 
        tuples.add(new int[]{3, 2, 6, 7});
        tuples.add(new int[]{12, 13, 9, 8});
        tuples.add(new int[]{15, 14, 10, 11});
    }

    private void initWeights() {
        weights.clear();
        for (int[] t : tuples) {
            int tableSize = (int) Math.pow(MAX_VAL_CODE, t.length);
            weights.add(new float[tableSize]);
        }
    }

    // Encoding: Must match Rust exactly!
    // 0 -> 0, 1 -> 1, 2 -> 2, 3+ -> log2(v/3)+3
    public static int encodeTile(int value) {
        if (value == 0) return 0;
        if (value == 1) return 1;
        if (value == 2) return 2;
        int code = (int)(Math.log(value / 3.0) / Math.log(2)) + 3;
        return Math.min(code, 14);
    }

    // Compute index for a tuple (Base 15) - Must match Rust!
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

    // ============== BINARY I/O (Rust Compatible - Little Endian) ==============

    /**
     * Load weights from Rust-exported binary file.
     * Format: [numTables:u32] [tableSize:u32, weights:f32[]]...
     */
    public void loadFromBinary(InputStream is) throws Exception {
        byte[] allBytes = readAllBytes(is);
        ByteBuffer buffer = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN);

        int numTables = buffer.getInt();
        
        // Validate: must match our tuple count
        if (numTables != tuples.size()) {
            throw new Exception("Model mismatch: file has " + numTables + 
                " tables, but Java expects " + tuples.size());
        }

        weights.clear();
        for (int i = 0; i < numTables; i++) {
            int tableSize = buffer.getInt();
            float[] table = new float[tableSize];
            for (int j = 0; j < tableSize; j++) {
                table[j] = buffer.getFloat();
            }
            weights.add(table);
        }
    }

    /**
     * Export weights to Rust-compatible binary file.
     * Format: [numTables:u32] [tableSize:u32, weights:f32[]]...
     */
    public void exportToBinary(OutputStream os) throws Exception {
        // Calculate total size
        int totalFloats = 0;
        for (float[] table : weights) {
            totalFloats += table.length;
        }
        int totalBytes = 4 + weights.size() * 4 + totalFloats * 4;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        
        // 1. Write number of tables
        buffer.putInt(weights.size());
        
        // 2. Write each table
        for (float[] table : weights) {
            buffer.putInt(table.length);
            for (float weight : table) {
                buffer.putFloat(weight);
            }
        }
        
        os.write(buffer.array());
        os.flush();
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }
}
