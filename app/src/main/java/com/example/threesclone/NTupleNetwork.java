package com.example.threesclone;

import java.io.Serializable;

public class NTupleNetwork implements Serializable {
    private static final long serialVersionUID = 1L;

    // Kích thước bảng nhớ: 4 ô, mỗi ô max code 14 (Tile 6144) -> 15^4
    private static final int MAX_VAL_CODE = 15;
    private static final int TABLE_SIZE = 50625; // 15^4

    // Bảng nhớ (Weights) - Dùng chung cho các tuple đối xứng để tiết kiệm RAM & học nhanh hơn
    public float[] tableRow = new float[TABLE_SIZE];    // 4 Hàng ngang
    public float[] tableCol = new float[TABLE_SIZE];    // 4 Hàng dọc
    public float[] tableSquare = new float[TABLE_SIZE]; // 9 Ô vuông 2x2

    // Mã hóa giá trị Tile sang code nhỏ gọn (0-14)
    private int encodeTile(int value) {
        if (value == 0) return 0;
        if (value == 1) return 1;
        if (value == 2) return 2;
        // Công thức: log2(value/3) + 3
        // 3->3, 6->4, 12->5 ...
        int code = (int)(Math.log(value / 3.0) / Math.log(2)) + 3;
        return Math.min(code, MAX_VAL_CODE - 1);
    }

    // Mã hóa 4 ô thành 1 index duy nhất (Hệ cơ số 15)
    private int getIndex(int c1, int c2, int c3, int c4) {
        return c1 * 3375 + c2 * 225 + c3 * 15 + c4;
    }

    // --- DỰ ĐOÁN GIÁ TRỊ (FORWARD) ---
    public float predict(Tile[][] board) {
        float sum = 0;
        int[][] codes = new int[4][4];

        // 1. Pre-calculate codes
        for(int r=0; r<4; r++)
            for(int c=0; c<4; c++)
                codes[r][c] = encodeTile(board[r][c].value);

        // 2. Quét 4 Hàng Ngang
        for(int r=0; r<4; r++) {
            int idx = getIndex(codes[r][0], codes[r][1], codes[r][2], codes[r][3]);
            sum += tableRow[idx];
        }

        // 3. Quét 4 Hàng Dọc
        for(int c=0; c<4; c++) {
            int idx = getIndex(codes[0][c], codes[1][c], codes[2][c], codes[3][c]);
            sum += tableCol[idx];
        }

        // 4. Quét 9 Ô Vuông 2x2
        for(int r=0; r<3; r++) {
            for(int c=0; c<3; c++) {
                int idx = getIndex(codes[r][c], codes[r][c+1], codes[r+1][c], codes[r+1][c+1]);
                sum += tableSquare[idx];
            }
        }

        return sum;
    }

    // --- HỌC (BACKWARD) ---
    public void train(Tile[][] board, float targetG, float learningRate) {
        float currentPred = predict(board);
        float error = targetG - currentPred;
        float delta = error * learningRate;

        // Chia nhỏ delta cho 17 tuples (4 row + 4 col + 9 square)
        float splitDelta = delta / 17.0f;

        int[][] codes = new int[4][4];
        for(int r=0; r<4; r++) for(int c=0; c<4; c++) codes[r][c] = encodeTile(board[r][c].value);

        // Update Row
        for(int r=0; r<4; r++) {
            int idx = getIndex(codes[r][0], codes[r][1], codes[r][2], codes[r][3]);
            tableRow[idx] += splitDelta;
        }
        // Update Col
        for(int c=0; c<4; c++) {
            int idx = getIndex(codes[0][c], codes[1][c], codes[2][c], codes[3][c]);
            tableCol[idx] += splitDelta;
        }
        // Update Square
        for(int r=0; r<3; r++) {
            for(int c=0; c<3; c++) {
                int idx = getIndex(codes[r][c], codes[r][c+1], codes[r+1][c], codes[r+1][c+1]);
                tableSquare[idx] += splitDelta;
            }
        }
    }
}
