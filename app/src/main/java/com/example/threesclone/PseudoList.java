package com.example.threesclone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PseudoList {
    private List<Integer> template = new ArrayList<>();
    private List<Integer> pool = new ArrayList<>();
    private int multiplier;

    public PseudoList(int multiplier) {
        this.multiplier = multiplier;
    }

    public void add(int item) {
        template.add(item);
    }

    public void generateList() {
        pool.clear();
        for (int item : template) {
            for (int i = 0; i < multiplier; i++) {
                pool.add(item);
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(pool);
    }

    public int getNext() {
        if (pool.isEmpty()) {
            if (template.isEmpty()) return 0; // Error safe
            generateList();
            shuffle();
        }
        // Remove last
        return pool.remove(pool.size() - 1);
    }
}
