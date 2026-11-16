package io.github.stevestorey.maven.timeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes mojo executions in the Chrome trace event format, viewable in https://ui.perfetto.dev or
 * chrome://tracing with a row per builder thread.
 */
public final class ChromeTrace {

    private ChromeTrace() {}

    public static void write(List<MojoRun> runs, Path file) throws IOException {
        Map<String, Integer> threadIds = new LinkedHashMap<>();
        runs.forEach(run -> threadIds.computeIfAbsent(run.thread(), name -> threadIds.size() + 1));

        StringBuilder json = new StringBuilder("{\"traceEvents\":[\n");
        boolean first = true;
        for (Map.Entry<String, Integer> thread : threadIds.entrySet()) {
            first = separator(json, first);
            json.append("{\"ph\":\"M\",\"name\":\"thread_name\",\"pid\":1,\"tid\":")
                    .append(thread.getValue())
                    .append(",\"args\":{\"name\":")
                    .append(string(thread.getKey()))
                    .append("}}");
        }
        for (MojoRun run : runs) {
            first = separator(json, first);
            json.append("{\"ph\":\"X\",\"pid\":1,\"tid\":")
                    .append(threadIds.get(run.thread()))
                    .append(",\"ts\":")
                    .append(run.start() / 1000)
                    .append(",\"dur\":")
                    .append(Math.max(1, run.duration() / 1000))
                    .append(",\"name\":")
                    .append(string(run.project() + " " + run.mojo()))
                    .append(",\"cat\":")
                    .append(string(run.category().label()))
                    .append(",\"args\":{\"project\":")
                    .append(string(run.project()))
                    .append(",\"phase\":")
                    .append(string(String.valueOf(run.phase())))
                    .append(",\"failed\":")
                    .append(run.failed())
                    .append("}}");
        }
        json.append("\n]}\n");

        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean separator(StringBuilder json, boolean first) {
        if (!first) {
            json.append(",\n");
        }
        return false;
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
