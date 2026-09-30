package dev.logicforge.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WindowSizeTest {

    @Test
    void aLargeScreenGetsThePreferredSize() {
        assertEquals(1360, LogicForgeApp.startSize(1360, 900, 2560));
    }

    @Test
    void aSmallerScreenGetsNinetyPercentButNotLessThanTheMinimum() {
        assertEquals(1152, LogicForgeApp.startSize(1360, 900, 1280));
        assertEquals(900, LogicForgeApp.startSize(1360, 900, 960));
    }

    @Test
    void aScreenSmallerThanTheMinimumIsFilled() {
        assertEquals(800, LogicForgeApp.startSize(1360, 900, 800));
    }
}
