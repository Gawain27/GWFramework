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

        // --- 1) should PASS: all strings exist in catalog constants ---
        CompilationResult ok = compile(
            Map.of(
                "com.example.OkInit", """
                    package com.example;

                    import com.gwngames.core.api.build.Init;
                    import com.gwngames.core.data.ModuleNames;
                    import com.gwngames.core.CoreComponent;
                    import com.gwngames.core.CoreSubComponent;
                    import com.gwngames.starter.Platform;

                    @Init(
                      module = ModuleNames.CORE,
                      component = CoreComponent.NONE,
                      subComp = CoreSubComponent.NONE,
                      platform = Platform.ALL
                    )
                    public final class OkInit {}
                """
            ),
            /*strict*/ true
        );

        Assertions.assertTrue(ok.success, () -> "Expected compilation success, got diagnostics:\n" + ok.diagnosticsText);

        // --- 2) should FAIL: invalid component string ---
        CompilationResult bad = compile(
            Map.of(
                "com.example.BadInit", """
                    package com.example;

                    import com.gwngames.core.api.build.Init;
                    import com.gwngames.core.data.ModuleNames;

                    @Init(
                      module = ModuleNames.CORE,
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

        // Check processor message (match something stable)
        Assertions.assertTrue(
            bad.diagnosticsText.contains("@Init.component=\"NOT_A_REAL_COMPONENT\"")
                || bad.diagnosticsText.contains("Init.component")
                || bad.diagnosticsText.contains("NOT_A_REAL_COMPONENT"),
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

        log.info("✅ Init annotation processor compile-time validation test passed.");
    }

    /* =======================================================================
     * In-memory compilation harness
     * ======================================================================= */

    private record CompilationResult(boolean success, String diagnosticsText) {
    }

    private static CompilationResult compile(Map<String, String> fqnToSource, boolean strict) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            // This happens if tests run on a JRE not a JDK.
            return new CompilationResult(false, "No system JavaCompiler available. Tests must run on a JDK.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager stdManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);

        // In-memory sources
        List<JavaFileObject> sources = new ArrayList<>();
        for (Map.Entry<String, String> e : fqnToSource.entrySet()) {
            sources.add(new StringJavaFileObject(e.getKey(), e.getValue()));
        }

        // Use current test classpath so catalog classes + Init are visible
        String classpath = System.getProperty("java.class.path");

        List<String> options = new ArrayList<>(List.of(
            "-classpath", classpath,
            "-proc:only" // only run annotation processing (no class output needed)
        ));
        if (strict) {
            options.add("-Agw.init.strict=true");
        }

        // Attach the processor directly (no service loading needed)
        Iterable<? extends Processor> processors = List.of(new InitStringValidatorProcessor());

        JavaCompiler.CompilationTask task =
            compiler.getTask(null, stdManager, diagnostics, options, null, sources);
        task.setProcessors(processors);

        boolean success = Boolean.TRUE.equals(task.call());

        // Render diagnostics
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
