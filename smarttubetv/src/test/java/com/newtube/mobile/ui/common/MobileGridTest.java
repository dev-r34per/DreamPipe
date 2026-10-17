package com.newtube.mobile.ui.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MobileGridTest {
    @Test
    public void phonePortraitUsesOneFullWidthCard() {
        assertEquals(1, MobileGrid.computeSpanCountForWidthDp(412));
    }

    @Test
    public void phoneLandscapeKeepsCompactReadableRows() {
        assertEquals(1, MobileGrid.computeSpanCountForWidthDp(915));
    }

    @Test
    public void wideTabletUsesTwoCompactColumns() {
        assertEquals(2, MobileGrid.computeSpanCountForWidthDp(1280));
    }

    @Test
    public void spanCountNeverDropsBelowOne() {
        assertEquals(1, MobileGrid.computeSpanCountForWidthDp(0));
    }
}
