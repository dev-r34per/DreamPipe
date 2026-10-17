package com.newtube.mobile.ui.common;

import java.util.function.IntPredicate;

/** Counts actual grid rows, including full-span channel entries. Zero means unlimited. */
public final class RowLimitPolicy {
    private RowLimitPolicy() {}

    public static int visibleCount(int size, int columns, int maximumRows, IntPredicate fullSpan) {
        if (maximumRows <= 0) return size;
        columns = Math.max(1, columns);
        int rows = 0;
        int cells = 0;
        int visible = 0;
        for (int i = 0; i < size; i++) {
            if (fullSpan.test(i)) {
                if (cells > 0) { rows++; cells = 0; }
                if (rows >= maximumRows) break;
                rows++;
            } else {
                if (cells == 0 && rows >= maximumRows) break;
                if (++cells == columns) { rows++; cells = 0; }
            }
            visible++;
        }
        return visible;
    }
}
