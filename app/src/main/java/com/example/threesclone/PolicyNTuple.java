package com.example.threesclone;

import java.io.Serializable;

/**
 * Policy N-Tuple Network for action selection.
 * Contains 4 N-Tuple networks, one for each direction.
 * 0: UP, 1: DOWN, 2: LEFT, 3: RIGHT
 */
public class PolicyNTuple implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 4 networks: 0:UP, 1:DOWN, 2:LEFT, 3:RIGHT
    public NTupleNetwork[] actors = new NTupleNetwork[4];

    public PolicyNTuple() {
        for (int i = 0; i < 4; i++) {
            actors[i] = new NTupleNetwork();
        }
    }

    /**
     * Get the best action based on network predictions, filtered by legal moves.
     * @param legalMoves boolean array of size 4 [UP, DOWN, LEFT, RIGHT]
     * @return Direction enum for the best move
     */
    public Direction getBestAction(Tile[][] board, boolean[] legalMoves) {
        float maxVal = -Float.MAX_VALUE;
        int bestAction = -1;

        for (int i = 0; i < 4; i++) {
            if (!legalMoves[i]) continue;
            
            float val = actors[i].predict(board);
            if (val > maxVal) {
                maxVal = val;
                bestAction = i;
            }
        }

        if (bestAction == -1) {
            // Fallback: pick first legal move
            for (int i = 0; i < 4; i++) {
                if (legalMoves[i]) return actionToDirection(i);
            }
            return null; // Game Over
        }

        return actionToDirection(bestAction);
    }

    /**
     * Get action scores for all directions.
     * @return float array [UP, DOWN, LEFT, RIGHT]
     */
    public float[] getActionScores(Tile[][] board) {
        float[] scores = new float[4];
        for (int i = 0; i < 4; i++) {
            scores[i] = actors[i].predict(board);
        }
        return scores;
    }

    /**
     * Train the policy network using PPO teacher's action.
     * Uses one-hot encoding: target action gets 1.0, others get 0.0
     */
    public void train(Tile[][] board, int targetAction, float learningRate) {
        for (int i = 0; i < 4; i++) {
            float targetValue = (i == targetAction) ? 1.0f : 0.0f;
            actors[i].train(board, targetValue, learningRate);
        }
    }

    private Direction actionToDirection(int action) {
        switch (action) {
            case 0: return Direction.UP;
            case 1: return Direction.DOWN;
            case 2: return Direction.LEFT;
            case 3: return Direction.RIGHT;
            default: return Direction.UP;
        }
    }

    public static int directionToAction(Direction dir) {
        switch (dir) {
            case UP: return 0;
            case DOWN: return 1;
            case LEFT: return 2;
            case RIGHT: return 3;
            default: return 0;
        }
    }
}
