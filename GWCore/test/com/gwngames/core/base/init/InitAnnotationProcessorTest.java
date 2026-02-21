package com.gwngames.core.base.init;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.InitStringValidatorProcessor;
import com.gwngames.core.util.ClassUtils;
import org.junit.jupiter.api.Assertions;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Compile-time tests for the @Init string validator annotation processor.
 *
 * IMPORTANT:
 * The processor discovers catalogs via RoundEnvironment, which only contains
 * elements compiled in the *current* compilation task (not classpath classes).
 * Therefore we compile the real catalogs as in-memory sources together with
 * the @Init test types.
 */
public final class InitAnnotationProcessorTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        // Real catalogs must be compiled in the same in-memory compilation.
        Map<String, String> catalogs = frameworkCatalogSources();

        // ------------------------------------------------------------------
        // 1) should PASS: real framework constants exist in catalogs
        // ------------------------------------------------------------------
        Map<String, String> okSources = new LinkedHashMap<>(catalogs);
        okSources.put("com.example.OkInit", """
            package com.example;

            import com.gwngames.core.api.build.Init;
            import com.gwngames.core.CoreModule;
            import com.gwngames.core.CoreComponent;
            import com.gwngames.core.CoreSubComponent;
            import com.gwngames.starter.Platform;

            @Init(
              module = CoreModule.CORE,
              component = CoreComponent.NONE,
              subComp = CoreSubComponent.NONE,
              platform = Platform.ALL
            )
            public final class OkInit {}
        """);

        CompilationResult ok = compile(okSources, /*strict*/ true);

        Assertions.assertTrue(
            ok.success,
            () -> "Expected compilation success, got diagnostics:\n" + ok.diagnosticsText
        );

        // ------------------------------------------------------------------
        // 2) should FAIL: invalid strings should be rejected
        // ------------------------------------------------------------------
        Map<String, String> badSources = new LinkedHashMap<>(catalogs);
        badSources.put("com.example.BadInit", """
            package com.example;

            import com.gwngames.core.api.build.Init;

            @Init(
              module = "nope",
              component = "NOT_A_REAL_COMPONENT",
              subComp = "NOT_A_REAL_SUBCOMP",
              platform = "NOT_A_REAL_PLATFORM"
            )
            public final class BadInit {}
        """);

        CompilationResult bad = compile(badSources, /*strict*/ true);

        Assertions.assertFalse(
            bad.success,
            () -> "Expected compilation failure but compilation succeeded.\nDiagnostics:\n" + bad.diagnosticsText
        );

        // Match something stable from your processor output
        Assertions.assertTrue(
            containsAny(bad.diagnosticsText,
                "@Init.component=\"NOT_A_REAL_COMPONENT\"",
                "NOT_A_REAL_COMPONENT",
                "@Init.component"),
            () -> "Expected error mentioning invalid component, got:\n" + bad.diagnosticsText
        );

        Assertions.assertTrue(
            containsAny(bad.diagnosticsText,
                "@Init.subComp=\"NOT_A_REAL_SUBCOMP\"",
                "NOT_A_REAL_SUBCOMP",
                "@Init.subComp"),
            () -> "Expected error mentioning invalid subComp, got:\n" + bad.diagnosticsText
        );

        Assertions.assertTrue(
            containsAny(bad.diagnosticsText,
                "@Init.platform=\"NOT_A_REAL_PLATFORM\"",
                "NOT_A_REAL_PLATFORM",
                "@Init.platform"),
            () -> "Expected error mentioning invalid platform, got:\n" + bad.diagnosticsText
        );

        Assertions.assertTrue(
            containsAny(bad.diagnosticsText,
                "@Init.module=\"nope\"",
                "nope",
                "@Init.module",
                "ModuleCatalog"),
            () -> "Expected error mentioning invalid module, got:\n" + bad.diagnosticsText
        );

        log.info("✅ Init annotation processor compile-time validation test passed.");
    }

    /**
     * These are the *real* catalogs, as sources, so the processor can see them
     * through RoundEnvironment during the in-memory compilation.
     *
     * Keep these in sync with the canonical classes in the repo.
     * (We only need the annotations + String constants.)
     */
    private static Map<String, String> frameworkCatalogSources() {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("com.gwngames.core.CoreComponent", """
            package com.gwngames.core;

            import com.gwngames.catalog.ComponentCatalog;

            @ComponentCatalog
            public final class CoreComponent {
                private CoreComponent() {}

                public static final String NONE = "NONE";
                // (other constants omitted; not needed for this test)
            }
        """);

        m.put("com.gwngames.core.CoreSubComponent", """
            package com.gwngames.core;

            import com.gwngames.catalog.SubComponentCatalog;

            @SubComponentCatalog
            public final class CoreSubComponent {
                private CoreSubComponent() {}

                public static final String NONE = "NONE";
                public static final String SIMPLE_EVENT = "SIMPLE_EVENT";
                public static final String SYSTEM_QUEUE  = "SYSTEM_QUEUE";
                public static final String COMM_QUEUE    = "COMM_QUEUE";
                public static final String EVENT_STATUS_LOGGER = "EVENT_STATUS_LOGGER";
            }
        """);

        m.put("com.gwngames.starter.Platform", """
            package com.gwngames.starter;

            import com.gwngames.catalog.PlatformCatalog;

            @PlatformCatalog
            public final class Platform {
                private Platform() {}

                public static final String ALL = "ALL";
                public static final String MOBILE = "MOBILE";
                public static final String DESKTOP = "DESKTOP";
                public static final String ANDROID = "ANDROID";
                public static final String IOS = "IOS";
                public static final String WEB = "WEB";
                public static final String CONSOLE = "CONSOLE";
            }
        """);

        m.put("com.gwngames.core.CoreModule", """
            package com.gwngames.core;

            import com.gwngames.catalog.ModuleCatalog;
            import com.gwngames.catalog.ModulePriorities;

            @ModuleCatalog
            @ModulePriorities({
                @ModulePriorities.Entry(id = "archive", priority = -1),
                @ModulePriorities.Entry(id = "core", priority = 5),
            })
            public final class CoreModule {
                private CoreModule() {}

                public static final String ARCHIVE = "archive";
                public static final String CORE = "core";
            }
        """);

        return m;
    }

    /* =======================================================================
     * In-memory compilation harness
     * ======================================================================= */

    private record CompilationResult(boolean success, String diagnosticsText) {}

    private static CompilationResult compile(Map<String, String> fqnToSource, boolean strict) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompilationResult(false,
                "No system JavaCompiler available. Tests must run on a JDK.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager stdManager =
            compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);

        List<JavaFileObject> sources = new ArrayList<>();
        for (Map.Entry<String, String> e : fqnToSource.entrySet()) {
            sources.add(new StringJavaFileObject(e.getKey(), e.getValue()));
        }

        String classpath = ClassUtils.effectiveClasspath();

        List<String> options = new ArrayList<>(List.of(
            "-classpath", classpath,
            "-proc:only"
        ));
        if (strict) {
            options.add("-Agw.init.strict=true");
        }

        Iterable<? extends Processor> processors = List.of(new InitStringValidatorProcessor());

        JavaCompiler.CompilationTask task =
            compiler.getTask(null, stdManager, diagnostics, options, null, sources);
        task.setProcessors(processors);

        boolean success = Boolean.TRUE.equals(task.call());

        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            sb.append(d.getKind())
                .append(": ")
                .append(d.getMessage(Locale.ROOT))
                .append(" (")
                .append(d.getSource() != null ? d.getSource().getName() : "no-source")
                .append(":")
                .append(d.getLineNumber())
                .append(")")
                .append("\n");
        }

        return new CompilationResult(success, sb.toString());
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null) return false;
        for (String n : needles) {
            if (n != null && !n.isEmpty() && haystack.contains(n)) return true;
        }
        return false;
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        StringJavaFileObject(String fqn, String code) {
            super(uriFor(fqn), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }

        private static URI uriFor(String fqn) {
            return URI.create("string:///" + fqn.replace('.', '/') + Kind.SOURCE.extension);
        }
    }
}
