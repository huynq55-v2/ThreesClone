package com.example.threesclone;

public class Tile {
    public int value;

    public Tile(int value) {
        this.value = value;
    }

    public boolean isEmpty() {
        return value == 0;
    }

    // Logic Rank giống hệt Rust
    public int getRank() {
        if (value == 0) return 0;
        if (value == 1) return 0; // Trong logic gốc game rank 1,2 coi như thấp nhất
        if (value == 2) return 0;
        // log2(value/3) + 1
        return (int) (Math.log(value / 3.0) / Math.log(2)) + 1;
    }
}
