package io.github.stevestorey.maven.timeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders mojo executions as text: a summary of how much the build gained from running mojos in parallel,
 * a row per project across a shared time axis and, optionally, the slowest mojos.
 */
public final class TimelineRenderer {

    static final char IDLE = '.';
    static final char FAILED = '!';

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long[] TICK_STEPS_SECONDS = {
        1, 2, 5, 10, 15, 20, 30, 60, 120, 300, 600, 900, 1200, 1800, 3600
    };
    private static final int MIN_TICK_SPACING = 8;
    private static final int MAX_NAME_WIDTH = 30;
    private static final int MIN_BAR_WIDTH = 20;

    private TimelineRenderer() {}

    /**
     * @param runs the mojo executions, with times relative to the start of the build
     * @param total the length of the build in nanoseconds
     * @param width the number of characters available for each line
     * @param top the number of slowest mojos to list, or 0 for none
     * @param colour whether to colour the output with ANSI escape sequences
     */
    public static List<String> render(List<MojoRun> runs, long total, int width, int top, boolean colour) {
        List<String> lines = new ArrayList<>();
        if (runs.isEmpty() || total <= 0) {
            return lines;
        }
        Ansi ansi = new Ansi(colour);
        Map<String, List<MojoRun>> projects = runs.stream()
                .sorted(Comparator.comparingLong(MojoRun::start))
                .collect(Collectors.groupingBy(MojoRun::project, LinkedHashMap::new, Collectors.toList()));
        long threads = runs.stream().map(MojoRun::thread).distinct().count();

        int nameWidth = Math.max(
                6,
                Math.min(
                        MAX_NAME_WIDTH,
                        projects.keySet().stream().mapToInt(String::length).max().orElse(0)));
        int prefixWidth = nameWidth + 2 + 7 + 2;
        int barWidth = Math.max(MIN_BAR_WIDTH, width - prefixWidth - 2);

        lines.add(ansi.paint("Build timeline:", Ansi.BOLD)
                + String.format(
                        Locale.ROOT,
                        " %s wall, %d %s, %d mojos",
                        seconds(total),
                        threads,
                        threads == 1 ? "thread" : "threads",
                        runs.size()));
        lines.add(parallelism(runs, ansi));
        lines.add(legend(runs, ansi));
        lines.add(spaces(prefixWidth + 1) + ansi.paint(axis(total, barWidth), Ansi.DIM));
        String border = ansi.paint("|", Ansi.DIM);
        projects.forEach((project, projectRuns) -> {
            long start = projectRuns.stream().mapToLong(MojoRun::start).min().orElse(0);
            long end = projectRuns.stream().mapToLong(MojoRun::end).max().orElse(0);
            boolean failed = projectRuns.stream().anyMatch(MojoRun::failed);
            lines.add(ansi.paint(pad(abbreviate(project, nameWidth), nameWidth), failed ? Ansi.RED : Ansi.BOLD)
                    + "  "
                    + String.format(Locale.ROOT, "%7s", seconds(end - start))
                    + "  "
                    + border
                    + ansi.bar(bar(projectRuns, start, end, total, barWidth))
                    + border);
        });

        if (top > 0) {
            lines.add("");
            lines.add(ansi.paint("Slowest mojos:", Ansi.BOLD));
            lines.addAll(slowest(runs, top, ansi));
        }
        return lines;
    }

    /**
     * Compares the time the mojos took, added together, with the time from the first starting to the last
     * finishing, which is roughly what running them one at a time would cost against what running them in
     * parallel did.
     */
    static String parallelism(List<MojoRun> runs, Ansi ansi) {
        long mojoTime = runs.stream().mapToLong(MojoRun::duration).sum();
        long first = runs.stream().mapToLong(MojoRun::start).min().orElse(0);
        long last = runs.stream().mapToLong(MojoRun::end).max().orElse(0);
        long span = Math.max(1, last - first);
        long saved = Math.max(0, mojoTime - span);
        return ansi.paint("Parallelism:", Ansi.BOLD)
                + " "
                + ansi.paint(String.format(Locale.ROOT, "%.1fx", mojoTime / (double) span), Ansi.BOLD, Ansi.GREEN)
                + String.format(
                        Locale.ROOT,
                        " on average, peak %d mojos at once; %s of mojo time in %s, about %s (%d%%) less"
                                + " than one at a time",
                        peak(runs),
                        seconds(mojoTime),
                        seconds(span),
                        seconds(saved),
                        mojoTime == 0 ? 0 : Math.round(100.0 * saved / mojoTime));
    }

    private static int peak(List<MojoRun> runs) {
        List<long[]> edges = edges(runs);
        int active = 0;
        int peak = 0;
        for (long[] edge : edges) {
            active += (int) edge[1];
            peak = Math.max(peak, active);
        }
        return peak;
    }

    private static String legend(List<MojoRun> runs, Ansi ansi) {
        EnumSet<Category> used = EnumSet.noneOf(Category.class);
        runs.forEach(run -> used.add(run.category()));
        StringBuilder legend = new StringBuilder(ansi.paint("Legend:", Ansi.BOLD));
        used.forEach(category -> legend.append(' ')
                .append(ansi.paint(String.valueOf(category.letter()), category.colour()))
                .append('=')
                .append(category.label()));
        legend.append(", UPPER CASE=mojos running at once, ")
                .append(ansi.paint(String.valueOf(IDLE), Ansi.DIM))
                .append("=idle");
        if (runs.stream().anyMatch(MojoRun::failed)) {
            legend.append(", ").append(ansi.paint(String.valueOf(FAILED), Ansi.BOLD, Ansi.RED)).append("=failed");
        }
        return legend.toString();
    }

    private static List<String> slowest(List<MojoRun> runs, int top, Ansi ansi) {
        List<MojoRun> slowest = runs.stream()
                .sorted(Comparator.comparingLong(MojoRun::duration).reversed())
                .limit(top)
                .collect(Collectors.toList());
        int mojoWidth = slowest.stream().mapToInt(run -> run.mojo().length()).max().orElse(0);
        int projectWidth =
                slowest.stream().mapToInt(run -> run.project().length()).max().orElse(0);
        int phaseWidth = slowest.stream()
                .mapToInt(run -> String.valueOf(run.phase()).length())
                .max()
                .orElse(0);
        List<String> lines = new ArrayList<>();
        for (MojoRun run : slowest) {
            lines.add("  " + pad(run.mojo(), mojoWidth)
                    + "  " + pad(run.project(), projectWidth)
                    + "  " + ansi.paint(pad(run.phase() == null ? "-" : run.phase(), phaseWidth), run.category().colour())
                    + "  " + String.format(Locale.ROOT, "%7s", seconds(run.duration()))
                    + (run.failed() ? "  " + ansi.paint("FAILED", Ansi.BOLD, Ansi.RED) : ""));
        }
        return lines;
    }

    /** Tick labels, each starting at the column its time falls in. */
    static String axis(long total, int barWidth) {
        long step = TICK_STEPS_SECONDS[TICK_STEPS_SECONDS.length - 1];
        for (long candidate : TICK_STEPS_SECONDS) {
            if ((double) candidate * NANOS_PER_SECOND / total * barWidth >= MIN_TICK_SPACING) {
                step = candidate;
                break;
            }
        }
        StringBuilder axis = new StringBuilder(spaces(barWidth));
        int free = 0;
        for (long tick = 0; tick * NANOS_PER_SECOND <= total; tick += step) {
            int column = (int) Math.round((double) tick * NANOS_PER_SECOND / total * barWidth);
            String label = tickLabel(tick);
            if (column >= free && column + label.length() <= barWidth) {
                axis.replace(column, column + label.length(), label);
                free = column + label.length() + 1;
            }
        }
        return stripTrailing(axis);
    }

    private static String tickLabel(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        return seconds % 60 == 0 ? seconds / 60 + "m" : seconds / 60 + "m" + seconds % 60 + "s";
    }

    static String bar(List<MojoRun> runs, long start, long end, long total, int barWidth) {
        List<long[]> concurrent = concurrentIntervals(runs);
        StringBuilder bar = new StringBuilder(barWidth);
        for (int cell = 0; cell < barWidth; cell++) {
            long from = total * cell / barWidth;
            long to = total * (cell + 1) / barWidth;
            Map<Category, Long> busy = new EnumMap<>(Category.class);
            boolean failed = false;
            for (MojoRun run : runs) {
                if (run.overlaps(from, to)) {
                    busy.merge(run.category(), Math.min(run.end(), to) - Math.max(run.start(), from), Long::sum);
                    failed |= run.failed();
                }
            }
            if (busy.isEmpty()) {
                bar.append(start < to && end > from ? IDLE : ' ');
            } else if (failed) {
                bar.append(FAILED);
            } else {
                char letter = busy.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .get()
                        .getKey()
                        .letter();
                boolean overlapping = concurrent.stream().anyMatch(interval -> interval[0] < to && interval[1] > from);
                bar.append(overlapping ? Character.toUpperCase(letter) : letter);
            }
        }
        return bar.toString();
    }

    /**
     * The start (+1) and end (-1) of each run in time order, with ends before starts at the same instant so
     * that back-to-back runs do not count as overlapping.
     */
    private static List<long[]> edges(List<MojoRun> runs) {
        List<long[]> edges = new ArrayList<>();
        for (MojoRun run : runs) {
            edges.add(new long[] {run.start(), 1});
            edges.add(new long[] {run.end(), -1});
        }
        edges.sort(Comparator.<long[]>comparingLong(edge -> edge[0]).thenComparingLong(edge -> edge[1]));
        return edges;
    }

    /** The intervals in which two or more of the runs were executing. */
    private static List<long[]> concurrentIntervals(List<MojoRun> runs) {
        List<long[]> intervals = new ArrayList<>();
        int active = 0;
        long openedAt = 0L;
        for (long[] edge : edges(runs)) {
            int before = active;
            active += (int) edge[1];
            if (before < 2 && active >= 2) {
                openedAt = edge[0];
            } else if (before >= 2 && active < 2 && edge[0] > openedAt) {
                intervals.add(new long[] {openedAt, edge[0]});
            }
        }
        return intervals;
    }

    static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / (double) NANOS_PER_SECOND);
    }

    private static String abbreviate(String text, int width) {
        return text.length() <= width ? text : text.substring(0, width - 1) + "~";
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + spaces(width - text.length());
    }

    private static String spaces(int count) {
        char[] spaces = new char[count];
        Arrays.fill(spaces, ' ');
        return new String(spaces);
    }

    private static String stripTrailing(CharSequence text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.subSequence(0, end).toString();
    }
}
