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
 * Optimized for Android performance.
 */
public class NTupleNetwork implements Serializable {
    private static final long serialVersionUID = 3L;

    // Max value code 0-14 (covers up to tile 6144)
    private static final int MAX_VAL_CODE = 15;
    
    // Pre-computed encoding map for fast lookup (avoid Math.log)
    private static final int[] ENCODE_MAP = new int[6145];
    static {
        ENCODE_MAP[0] = 0;
        ENCODE_MAP[1] = 1;
        ENCODE_MAP[2] = 2;
        for (int v = 3; v <= 6144; v++) {
            if (v % 3 == 0) {
                ENCODE_MAP[v] = Math.min((int)(Math.log(v / 3.0) / Math.log(2)) + 3, 14);
            }
        }
    }
    
    // Reusable buffer to avoid GC pressure
    private transient int[] codesBuffer = new int[16];
    
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
        
        // 3. Ensure buffer is initialized
        ensureBuffer();
    }
    
    private void ensureBuffer() {
        if (codesBuffer == null) {
            codesBuffer = new int[16];
        }
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

    // Fast encoding using lookup table
    public static int encodeTile(int value) {
        if (value > 6144) return 14;
        if (value < 0) return 0;
        return ENCODE_MAP[value];
    }

    // Optimized predict - reuses buffer and inlines index calculation
    public float predict(Tile[][] board) {
        ensureBuffer();
        
        // Encode all tiles once
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                codesBuffer[r * 4 + c] = encodeTile(board[r][c].value);
            }
        }

        float sum = 0;
        for (int i = 0; i < tuples.size(); i++) {
            int[] tuple = tuples.get(i);
            // Inline index calculation for speed
            int index = 0;
            for (int pos : tuple) {
                index = index * MAX_VAL_CODE + codesBuffer[pos];
            }
            sum += weights.get(i)[index];
        }
        return sum;
    }

    public void train(Tile[][] board, float targetG, float learningRate) {
        ensureBuffer();
        
        float currentPred = predict(board);
        float error = targetG - currentPred;
        float delta = error * learningRate;
        float splitDelta = delta / tuples.size();

        for (int i = 0; i < tuples.size(); i++) {
            int[] tuple = tuples.get(i);
            int index = 0;
            for (int pos : tuple) {
                index = index * MAX_VAL_CODE + codesBuffer[pos];
            }
            weights.get(i)[index] += splitDelta;
        }
    }

    // ============== BINARY I/O (Rust Compatible - Little Endian) ==============

    public void loadFromBinary(InputStream is) throws Exception {
        byte[] allBytes = readAllBytes(is);
        ByteBuffer buffer = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN);

        int numTables = buffer.getInt();
        
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
        
        ensureBuffer();
    }

    public void exportToBinary(OutputStream os) throws Exception {
        int totalFloats = 0;
        for (float[] table : weights) {
            totalFloats += table.length;
        }
        int totalBytes = 4 + weights.size() * 4 + totalFloats * 4;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(weights.size());
        
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
