package io.github.stevestorey.maven.timeline;

/** Finds the width of the terminal Maven is writing to, through whichever console API the running Maven has. */
final class TerminalWidth {

    private TerminalWidth() {}

    /** The terminal width, or 0 when it is unknown, such as when the output is not a terminal. */
    static int detect() {
        int width = invoke("org.apache.maven.jline.MessageUtils");
        if (width <= 0) {
            width = invoke("org.apache.maven.shared.utils.logging.MessageUtils");
        }
        if (width <= 0) {
            width = jansi();
        }
        if (width <= 0) {
            width = columns();
        }
        return width;
    }

    /** Maven 4's and maven-shared-utils' {@code MessageUtils.getTerminalWidth()}. */
    private static int invoke(String className) {
        try {
            Class<?> type = Class.forName(className, true, TerminalWidth.class.getClassLoader());
            return (int) type.getMethod("getTerminalWidth").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** Maven 3's {@code AnsiConsole.out().getTerminalWidth()}. */
    private static int jansi() {
        try {
            Class<?> type = Class.forName("org.fusesource.jansi.AnsiConsole", true, TerminalWidth.class.getClassLoader());
            Object out = type.getMethod("out").invoke(null);
            return (int) out.getClass().getMethod("getTerminalWidth").invoke(out);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return 0;
        }
    }

    private static int columns() {
        try {
            String columns = System.getenv("COLUMNS");
            return columns != null ? Integer.parseInt(columns.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
