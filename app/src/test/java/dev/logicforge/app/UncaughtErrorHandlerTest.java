package dev.logicforge.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

class UncaughtErrorHandlerTest {

    @Test
    void theSummaryNamesTheInnermostCause() {
        Throwable failure = new RuntimeException("wrapper",
                new UncheckedIOException(new IOException("disk full")));

        assertEquals("IOException: disk full", UncaughtErrorHandler.summary(failure));
    }

    @Test
    void aFailureWithoutMessageIsSummarisedByItsType() {
        assertEquals("NullPointerException", UncaughtErrorHandler.summary(new NullPointerException()));
    }

    @Test
    void theSameFailureFromTheSamePlaceHasTheSameSignature() {
        assertEquals(UncaughtErrorHandler.signature(fail("x")), UncaughtErrorHandler.signature(fail("x")));
        assertNotEquals(UncaughtErrorHandler.signature(fail("x")), UncaughtErrorHandler.signature(fail("y")));
    }

    private static IllegalStateException fail(String message) {
        return new IllegalStateException(message);
    }
}
