# Maven Build Timeline

A Maven core extension that records when every mojo starts and finishes and, at the end of the
build, prints a per-module timeline under the build result. It works with Maven 3 and Maven 4 and
with every builder, including Maven 4's concurrent builder (`-b concurrent`), whose Reactor Summary
times no longer show where the time went.

```
[INFO] Build timeline: 42.9 s wall, 16 threads, 359 mojos
[INFO] Parallelism: 8.8x on average, peak 16 mojos at once; 376.9 s of mojo time in 42.7 s, about 334.2 s (89%) less than one at a time
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

- The parallelism line adds up the time every mojo took and compares it with the time from the
  first mojo starting to the last finishing, with the most mojos that ran at any one moment.
  It is an upper bound on what parallel building saved: mojos sharing the machine run slower
  than they would alone, so running them one at a time would take less than their total.
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

## Colour

The timeline is coloured whenever Maven colours its own output, so it is plain in batch mode
(`-B`), with `--color=never` and when the output is not a terminal. Setting the `NO_COLOR`
environment variable also turns it off. It uses the 16 basic ANSI colours, which follow the
terminal's colour scheme, so it works in any ANSI terminal such as Konsole or kitty. Maven 3
removes colour itself whenever its output is not a terminal, even with `build.timeline.color=always`.

## Turning it on

The extension records nothing unless one of these properties is set:

| Property | Effect |
|---|---|
| `build.timeline` | Prints the timeline after the build result (`-Dbuild.timeline`) |
| `build.timeline.trace` | Also writes a Chrome trace to this file, relative to the directory Maven runs from, for viewing in https://ui.perfetto.dev or `chrome://tracing` with a row per builder thread |
| `build.timeline.width` | The line width. By default it follows the terminal, or 120 columns when the output is not a terminal |
| `build.timeline.top` | Lists the slowest mojos after the timeline: 10 for `-Dbuild.timeline.top`, or the number given |
| `build.timeline.color` | `always`, `never` or `auto` (the default), which follows Maven's own colour setting |

For example:

```
mvn4 verify -T1C -b concurrent -Dbuild.timeline -Dbuild.timeline.trace=target/build-trace.json
```

Adding `-Dbuild.timeline` to `.mvn/maven.config` as well prints the timeline on every build.
