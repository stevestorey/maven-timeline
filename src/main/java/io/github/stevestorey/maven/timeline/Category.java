package io.github.stevestorey.maven.timeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Groups of lifecycle phases, each drawn with its own letter on the timeline. The phases include both
 * Maven 3's names and Maven 4's, such as {@code resources} for {@code after:resources}, the Maven 4 form
 * of {@code process-resources}.
 */
public enum Category {
    CLEAN('x', "clean", phases("pre-clean", "clean", "post-clean")),
    COMPILE(
            'c',
            "compile",
            phases(
                    "validate",
                    "initialize",
                    "sources",
                    "generate-sources",
                    "process-sources",
                    "resources",
                    "generate-resources",
                    "process-resources",
                    "compile",
                    "process-classes",
                    "ready")),
    TEST_COMPILE(
            'k',
            "test-compile",
            phases(
                    "test-sources",
                    "generate-test-sources",
                    "process-test-sources",
                    "test-resources",
                    "generate-test-resources",
                    "process-test-resources",
                    "test-compile",
                    "process-test-classes")),
    TEST('t', "test", phases("test", "unit-test")),
    PACKAGE('p', "package", phases("prepare-package", "package")),
    VERIFY('v', "verify", phases("pre-integration-test", "integration-test", "post-integration-test", "verify")),
    INSTALL('i', "install/deploy", phases("install", "deploy")),
    SITE('s', "site", phases("pre-site", "site", "post-site", "site-deploy")),
    OTHER('*', "other", phases());

    private final char letter;
    private final String label;
    private final Set<String> phases;

    Category(char letter, String label, Set<String> phases) {
        this.letter = letter;
        this.label = label;
        this.phases = phases;
    }

    public char letter() {
        return letter;
    }

    public String label() {
        return label;
    }

    private static Set<String> phases(String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }

    /**
     * Finds the category of a phase, ignoring Maven 4's {@code before:}, {@code after:} and {@code at:}
     * prefixes.
     */
    public static Category of(String phase) {
        if (phase == null) {
            return OTHER;
        }
        int colon = phase.indexOf(':');
        String name = colon < 0 ? phase : phase.substring(colon + 1);
        for (Category category : values()) {
            if (category.phases.contains(name)) {
                return category;
            }
        }
        return OTHER;
    }
}
