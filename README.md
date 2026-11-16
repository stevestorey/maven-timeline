# Maven Build Timeline

A Maven core extension that records when every mojo starts and finishes and, at the end of the
build, prints a per-module timeline under the build result. It works with Maven 3 and Maven 4 and
with every builder, including Maven 4's concurrent builder (`-b concurrent`), whose Reactor Summary
times no longer show where the time went.

```
[INFO] Build timeline: 42.9 s wall, 16 threads, 359 mojos
[INFO] Legend: c=compile k=test-compile t=test p=package v=verify, UPPER CASE=mojos running at once, .=idle
[INFO]                        0s           5s          10s          15s          20s
[INFO] parent         0.5 s  |cc                                                       |
[INFO] core           6.4 s  |  ccccckkkttttttttt                                      |
[INFO] web-ui        12.0 s  |  ccccccccccccccccccccccccccccpppp                       |
[INFO] service        6.2 s  |      cccccckkkkttttttt                                  |
[INFO] ...
[INFO]
[INFO] Slowest mojos:
[INFO]   surefire:test (default-test)  persistence  test   27.9 s
[INFO]   surefire:test (default-test)  service      test   26.3 s
```

## Reading the timeline

- One row per project, in the order the projects started, on a time axis shared by all rows.
  The time beside each name runs from the project's first mojo starting to its last finishing.
- Each column is a slice of the build. Its letter is the kind of phase that ran for longest in
  that slice: `c` validate to process-classes, `k` test sources to test-compile, `t` test,
  `p` package, `v` integration tests and verify, `i` install and deploy, `s` site, `x` clean,
  and `*` goals run directly from the command line. Maven 4's phase names and its `before:`,
  `after:` and `at:` phases are grouped the same way.
- An upper-case letter means two or more of the project's mojos were running at the same time.
- `.` means the project had started but none of its mojos were running. Under the concurrent
  builder, that is time spent waiting for an upstream project or for the project lock that
  Maven 3 API mojos take.
- `!` marks a mojo that failed, and the list of slowest mojos marks it `FAILED`.

## Building

```
mvn package
```

This builds `target/maven-timeline.jar`.

## Adding it to a project

Copy the jar into the project's `.mvn/ext` directory and add this line to `.mvn/maven.config`:

```
-Dmaven.ext.class.path=${session.rootDirectory}/.mvn/ext/maven-timeline.jar
```

Maven 3 and Maven 4 both expand `${session.rootDirectory}` there, so the extension loads whichever
directory of the project Maven runs from. `maven.ext.class.path` holds a single path, so a value
given on the command line may replace this one.

To try it without changing a project, pass the same property on the command line with the jar's
path.

## Turning it on

The extension records nothing unless one of these properties is set:

| Property | Effect |
|---|---|
| `build.timeline` | Prints the timeline after the build result (`-Dbuild.timeline`) |
| `build.timeline.trace` | Also writes a Chrome trace to this file, relative to the directory Maven runs from, for viewing in https://ui.perfetto.dev or `chrome://tracing` with a row per builder thread |
| `build.timeline.width` | The line width. By default it follows the terminal, or 120 columns when the output is not a terminal |
| `build.timeline.top` | How many of the slowest mojos to list (10 by default, 0 for none) |

For example:

```
mvn4 verify -T1C -b concurrent -Dbuild.timeline -Dbuild.timeline.trace=target/build-trace.json
```

Adding `-Dbuild.timeline` to `.mvn/maven.config` as well prints the timeline on every build.
