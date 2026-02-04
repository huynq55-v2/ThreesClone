package com.example.threesclone;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

/**
 * N-Tuple Network for value approximation.
 * SHARED SNAKE ARCHITECTURE - Compatible with Rust MessagePack format.
 * 
 * Key concepts:
 * - 12 Snake Window positions (sliding 5-cell window on S-shaped path)
 * - Each window has 8 symmetry variants (4 rotations × 2 mirrors)
 * - All 8 variants SHARE the same weight table (weight sharing)
 * - Total: 96 tuples, but only 12 weight tables
 */
public class NTupleNetwork implements Serializable {
    private static final long serialVersionUID = 4L;

    // Max value code 0-14 (covers up to tile 6144)
    private static final int MAX_VAL_CODE = 15;
    private static final int TUPLE_SIZE = 5;
    private static final int TABLE_SIZE = (int) Math.pow(MAX_VAL_CODE, TUPLE_SIZE); // 759375
    
    // Pre-computed encoding map for fast lookup
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
    
    // Snake path (S-shaped traversal of 4x4 board)
    private static final int[] SNAKE_PATH = {0, 1, 2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12};
    
    /**
     * TupleConfig - matches Rust struct
     * indices: positions on board (e.g., [0,1,2,3,7])
     * weightIndex: which weight table to use
     */
    public static class TupleConfig {
        public int[] indices;
        public int weightIndex;
        
        public TupleConfig(int[] indices, int weightIndex) {
            this.indices = indices;
            this.weightIndex = weightIndex;
        }
    }
    
    // Network structure
    public List<TupleConfig> tuples = new ArrayList<>();  // 96 snake variants
    public List<float[]> weights = new ArrayList<>();     // Only 12 weight tables
    public float alpha = 0.1f;
    public float gamma = 0.995f;
    
    // Potential weights (loaded from MessagePack, used for PBRS)
    public float wEmpty = 0.0f;
    public float wSnake = 0.0f;

    public NTupleNetwork() {
        addSharedSnake();
        ensureBuffer();
    }
    
    private void ensureBuffer() {
        if (codesBuffer == null) {
            codesBuffer = new int[16];
        }
    }

    // ============== SNAKE GENERATION (Match Rust exactly) ==============

    private void addSharedSnake() {
        // Sliding window of 5 cells on snake path
        for (int i = 0; i <= SNAKE_PATH.length - TUPLE_SIZE; i++) {
            // 1. Create weight table (Master)
            weights.add(new float[TABLE_SIZE]);
            int currentWeightId = weights.size() - 1;
            
            // 2. Extract base indices from snake path
            int[] baseIndices = new int[TUPLE_SIZE];
            for (int j = 0; j < TUPLE_SIZE; j++) {
                baseIndices[j] = SNAKE_PATH[i + j];
            }
            
            // 3. Generate 8 symmetry variants (Slaves) pointing to same Master
            addSymmetriesShared(baseIndices, currentWeightId);
        }
    }
    
    private void addSymmetriesShared(int[] baseTuple, int weightId) {
        List<int[]> variants = new ArrayList<>();
        int[] currentTuple = baseTuple.clone();
        
        // Generate 4 rotations (0°, 90°, 180°, 270°)
        for (int rot = 0; rot < 4; rot++) {
            // 1. Add current rotation
            variants.add(currentTuple.clone());
            
            // 2. Add mirrored version of current rotation
            int[] mirrored = new int[currentTuple.length];
            for (int i = 0; i < currentTuple.length; i++) {
                mirrored[i] = mirror(currentTuple[i]);
            }
            variants.add(mirrored);
            
            // 3. Rotate 90° for next iteration
            int[] rotated = new int[currentTuple.length];
            for (int i = 0; i < currentTuple.length; i++) {
                rotated[i] = rotate90(currentTuple[i]);
            }
            currentTuple = rotated;
        }
        
        // Deduplicate (some symmetric patterns produce duplicates)
        List<int[]> unique = new ArrayList<>();
        for (int[] v : variants) {
            boolean isDuplicate = false;
            for (int[] u : unique) {
                if (arraysEqual(v, u)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                unique.add(v);
            }
        }
        
        // Add to tuples list
        for (int[] v : unique) {
            tuples.add(new TupleConfig(v, weightId));
        }
    }
    
    // Rotate 90° clockwise: (row, col) -> (col, 3 - row)
    private int rotate90(int idx) {
        int r = idx / 4;
        int c = idx % 4;
        return c * 4 + (3 - r);
    }
    
    // Mirror horizontal: (row, col) -> (row, 3 - col)
    private int mirror(int idx) {
        int r = idx / 4;
        int c = idx % 4;
        return r * 4 + (3 - c);
    }
    
    private boolean arraysEqual(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    // ============== ENCODING ==============

    public static int encodeTile(int value) {
        if (value > 6144) return 14;
        if (value < 0) return 0;
        return ENCODE_MAP[value];
    }

    // ============== PREDICT (with weight sharing) ==============

    public float predict(Tile[][] board) {
        ensureBuffer();
        
        // Encode all tiles once
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                codesBuffer[r * 4 + c] = encodeTile(board[r][c].value);
            }
        }

        float sum = 0;
        for (TupleConfig tuple : tuples) {
            // Calculate index from tuple positions
            int index = 0;
            for (int pos : tuple.indices) {
                index = index * MAX_VAL_CODE + codesBuffer[pos];
            }
            // Read from SHARED weight table
            sum += weights.get(tuple.weightIndex)[index];
        }
        return sum;
    }
    
    // Overload for flat board array (used internally)
    public float predict(int[] board16) {
        float sum = 0;
        for (TupleConfig tuple : tuples) {
            int index = 0;
            for (int pos : tuple.indices) {
                index = index * MAX_VAL_CODE + encodeTile(board16[pos]);
            }
            sum += weights.get(tuple.weightIndex)[index];
        }
        return sum;
    }

    // ============== POTENTIAL FUNCTIONS (PBRS) ==============
    
    // Snake weight pattern (S-shaped traversal priorities)
    private static final float[] SNAKE_WEIGHTS = {
        // Row 0 (highest priority)
        65536.0f, 32768.0f, 16384.0f, 8192.0f,
        // Row 1 (reversed)
        512.0f, 1024.0f, 2048.0f, 4096.0f,
        // Row 2
        256.0f, 128.0f, 64.0f, 32.0f,
        // Row 3 (lowest)
        2.0f, 4.0f, 8.0f, 16.0f
    };
    
    // Calculate empty cell count
    public float calculateEmpty(Tile[][] board) {
        int count = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if (board[r][c].value == 0) {
                    count++;
                }
            }
        }
        return (float) count;
    }
    
    // Calculate snake score (best of 4 corners)
    public float calculateSnake(Tile[][] board) {
        float maxScore = 0;
        
        // 1. Top-Left (Normal)
        float score = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int idx = r * 4 + c;
                float rank = getRank(board[r][c].value);
                score += rank * SNAKE_WEIGHTS[idx];
            }
        }
        if (score > maxScore) maxScore = score;
        
        // 2. Top-Right (Mirror Horizontal)
        score = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int idx = r * 4 + c;
                float rank = getRank(board[r][3 - c].value);
                score += rank * SNAKE_WEIGHTS[idx];
            }
        }
        if (score > maxScore) maxScore = score;
        
        // 3. Bottom-Left (Mirror Vertical)
        score = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int idx = r * 4 + c;
                float rank = getRank(board[3 - r][c].value);
                score += rank * SNAKE_WEIGHTS[idx];
            }
        }
        if (score > maxScore) maxScore = score;
        
        // 4. Bottom-Right (Mirror Both)
        score = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int idx = r * 4 + c;
                float rank = getRank(board[3 - r][3 - c].value);
                score += rank * SNAKE_WEIGHTS[idx];
            }
        }
        if (score > maxScore) maxScore = score;
        
        return maxScore;
    }
    
    // Get rank of tile value
    private float getRank(int val) {
        if (val <= 2) return 0.0f;
        return (float)(Math.log(val / 3.0) / Math.log(2)) + 1.0f;
    }
    
    // Get composite potential (using loaded weights)
    public float getCompositePotential(Tile[][] board) {
        float phiEmpty = calculateEmpty(board);
        float phiSnake = calculateSnake(board);
        return (wEmpty * phiEmpty) + (wSnake * phiSnake);
    }
    
    // Get total value = Brain prediction + Potential
    public float getTotalValue(Tile[][] board) {
        return predict(board) + getCompositePotential(board);
    }

    // ============== MESSAGEPACK I/O (Rust Compatible) ==============

    /**
     * Load from MessagePack format (Rust rmp_serde compatible)
     * 
     * Format:
     * - Array of TupleConfig (each: {indices: [int], weight_index: int})
     * - Array of weight tables (each: [float])
     * - alpha: float
     * - gamma: float
     */
    public void loadFromMessagePack(InputStream is) throws Exception {
        byte[] allBytes = readAllBytes(is);
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(allBytes);
        
        // 1. Read tuples array
        int numTuples = unpacker.unpackArrayHeader();
        tuples.clear();
        for (int i = 0; i < numTuples; i++) {
            // Each tuple is a map with 2 keys: "indices" and "weight_index"
            int mapSize = unpacker.unpackMapHeader();
            int[] indices = null;
            int weightIndex = 0;
            
            for (int j = 0; j < mapSize; j++) {
                String key = unpacker.unpackString();
                if (key.equals("indices")) {
                    int arrLen = unpacker.unpackArrayHeader();
                    indices = new int[arrLen];
                    for (int k = 0; k < arrLen; k++) {
                        indices[k] = (int) unpacker.unpackLong();
                    }
                } else if (key.equals("weight_index")) {
                    weightIndex = (int) unpacker.unpackLong();
                }
            }
            tuples.add(new TupleConfig(indices, weightIndex));
        }
        
        // 2. Read weights array
        int numWeightTables = unpacker.unpackArrayHeader();
        weights.clear();
        for (int i = 0; i < numWeightTables; i++) {
            int tableSize = unpacker.unpackArrayHeader();
            float[] table = new float[tableSize];
            for (int j = 0; j < tableSize; j++) {
                table[j] = unpacker.unpackFloat();
            }
            weights.add(table);
        }
        
        // 3. Read alpha and gamma
        alpha = unpacker.unpackFloat();
        gamma = unpacker.unpackFloat();
        
        unpacker.close();
        ensureBuffer();
    }
    
    // Legacy binary format loader (for backwards compatibility)
    public void loadFromBinary(InputStream is) throws Exception {
        byte[] allBytes = readAllBytes(is);
        if (allBytes.length == 0) return;

        // THỨ TỰ ƯU TIÊN 1: Thử load bằng MessagePack (vì đây là chuẩn mới)
        try {
            android.util.Log.d("AI_LOAD", "Attempting MessagePack load...");
            loadFromMessagePackBytes(allBytes);
            android.util.Log.d("AI_LOAD", "MessagePack Load Success!");
            return; // Thành công thì thoát luôn
        } catch (Exception e) {
            android.util.Log.w("AI_LOAD", "MessagePack failed: " + e.getMessage());
        }

        // THỨ TỰ ƯU TIÊN 2: Nếu MessagePack lỗi, thử Legacy nhưng phải CỰC KỲ CẨN THẬN
        try {
            android.util.Log.d("AI_LOAD", "Attempting Legacy Binary load...");
            
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(allBytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            
            int numTables = buffer.getInt();
            
            // KIỂM TRA ĐỘ AN TOÀN (Sanity Check)
            // Nếu numTables > 1000 hoặc số lượng quá vô lý, dừng ngay không cấp phát RAM
            if (numTables <= 0 || numTables > 1000) {
                throw new Exception("Vô lý! Số lượng table (" + numTables + ") quá lớn hoặc sai định dạng.");
            }

            // Chỉ chạy tiếp nếu numTables hợp lý
            this.tuples.clear();
            this.weights.clear();
            for (int i = 0; i < numTables; i++) {
                int tableSize = buffer.getInt();
                if (tableSize <= 0 || tableSize > 1000000) throw new Exception("Table size quá lớn");
                
                float[] table = new float[tableSize];
                for (int j = 0; j < tableSize; j++) {
                    table[j] = buffer.getFloat();
                }
                this.weights.add(table);
            }
            android.util.Log.d("AI_LOAD", "Legacy Load Success!");
            
        } catch (Exception e) {
            android.util.Log.e("AI_LOAD", "Tất cả các phương thức load đều thất bại: " + e.getMessage());
            throw new Exception("File model không đúng định dạng hoặc bị hỏng.");
        }
    }
    
    private void loadFromMessagePackBytes(byte[] data) throws Exception {
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);
        
        // Đọc Map Header (Số lượng trường trong Struct Rust)
        int mapSize = unpacker.unpackMapHeader();
        
        for (int m = 0; m < mapSize; m++) {
            String key = unpacker.unpackString();
            
            switch (key) {
                case "tuples":
                    int numTuples = unpacker.unpackArrayHeader();
                    this.tuples.clear();
                    for (int i = 0; i < numTuples; i++) {
                        // Mỗi tuple trong Rust là 1 struct -> Map trong MsgPack
                        int tupleMapSize = unpacker.unpackMapHeader();
                        int[] indices = null;
                        int weightIndex = 0;
                        for (int j = 0; j < tupleMapSize; j++) {
                            String tKey = unpacker.unpackString();
                            if (tKey.equals("indices")) {
                                int arrLen = unpacker.unpackArrayHeader();
                                indices = new int[arrLen];
                                for (int k = 0; k < arrLen; k++) indices[k] = (int)unpacker.unpackLong();
                            } else if (tKey.equals("weight_index")) {
                                weightIndex = (int)unpacker.unpackLong();
                            }
                        }
                        this.tuples.add(new TupleConfig(indices, weightIndex));
                    }
                    break;

                case "weights":
                    int numTables = unpacker.unpackArrayHeader();
                    this.weights.clear();
                    for (int i = 0; i < numTables; i++) {
                        int tableLen = unpacker.unpackArrayHeader();
                        float[] table = new float[tableLen];
                        for (int j = 0; j < tableLen; j++) table[j] = unpacker.unpackFloat();
                        this.weights.add(table);
                    }
                    break;

                case "alpha": this.alpha = unpacker.unpackFloat(); break;
                case "gamma": this.gamma = unpacker.unpackFloat(); break;
                case "w_empty": this.wEmpty = unpacker.unpackFloat(); break;
                case "w_snake": this.wSnake = unpacker.unpackFloat(); break;
                
                default:
                    // QUAN TRỌNG: Nếu gặp key lạ (ví dụ w_disorder), phải skip nó 
                    // để không làm lệch pointer của unpacker
                    unpacker.skipValue();
                    break;
            }
        }
        unpacker.close();
    }
    
    private void loadFromLegacyBinary(byte[] data) throws Exception {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        
        int numTables = buffer.getInt();
        
        // Rebuild tuples structure based on number of tables
        // Legacy format didn't store tuples, only weights
        tuples.clear();
        weights.clear();
        
        // Just load weights, regenerate structure
        addSharedSnake();
        weights.clear(); // Clear auto-generated empty weights
        
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

    private byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }
    
    // Debug info
    public String getNetworkInfo() {
        return String.format("NTupleNetwork: %d tuples, %d weight tables, table_size=%d",
            tuples.size(), weights.size(), TABLE_SIZE);
    }
    
    // Export to binary (for saving to internal storage)
    public void exportToBinary(java.io.OutputStream os) throws Exception {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(4 + weights.size() * 4 + 
            weights.stream().mapToInt(w -> w.length).sum() * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        
        buffer.putInt(weights.size());
        for (float[] table : weights) {
            buffer.putInt(table.length);
            for (float w : table) {
                buffer.putFloat(w);
            }
        }
        
        os.write(buffer.array());
        os.flush();
    }
}
