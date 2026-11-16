package io.github.stevestorey.maven.timeline;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records the start and end of every mojo execution and, once the build has finished, logs them as a
 * timeline and optionally writes them as a Chrome trace.
 *
 * <p>It records nothing unless one of these properties is set:
 *
 * <ul>
 *   <li>{@code build.timeline}: logs the timeline after the build result ({@code -Dbuild.timeline} or
 *       {@code -Dbuild.timeline=true})
 *   <li>{@code build.timeline.trace}: a file to write the Chrome trace to, relative to the directory Maven
 *       was run from
 * </ul>
 *
 * <p>Also:
 *
 * <ul>
 *   <li>{@code build.timeline.top}: lists the slowest mojos after the timeline, 10 of them for {@code
 *       -Dbuild.timeline.top} or the number given
 *   <li>{@code build.timeline.width}: the line width, which otherwise follows the terminal
 *   <li>{@code build.timeline.color}: {@code always}, {@code never} or, by default, {@code auto}, which
 *       colours the timeline whenever Maven colours its own output
 * </ul>
 */
@Named
@Singleton
public class BuildTimelineSpy extends AbstractEventSpy {

    static final String ENABLED = "build.timeline";
    static final String TRACE = "build.timeline.trace";
    static final String WIDTH = "build.timeline.width";
    static final String TOP = "build.timeline.top";
    static final String COLOUR = "build.timeline.color";

    /** The width of the {@code [INFO] } prefix Maven adds to each line. */
    private static final int LOG_PREFIX_WIDTH = 7;

    private static final int DEFAULT_WIDTH = 120;
    private static final int DEFAULT_TOP = 10;

    private static final Logger LOG = LoggerFactory.getLogger(BuildTimelineSpy.class);

    private static final class Key {
        private final MavenProject project;
        private final MojoExecution execution;

        Key(MavenProject project, MojoExecution execution) {
            this.project = project;
            this.execution = execution;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key other = (Key) o;
            return Objects.equals(project, other.project) && Objects.equals(execution, other.execution);
        }

        @Override
        public int hashCode() {
            return Objects.hash(project, execution);
        }
    }

    private static final class Started {
        private final long thread;
        private final long start;

        Started(long thread, long start) {
            this.thread = thread;
            this.start = start;
        }
    }

    private final Map<Key, Started> running = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<MojoRun> finished = new ConcurrentLinkedQueue<>();

    private volatile boolean recording;
    private volatile boolean reported;
    private volatile long origin;
    private volatile long end;
    private volatile MavenSession session;

    @Override
    public void onEvent(Object event) {
        if (!(event instanceof ExecutionEvent)) {
            return;
        }
        ExecutionEvent executionEvent = (ExecutionEvent) event;
        long now = System.nanoTime();
        switch (executionEvent.getType()) {
            case SessionStarted:
                session = executionEvent.getSession();
                origin = now;
                recording = property(ENABLED) != null || property(TRACE) != null;
                break;
            case MojoStarted:
                if (recording) {
                    running.put(
                            key(executionEvent), new Started(Thread.currentThread().getId(), now - origin));
                }
                break;
            case MojoSucceeded:
                finish(executionEvent, now, false);
                break;
            case MojoFailed:
                finish(executionEvent, now, true);
                break;
            case SessionEnded:
                end = now - origin;
                break;
            default:
                break;
        }
    }

    private void finish(ExecutionEvent event, long now, boolean failed) {
        if (!recording) {
            return;
        }
        Started started = running.remove(key(event));
        if (started != null) {
            MojoExecution execution = event.getMojoExecution();
            finished.add(new MojoRun(
                    event.getProject().getArtifactId(),
                    label(execution),
                    execution.getLifecyclePhase(),
                    started.thread,
                    started.start,
                    now - origin,
                    failed));
        }
    }

    @Override
    public void close() {
        if (!recording || reported) {
            return;
        }
        reported = true;
        List<MojoRun> runs = new ArrayList<>(finished);
        long total = end > 0 ? end : System.nanoTime() - origin;

        String enabled = property(ENABLED);
        if (enabled != null && !"false".equalsIgnoreCase(enabled)) {
            log(TimelineRenderer.render(
                    runs, total, session.getRequest().getDegreeOfConcurrency(), width() - LOG_PREFIX_WIDTH, top(), colour()));
        }
        String trace = property(TRACE);
        if (trace != null && !trace.trim().isEmpty()) {
            Path file = Paths.get(session.getExecutionRootDirectory()).resolve(trace);
            try {
                ChromeTrace.write(runs, file);
                LOG.info("Build trace written to {}", file);
            } catch (IOException e) {
                LOG.warn("Could not write the build trace to {}: {}", file, e.toString());
            }
        }
    }

    private static void log(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        LOG.info("");
        lines.forEach(LOG::info);
        LOG.info("------------------------------------------------------------------------");
    }

    private static Key key(ExecutionEvent event) {
        return new Key(event.getProject(), event.getMojoExecution());
    }

    private static String label(MojoExecution execution) {
        MojoDescriptor descriptor = execution.getMojoDescriptor();
        String prefix = descriptor != null && descriptor.getPluginDescriptor() != null
                ? descriptor.getPluginDescriptor().getGoalPrefix()
                : execution.getArtifactId();
        return prefix + ":" + execution.getGoal() + " (" + execution.getExecutionId() + ")";
    }

    private String property(String name) {
        MavenSession session = this.session;
        if (session == null) {
            return null;
        }
        String value = session.getUserProperties().getProperty(name);
        return value != null ? value : session.getSystemProperties().getProperty(name);
    }

    private int intProperty(String name, int fallback) {
        try {
            String value = property(name);
            return value != null ? Integer.parseInt(value.trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The number of slowest mojos to list: none unless the property is set, and 10 when it has no value. */
    private int top() {
        String value = property(TOP);
        if (value == null || "false".equalsIgnoreCase(value.trim())) {
            return 0;
        }
        return value.trim().isEmpty() || "true".equalsIgnoreCase(value.trim()) ? DEFAULT_TOP : intProperty(TOP, DEFAULT_TOP);
    }

    private boolean colour() {
        String value = property(COLOUR);
        if ("always".equalsIgnoreCase(value)) {
            return true;
        }
        if ("never".equalsIgnoreCase(value)) {
            return false;
        }
        return Terminal.colour();
    }

    private int width() {
        int width = intProperty(WIDTH, 0);
        if (width <= 0) {
            width = Terminal.width();
        }
        return width > 0 ? width : DEFAULT_WIDTH;
    }
}
