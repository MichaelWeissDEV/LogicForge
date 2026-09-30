package dev.logicforge.app;

import dev.logicforge.ui.view.Dialogs;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.stage.Window;

/**
 * The last line of defence: an exception nothing else caught.
 *
 * <p>It is logged (with the full stack trace) and, while the user interface is running,
 * explained in a dialog whose technical details stay folded away. A failure that repeats —
 * say, on every repaint — is logged a few times and then only counted, and each distinct
 * failure gets at most one dialog, so the user is never trapped in a stream of messages.
 * In non-interactive runs (the packaging smoke test) any such failure ends the process with
 * a non-zero exit code instead.
 */
final class UncaughtErrorHandler implements Thread.UncaughtExceptionHandler {

    static final int FULLY_LOGGED_REPEATS = 3;

    private final AppLog log;
    private final boolean interactive;
    private final Map<String, Integer> occurrences = new HashMap<>();
    private final Set<String> explained = new HashSet<>();
    private volatile Supplier<Window> owner = () -> null;

    UncaughtErrorHandler(AppLog log, boolean interactive) {
        this.log = log;
        this.interactive = interactive;
    }

    /** Installs the handler for every thread that does not bring its own. */
    static UncaughtErrorHandler install(AppLog log, boolean interactive) {
        UncaughtErrorHandler handler = new UncaughtErrorHandler(log, interactive);
        Thread.setDefaultUncaughtExceptionHandler(handler);
        return handler;
    }

    /** The window error dialogs belong to, once there is one. */
    void setOwner(Supplier<Window> owner) {
        this.owner = owner;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable failure) {
        try {
            handle(thread, failure);
        } catch (Throwable secondary) {
            // Reporting failed too (for example out of memory); do not recurse.
            System.err.println("LogicForge: unexpected error: " + failure);
        }
    }

    private void handle(Thread thread, Throwable failure) {
        String signature = signature(failure);
        int count;
        synchronized (occurrences) {
            count = occurrences.merge(signature, 1, Integer::sum);
        }
        if (count <= FULLY_LOGGED_REPEATS) {
            log.error("Unexpected error in thread \"" + thread.getName() + "\"", failure);
        } else if (Integer.bitCount(count) == 1) {
            log.info("The same unexpected error has now occurred " + count + " times: " + signature);
        }
        if (!interactive) {
            System.err.println("LogicForge: aborting after an unexpected error (see " + log.file() + ")");
            Runtime.getRuntime().halt(1);
        }
        boolean firstExplanation;
        synchronized (explained) {
            firstExplanation = explained.add(signature);
        }
        if (firstExplanation) {
            explainLater(failure);
        }
    }

    /**
     * Always deferred: the failure may have happened during a layout or animation pulse,
     * where a modal dialog is not allowed.
     */
    private void explainLater(Throwable failure) {
        try {
            Platform.runLater(() -> explain(failure));
        } catch (IllegalStateException toolkitNotRunning) {
            // Before start-up or after shutdown there is no window to explain it in.
        }
    }

    private void explain(Throwable failure) {
        Dialogs.errorWithDetails(owner.get(), "Unexpected Error",
                "LogicForge ran into an unexpected problem",
                summary(failure) + "\n\nLogicForge may not work correctly until it is restarted."
                        + " If you have unsaved changes, save them under a new name to be safe."
                        + "\n\nThe details were written to " + log.file() + ".",
                AppLog.stackTrace(failure));
    }

    /** One line for the user: the innermost cause is usually the informative one. */
    static String summary(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String type = root.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    /** Identifies "the same failure again": type, message and where it was thrown. */
    static String signature(Throwable failure) {
        StackTraceElement[] trace = failure.getStackTrace();
        String origin = trace.length > 0 ? trace[0].toString() : "";
        return failure.getClass().getName() + ": " + failure.getMessage() + " @ " + origin;
    }
}
