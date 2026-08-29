package dev.logicforge.ui.render;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClockRendererTest {

    @Test
    void clockUsesDedicatedRenderer() {
        assertInstanceOf(ClockRenderer.class,
                RendererRegistry.standard().rendererFor("source.clock"));
    }

    @Test
    void squareWaveGeometryHasOrderedVerticalTransitionsAndDistinctRails() {
        ClockRenderer.GlyphGeometry glyph = ClockRenderer.geometry(20, 20);
        assertTrue(glyph.left() < glyph.firstRiseX());
        assertTrue(glyph.firstRiseX() < glyph.firstFallX());
        assertTrue(glyph.firstFallX() < glyph.secondRiseX());
        assertTrue(glyph.secondRiseX() < glyph.right());
        assertTrue(glyph.highY() < glyph.lowY());
    }

    @Test
    void glyphRemainsInsideSmallAndLargeComponentBodies() {
        for (double halfSize : new double[]{12, 20, 40}) {
            ClockRenderer.GlyphGeometry glyph = ClockRenderer.geometry(halfSize, halfSize);
            assertTrue(glyph.left() >= -halfSize);
            assertTrue(glyph.right() <= halfSize);
            assertTrue(glyph.highY() >= -halfSize);
            assertTrue(glyph.lowY() <= halfSize);
        }
    }
}
