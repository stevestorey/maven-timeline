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

        var lines = TimelineRenderer.render(runs, 10 * S, 60, 1);

        assertTrue(lines.get(0).startsWith("Build timeline: 10.0 s wall, 1 thread, 2 mojos"), lines.get(0));
        assertTrue(lines.get(3).startsWith("lib "), lines.get(3));
        assertTrue(lines.get(4).startsWith("app "), lines.get(4));
        assertEquals("  x:y (test)  app  test    6.0 s", lines.get(lines.size() - 1));
    }
}
