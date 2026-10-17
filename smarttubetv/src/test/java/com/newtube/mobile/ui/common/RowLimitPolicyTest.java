package com.newtube.mobile.ui.common;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RowLimitPolicyTest {
    @Test public void fullSpanResultsConsumeRealRows() {
        assertEquals(2, RowLimitPolicy.visibleCount(100, 2, 2, index -> index == 1));
        assertEquals(1, RowLimitPolicy.visibleCount(100, 3, 1, index -> index == 0));
    }
    @Test public void listGridAndUnlimitedRemainReachable() {
        assertEquals(5, RowLimitPolicy.visibleCount(100, 1, 5, index -> false));
        assertEquals(10, RowLimitPolicy.visibleCount(100, 2, 5, index -> false));
        assertEquals(100, RowLimitPolicy.visibleCount(100, 1, 0, index -> false));
    }
}
