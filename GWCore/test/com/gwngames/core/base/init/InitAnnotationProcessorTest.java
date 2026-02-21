package com.gwngames.core.base.init;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.InitStringValidatorProcessor;
import org.junit.jupiter.api.Assertions;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Compile-time tests for the @Init string validator annotation processor.
 * This runs an in-memory compilation using JavaCompiler and asserts that:
 *  - Valid @Init values compile successfully.
 *  - Invalid @Init values fail compilation and emit a useful error.
 */
public final class InitAnnotationProcessorTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        // --- 1) should PASS: strings exist in catalog constants ---
        CompilationResult ok = compile(
            Map.ofEntries(
                // Minimal catalogs visible to the processor in this in-memory compilation
                Map.entry("com.example.catalogs.TestModuleCatalog", """
                    package com.example.catalogs;

                    import com.gwngames.core.api.build.catalog.ModuleCatalog;

                    @ModuleCatalog
                    public final class TestModuleCatalog {
                        private TestModuleCatalog() {}
                        public static final String CORE = "core";
                    }
                """),
                Map.entry("com.example.catalogs.TestComponentCatalog", """
                    package com.example.catalogs;

                    import com.gwngames.core.api.build.catalog.ComponentCatalog;

                    @ComponentCatalog
                    public final class TestComponentCatalog {
                        private TestComponentCatalog() {}
                        public static final String NONE = "NONE";
                    }
                """),
                Map.entry("com.example.catalogs.TestSubComponentCatalog", """
                    package com.example.catalogs;

                    import com.gwngames.core.api.build.catalog.SubComponentCatalog;

                    @SubComponentCatalog
                    public final class TestSubComponentCatalog {
                        private TestSubComponentCatalog() {}
                        public static final String NONE = "NONE";
                        public static final String SIMPLE_EVENT = "SIMPLE_EVENT";
                    }
                """),
                Map.entry("com.example.catalogs.TestPlatformCatalog", """
                    package com.example.catalogs;

                    import com.gwngames.core.api.build.catalog.PlatformCatalog;

                    @PlatformCatalog
                    public final class TestPlatformCatalog {
                        private TestPlatformCatalog() {}
                        public static final String ALL = "ALL";
                    }
                """),

                // The class under test
                Map.entry("com.example.OkInit", """
                    package com.example;

                    import com.gwngames.core.api.build.Init;
                    import com.example.catalogs.TestModuleCatalog;
                    import com.example.catalogs.TestComponentCatalog;
                    import com.example.catalogs.TestSubComponentCatalog;
                    import com.example.catalogs.TestPlatformCatalog;

                    @Init(
                      module = TestModuleCatalog.CORE,
                      component = TestComponentCatalog.NONE,
                      subComp = TestSubComponentCatalog.NONE,
                      platform = TestPlatformCatalog.ALL
                    )
                    public final class OkInit {}
                """)
            ),
            /*strict*/ true
        );

        Assertions.assertTrue(ok.success, () -> "Expected compilation success, got diagnostics:\n" + ok.diagnosticsText);

        // --- 2) should FAIL: invalid strings ---
        CompilationResult bad = compile(
            Map.of(
                "com.example.BadInit", """
                    package com.example;

                    import com.gwngames.core.api.build.Init;

                    @Init(
                      module = "nope",
                      component = "NOT_A_REAL_COMPONENT",
                      subComp = "NOT_A_REAL_SUBCOMP",
                      platform = "NOT_A_REAL_PLATFORM"
                    )
                    public final class BadInit {}
                """
            ),
            /*strict*/ true
        );

        Assertions.assertFalse(bad.success, "Expected compilation failure but compilation succeeded.");

        Assertions.assertTrue(
            bad.diagnosticsText.contains("NOT_A_REAL_COMPONENT") || bad.diagnosticsText.contains("@Init.component"),
            () -> "Expected error mentioning invalid component, got:\n" + bad.diagnosticsText
        );

        Assertions.assertTrue(
            bad.diagnosticsText.contains("NOT_A_REAL_SUBCOMP") || bad.diagnosticsText.contains("@Init.subComp"),
            () -> "Expected error mentioning invalid subComp, got:\n" + bad.diagnosticsText
        );

        Assertions.assertTrue(
            bad.diagnosticsText.contains("NOT_A_REAL_PLATFORM") || bad.diagnosticsText.contains("@Init.platform"),
            () -> "Expected error mentioning invalid platform, got:\n" + bad.diagnosticsText
        );

        Assertions.assertTrue(
            bad.diagnosticsText.contains("module") || bad.diagnosticsText.contains("@Init.module") || bad.diagnosticsText.contains("nope"),
            () -> "Expected error mentioning invalid module, got:\n" + bad.diagnosticsText
        );

        log.info("✅ Init annotation processor compile-time validation test passed.");
    }

    /* =======================================================================
     * In-memory compilation harness
     * ======================================================================= */

    private record CompilationResult(boolean success, String diagnosticsText) {}

    private static CompilationResult compile(Map<String, String> fqnToSource, boolean strict) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompilationResult(false, "No system JavaCompiler available. Tests must run on a JDK.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager stdManager =
            compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);

        List<JavaFileObject> sources = new ArrayList<>();
        for (Map.Entry<String, String> e : fqnToSource.entrySet()) {
            sources.add(new StringJavaFileObject(e.getKey(), e.getValue()));
        }

        String classpath = System.getProperty("java.class.path");

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
