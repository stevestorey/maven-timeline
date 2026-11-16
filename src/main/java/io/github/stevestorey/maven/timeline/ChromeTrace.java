package io.github.stevestorey.maven.timeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes mojo executions in the Chrome trace event format, viewable in https://ui.perfetto.dev or
 * chrome://tracing, as two groups of rows: one row per builder thread, showing how busy each thread was and
 * where it sat idle, and one row per project, showing where each project's time went.
 */
public final class ChromeTrace {

    private static final int THREADS = 1;
    private static final int PROJECTS = 2;

    private ChromeTrace() {}

    public static void write(List<MojoRun> runs, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, json(runs).getBytes(StandardCharsets.UTF_8));
    }

    static String json(List<MojoRun> runs) {
        List<MojoRun> sorted = new ArrayList<>(runs);
        sorted.sort(Comparator.comparingLong(MojoRun::start));
        // Rows are numbered in the order they first ran a mojo
        Map<Long, Integer> threads = new LinkedHashMap<>();
        Map<String, Integer> projects = new LinkedHashMap<>();
        sorted.forEach(run -> {
            threads.computeIfAbsent(run.thread(), id -> threads.size() + 1);
            projects.computeIfAbsent(run.project(), name -> projects.size() + 1);
        });

        List<String> events = new ArrayList<>();
        process(events, THREADS, "Builder threads");
        threads.values().forEach(row -> row(events, THREADS, row, "builder " + row));
        process(events, PROJECTS, "Projects");
        projects.forEach((project, row) -> row(events, PROJECTS, row, project));
        for (MojoRun run : sorted) {
            events.add(slice(run, THREADS, threads.get(run.thread()), run.project() + " " + run.mojo()));
            events.add(slice(run, PROJECTS, projects.get(run.project()), run.mojo()));
        }
        return "{\"traceEvents\":[\n" + String.join(",\n", events) + "\n]}\n";
    }

    private static void process(List<String> events, int pid, String name) {
        events.add(metadata("process_name", pid, 0, "\"name\":" + string(name)));
        events.add(metadata("process_sort_index", pid, 0, "\"sort_index\":" + pid));
    }

    private static void row(List<String> events, int pid, int tid, String name) {
        events.add(metadata("thread_name", pid, tid, "\"name\":" + string(name)));
        events.add(metadata("thread_sort_index", pid, tid, "\"sort_index\":" + tid));
    }

    private static String metadata(String name, int pid, int tid, String args) {
        return "{\"ph\":\"M\",\"name\":\"" + name + "\",\"pid\":" + pid + ",\"tid\":" + tid + ",\"args\":{" + args
                + "}}";
    }

    private static String slice(MojoRun run, int pid, int tid, String name) {
        return "{\"ph\":\"X\",\"pid\":" + pid
                + ",\"tid\":" + tid
                + ",\"ts\":" + run.start() / 1000
                + ",\"dur\":" + Math.max(1, run.duration() / 1000)
                + ",\"name\":" + string(name)
                + ",\"cat\":" + string(run.category().label())
                + ",\"args\":{\"project\":" + string(run.project())
                + ",\"mojo\":" + string(run.mojo())
                + ",\"phase\":" + string(String.valueOf(run.phase()))
                + ",\"failed\":" + run.failed()
                + "}}";
    }

    private static String string(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"':
                    quoted.append("\\\"");
                    break;
                case '\\':
                    quoted.append("\\\\");
                    break;
                default:
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
            }
        }
        return quoted.append('"').toString();
    }
}
