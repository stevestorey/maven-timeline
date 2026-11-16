package io.github.stevestorey.maven.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ChromeTraceTest {

    private static final long MS = 1_000_000L;

    private static List<String> lines(List<MojoRun> runs) {
        return Arrays.stream(ChromeTrace.json(runs).split("\n")).collect(Collectors.toList());
    }

    private static List<String> matching(List<MojoRun> runs, String text) {
        return lines(runs).stream().filter(line -> line.contains(text)).collect(Collectors.toList());
    }

    @Test
    void groupsRowsByBuilderThreadAndByProject() {
        // Thread 42 builds lib and then app; thread 7 builds web alongside lib
        var runs = List.of(
                new MojoRun("app", "compiler:compile (default-compile)", "compile", 42, 2 * MS, 3 * MS, false),
                new MojoRun("lib", "compiler:compile (default-compile)", "compile", 42, 0, 2 * MS, false),
                new MojoRun("web", "compiler:compile (default-compile)", "compile", 7, MS, 3 * MS, false));

        assertEquals(
                List.of(
                        "{\"ph\":\"M\",\"name\":\"process_name\",\"pid\":1,\"tid\":0,\"args\":{\"name\":\"Builder threads\"}},",
                        "{\"ph\":\"M\",\"name\":\"process_name\",\"pid\":2,\"tid\":0,\"args\":{\"name\":\"Projects\"}},"),
                matching(runs, "process_name"));
        // Builders are numbered in the order they first ran a mojo, not by thread id
        assertEquals(
                List.of(
                        "{\"ph\":\"M\",\"name\":\"thread_name\",\"pid\":1,\"tid\":1,\"args\":{\"name\":\"builder 1\"}},",
                        "{\"ph\":\"M\",\"name\":\"thread_name\",\"pid\":1,\"tid\":2,\"args\":{\"name\":\"builder 2\"}},",
                        "{\"ph\":\"M\",\"name\":\"thread_name\",\"pid\":2,\"tid\":1,\"args\":{\"name\":\"lib\"}},",
                        "{\"ph\":\"M\",\"name\":\"thread_name\",\"pid\":2,\"tid\":2,\"args\":{\"name\":\"web\"}},",
                        "{\"ph\":\"M\",\"name\":\"thread_name\",\"pid\":2,\"tid\":3,\"args\":{\"name\":\"app\"}},"),
                matching(runs, "thread_name"));

        List<String> app = matching(runs, "\"ph\":\"X\"").stream()
                .filter(line -> line.contains("\"project\":\"app\""))
                .collect(Collectors.toList());
        assertEquals(2, app.size());
        // On the thread rows, app shares builder 1 with lib and is named with its project
        assertTrue(app.get(0).startsWith("{\"ph\":\"X\",\"pid\":1,\"tid\":1,\"ts\":2000,\"dur\":1000,"
                + "\"name\":\"app compiler:compile (default-compile)\""), app.get(0));
        // On the project rows, it has a row of its own
        assertTrue(app.get(1).startsWith("{\"ph\":\"X\",\"pid\":2,\"tid\":3,\"ts\":2000,\"dur\":1000,"
                + "\"name\":\"compiler:compile (default-compile)\""), app.get(1));
    }

    @Test
    void escapesNames() {
        var runs = List.of(new MojoRun("a\"b\\c", "x:y", null, 1, 0, MS, true));

        assertTrue(ChromeTrace.json(runs).contains("\"name\":\"a\\\"b\\\\c\""));
        assertTrue(ChromeTrace.json(runs).contains("\"phase\":\"null\",\"failed\":true"));
    }
}
