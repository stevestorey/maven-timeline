package io.github.stevestorey.maven.timeline;

/**
 * ANSI SGR colouring with the 16 basic colours, which every ANSI terminal supports and which follow the
 * terminal's own colour scheme.
 */
final class Ansi {

    static final String BOLD = "1";
    static final String DIM = "90";
    static final String RED = "31";
    static final String GREEN = "32";
    static final String YELLOW = "33";
    static final String BLUE = "34";
    static final String MAGENTA = "35";
    static final String CYAN = "36";
    static final String WHITE = "37";
    static final String BRIGHT_BLUE = "94";
    static final String BRIGHT_MAGENTA = "95";

    private static final String RESET = "\u001b[0m";

    private final boolean enabled;

    Ansi(boolean enabled) {
        this.enabled = enabled;
    }

    /** Wraps the text in the given SGR codes, joined with {@code ;}, or returns it unchanged when disabled. */
    String paint(String text, String... codes) {
        if (!enabled || text.isEmpty() || codes.length == 0) {
            return text;
        }
        return "\u001b[" + String.join(";", codes) + "m" + text + RESET;
    }

    /** Colours each run of identical characters in a timeline bar. */
    String bar(String bar) {
        if (!enabled) {
            return bar;
        }
        StringBuilder painted = new StringBuilder();
        int from = 0;
        while (from < bar.length()) {
            char c = bar.charAt(from);
            int to = from;
            while (to < bar.length() && bar.charAt(to) == c) {
                to++;
            }
            String run = bar.substring(from, to);
            painted.append(c == ' ' ? run : paint(run, codes(c)));
            from = to;
        }
        return painted.toString();
    }

    private static String[] codes(char c) {
        if (c == TimelineRenderer.IDLE) {
            return new String[] {DIM};
        }
        if (c == TimelineRenderer.FAILED) {
            return new String[] {BOLD, RED};
        }
        Category category = Category.ofLetter(Character.toLowerCase(c));
        return Character.isUpperCase(c)
                ? new String[] {BOLD, category.colour()}
                : new String[] {category.colour()};
    }
}
