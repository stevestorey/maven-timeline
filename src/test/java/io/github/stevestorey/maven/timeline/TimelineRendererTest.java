package io.github.stevestorey.maven.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineRendererTest {

    private static final long S = 1_000_000_000L;

    private static MojoRun run(String project, String phase, long from, long to) {
        return new MojoRun(project, "x:y (" + phase + ")", phase, "t1", from * S, to * S, false);
    }

    @Test
    void drawsEachMojoAtItsTimeWithIdleGaps() {
        var runs = List.of(run("lib", "compile", 0, 2), run("lib", "package", 4, 6));

        // One cell per second
        assertEquals("cc..pp    ", TimelineRenderer.bar(runs, 0, 6 * S, 10 * S, 10));
    }

    @Test
    void capitalisesCellsWhereMojosOverlap() {
        var runs = List.of(run("app", "test", 0, 4), run("app", "package", 2, 3));

        // Package runs only during the third second
        assertEquals("ttTt", TimelineRenderer.bar(runs, 0, 4 * S, 4 * S, 4));
    }

    @Test
    void doesNotCapitaliseBackToBackMojos() {
        var runs = List.of(run("app", "compile", 0, 2), run("app", "test", 2, 4));

        assertEquals("cctt", TimelineRenderer.bar(runs, 0, 4 * S, 4 * S, 4));
    }

    @Test
    void marksFailedMojos() {
        var runs = List.of(new MojoRun("app", "x:y (t)", "test", "t1", 0, 2 * S, true));

        assertEquals("!!", TimelineRenderer.bar(runs, 0, 2 * S, 2 * S, 2));
    }

    @Test
    void ignoresPhasePrefixes() {
        assertEquals(Category.VERIFY, Category.of("after:verify"));
        assertEquals(Category.TEST, Category.of("before:test"));
        assertEquals(Category.COMPILE, Category.of("after:resources"));
        assertEquals(Category.OTHER, Category.of(null));
    }

    @Test
    void spacesAxisLabels() {
        // A 40 column bar over 40 seconds puts a 10 second tick every 10 columns; the 40s label does not fit
        assertEquals("0s        10s       20s       30s", TimelineRenderer.axis(40 * S, 40));
    }

    @Test
    void rendersRowsInStartOrderAndSlowestMojos() {
        var runs = List.of(run("app", "test", 3, 9), run("lib", "compile", 0, 3));

        var lines = TimelineRenderer.render(runs, 10 * S, 60, 1, false);

        assertTrue(lines.get(0).startsWith("Build timeline: 10.0 s wall, 1 thread, 2 mojos"), lines.get(0));
        assertTrue(lines.get(4).startsWith("lib "), lines.get(4));
        assertTrue(lines.get(5).startsWith("app "), lines.get(5));
        assertEquals("  x:y (test)  app  test    6.0 s", lines.get(lines.size() - 1));
    }

    @Test
    void listsSlowestMojosOnlyWhenAsked() {
        var runs = List.of(run("lib", "compile", 0, 3));

        assertTrue(TimelineRenderer.render(runs, 4 * S, 60, 0, false).stream()
                .noneMatch(line -> line.contains("Slowest")));
    }

    @Test
    void comparesMojoTimeWithElapsedTime() {
        // 12 s of mojos within 6 s, with three running at once from 2 s to 3 s
        var runs = List.of(run("a", "test", 0, 6), run("b", "test", 1, 3), run("c", "test", 2, 6));

        assertEquals(
                "Parallelism: 2.0x on average, peak 3 mojos at once; 12.0 s of mojo time in 6.0 s,"
                        + " about 6.0 s (50%) less than one at a time",
                TimelineRenderer.parallelism(runs, new Ansi(false)));
    }

    @Test
    void coloursOnlyWhenEnabled() {
        var runs = List.of(run("lib", "compile", 0, 3));

        assertTrue(TimelineRenderer.render(runs, 4 * S, 60, 1, false).stream()
                .noneMatch(line -> line.contains("\u001b")));
        assertTrue(TimelineRenderer.render(runs, 4 * S, 60, 1, true).stream()
                .allMatch(line -> line.isEmpty() || line.contains("\u001b[")));
    }

    @Test
    void coloursEachRunOfABar() {
        var ansi = new Ansi(true);

        assertEquals(
                "\u001b[34mcc\u001b[0m\u001b[90m.\u001b[0m\u001b[1;32mT\u001b[0m  ", ansi.bar("cc.T  "));
    }
}
