package io.github.stevestorey.maven.timeline;

import java.util.Objects;

/** One completed mojo execution. */
public final class MojoRun {

    private final String project;
    private final String mojo;
    private final String phase;
    private final String thread;
    private final long start;
    private final long end;
    private final boolean failed;

    /**
     * @param project the artifactId of the project the mojo ran for
     * @param mojo the mojo as {@code prefix:goal (executionId)}
     * @param phase the lifecycle phase the execution is bound to, or {@code null} for a goal invoked directly
     * @param thread the name of the thread the mojo ran on
     * @param start nanoseconds since the start of the build
     * @param end nanoseconds since the start of the build
     * @param failed whether the mojo failed
     */
    public MojoRun(String project, String mojo, String phase, String thread, long start, long end, boolean failed) {
        this.project = project;
        this.mojo = mojo;
        this.phase = phase;
        this.thread = thread;
        this.start = start;
        this.end = end;
        this.failed = failed;
    }

    public String project() {
        return project;
    }

    public String mojo() {
        return mojo;
    }

    public String phase() {
        return phase;
    }

    public String thread() {
        return thread;
    }

    public long start() {
        return start;
    }

    public long end() {
        return end;
    }

    public boolean failed() {
        return failed;
    }

    public long duration() {
        return end - start;
    }

    public Category category() {
        return Category.of(phase);
    }

    boolean overlaps(long from, long to) {
        return start < to && end > from;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MojoRun)) {
            return false;
        }
        MojoRun other = (MojoRun) o;
        return start == other.start
                && end == other.end
                && failed == other.failed
                && Objects.equals(project, other.project)
                && Objects.equals(mojo, other.mojo)
                && Objects.equals(phase, other.phase)
                && Objects.equals(thread, other.thread);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project, mojo, phase, thread, start, end, failed);
    }

    @Override
    public String toString() {
        return "MojoRun[project=" + project + ", mojo=" + mojo + ", phase=" + phase + ", thread=" + thread
                + ", start=" + start + ", end=" + end + ", failed=" + failed + "]";
    }
}
