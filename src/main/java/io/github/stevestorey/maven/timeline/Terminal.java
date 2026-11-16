package io.github.stevestorey.maven.timeline;

/** Finds out about the terminal Maven is writing to, through whichever console API the running Maven has. */
final class Terminal {

    private Terminal() {}

    /**
     * Whether Maven is colouring its own output, which it does not in batch mode ({@code -B}), with {@code
     * --color=never} or when the output is not a terminal. Setting the {@code NO_COLOR} environment variable
     * turns colour off too.
     */
    static boolean colour() {
        String noColour = System.getenv("NO_COLOR");
        if (noColour != null && !noColour.isEmpty()) {
            return false;
        }
        return colourEnabled("org.apache.maven.jline.MessageUtils")
                || colourEnabled("org.apache.maven.shared.utils.logging.MessageUtils");
    }

    /** {@code MessageUtils.isColorEnabled()} of Maven 3.10 and 4, or of maven-shared-utils in older Maven 3. */
    private static boolean colourEnabled(String className) {
        try {
            Class<?> type = Class.forName(className, true, Terminal.class.getClassLoader());
            return (boolean) type.getMethod("isColorEnabled").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** The terminal width, or 0 when it is unknown, such as when the output is not a terminal. */
    static int width() {
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

    /** {@code MessageUtils.getTerminalWidth()} of Maven 3.10 and 4, or of maven-shared-utils in older Maven 3. */
    private static int invoke(String className) {
        try {
            Class<?> type = Class.forName(className, true, Terminal.class.getClassLoader());
            return (int) type.getMethod("getTerminalWidth").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** {@code AnsiConsole.out().getTerminalWidth()} of the Jansi in older Maven 3. */
    private static int jansi() {
        try {
            Class<?> type = Class.forName("org.fusesource.jansi.AnsiConsole", true, Terminal.class.getClassLoader());
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
