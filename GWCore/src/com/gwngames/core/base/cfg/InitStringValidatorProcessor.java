package com.gwngames.core.base.cfg;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedOptions({ "gw.init.strict" })
public final class InitStringValidatorProcessor extends AbstractProcessor {

    private static final String INIT_FQN = "com.gwngames.core.api.build.Init";

    private static final String COMP_CAT_FQN = "com.gwngames.core.api.build.ComponentCatalog";
    private static final String SUB_CAT_FQN  = "com.gwngames.core.api.build.SubComponentCatalog";
    private static final String PLAT_CAT_FQN = "com.gwngames.core.api.build.PlatformCatalog";
    private static final String MOD_CAT_FQN  = "com.gwngames.core.api.build.ModuleCatalog";

    private static final String MOD_PRI_FQN  = "com.gwngames.core.api.build.ModulePriorities";

    private static final String GEN_PKG = "com.gwngames.core.generated";
    private static final String GEN_CLS = "ModulePriorityRegistry";

    private Messager messager;
    private Elements elements;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        this.messager = env.getMessager();
        this.elements = env.getElementUtils();
        this.filer = env.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
            INIT_FQN,
            COMP_CAT_FQN, SUB_CAT_FQN, PLAT_CAT_FQN, MOD_CAT_FQN,
            MOD_PRI_FQN
        );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeElement initAnn = elements.getTypeElement(INIT_FQN);
        if (initAnn == null) return false;

        boolean strict = "true".equalsIgnoreCase(processingEnv.getOptions().get("gw.init.strict"));

        // Collect allowed constants from catalogs
        Set<String> allowedComponents    = collectCatalogStrings(roundEnv, COMP_CAT_FQN);
        Set<String> allowedSubComponents = collectCatalogStrings(roundEnv, SUB_CAT_FQN);
        Set<String> allowedPlatforms     = collectCatalogStrings(roundEnv, PLAT_CAT_FQN);
        Set<String> allowedModules       = collectCatalogStrings(roundEnv, MOD_CAT_FQN);

        // Validate @Init usage
        for (Element e : roundEnv.getElementsAnnotatedWith(initAnn)) {
            if (!(e instanceof TypeElement)) continue;

            AnnotationMirror initMirror = findAnnotationMirror(e, INIT_FQN);
            if (initMirror == null) continue;

            Map<String, Object> vals = readAnnotationValues(initMirror);

            validateString(e, "component", vals, allowedComponents, strict, "ComponentCatalog");
            validateString(e, "subComp",   vals, allowedSubComponents, strict, "SubComponentCatalog");
            validateString(e, "platform",  vals, allowedPlatforms, strict, "PlatformCatalog");
            validateString(e, "module",    vals, allowedModules, strict, "ModuleCatalog");
        }

        // Collect module priorities & generate registry at end of processing
        Map<String, Integer> priorities = collectModulePriorities(roundEnv);

        // In strict mode: warn/error if priority is missing for some declared module id
        if (strict && !allowedModules.isEmpty()) {
            for (String m : allowedModules) {
                if (!priorities.containsKey(norm(m))) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Module id \"" + m + "\" is declared in a @ModuleCatalog but has no priority declared in any @ModulePriorities.",
                        // attach to nothing global (compiler prints it anyway)
                        null
                    );
                }
            }
        }

        // Only generate once, on final round
        if (roundEnv.processingOver()) {
            try {
                generateRegistry(priorities);
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed generating " + GEN_CLS + ": " + ex);
            }
        }

        return false;
    }

    private Set<String> collectCatalogStrings(RoundEnvironment env, String catalogAnnFqn) {
        TypeElement ann = elements.getTypeElement(catalogAnnFqn);
        if (ann == null) return Set.of();

        Set<String> out = new HashSet<>();
        for (Element type : env.getElementsAnnotatedWith(ann)) {
            if (!(type instanceof TypeElement)) continue;

            for (Element enclosed : type.getEnclosedElements()) {
                if (enclosed.getKind() != ElementKind.FIELD) continue;
                VariableElement f = (VariableElement) enclosed;

                Set<Modifier> mods = f.getModifiers();
                if (!mods.containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))) continue;
                if (!"java.lang.String".equals(f.asType().toString())) continue;

                Object cv = f.getConstantValue();
                if (cv instanceof String s && !s.isBlank()) out.add(s);
            }
        }
        return out;
    }

    private Map<String, Integer> collectModulePriorities(RoundEnvironment env) {
        TypeElement ann = elements.getTypeElement(MOD_PRI_FQN);
        if (ann == null) return new HashMap<>();

        Map<String, Integer> out = new HashMap<>();
        for (Element type : env.getElementsAnnotatedWith(ann)) {
            AnnotationMirror m = findAnnotationMirror(type, MOD_PRI_FQN);
            if (m == null) continue;

            // value = array of @Entry(id=..., priority=...)
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : m.getElementValues().entrySet()) {
                if (!"value".equals(e.getKey().getSimpleName().toString())) continue;

                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> entries = (List<? extends AnnotationValue>) e.getValue().getValue();
                for (AnnotationValue av : entries) {
                    AnnotationMirror entryMirror = (AnnotationMirror) av.getValue();
                    String id = null;
                    Integer pr = null;
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> ev : entryMirror.getElementValues().entrySet()) {
                        String n = ev.getKey().getSimpleName().toString();
                        Object v = ev.getValue().getValue();
                        if ("id".equals(n)) id = (String) v;
                        if ("priority".equals(n)) pr = (Integer) v;
                    }
                    if (id == null || pr == null) continue;

                    String key = norm(id);
                    Integer prev = out.putIfAbsent(key, pr);
                    if (prev != null && !prev.equals(pr)) {
                        messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Duplicate module priority for id \"" + id + "\": " + prev + " vs " + pr +
                                ". Keep only one @ModulePriorities.Entry for this id.",
                            type
                        );
                    }
                }
            }
        }
        return out;
    }

    private void generateRegistry(Map<String, Integer> priorities) throws IOException {
        // Always generate; if empty, it still compiles and returns 0.
        String qn = GEN_PKG + "." + GEN_CLS;

        // Deterministic output
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(priorities.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GEN_PKG).append(";\n\n")
            .append("import java.util.*;\n\n")
            .append("/** GENERATED: module priority lookup (id -> int). */\n")
            .append("public final class ").append(GEN_CLS).append(" {\n")
            .append("  private ").append(GEN_CLS).append("() {}\n\n")
            .append("  private static final Map<String,Integer> PRIORITY = Map.ofEntries(\n");

        if (entries.isEmpty()) {
            sb.append("    Map.entry(\"unimplemented\", 0)\n"); // minimal valid Map.ofEntries
        } else {
            for (int i = 0; i < entries.size(); i++) {
                var en = entries.get(i);
                sb.append("    Map.entry(\"").append(escape(en.getKey())).append("\", ").append(en.getValue()).append(")");
                sb.append(i == entries.size() - 1 ? "\n" : ",\n");
            }
        }

        sb.append("  );\n\n")
            .append("  /** Returns priority for a module id (case-insensitive). Unknown -> 0. */\n")
            .append("  public static int priorityOf(String moduleId) {\n")
            .append("    if (moduleId == null) return 0;\n")
            .append("    return PRIORITY.getOrDefault(moduleId.trim().toLowerCase(Locale.ROOT), 0);\n")
            .append("  }\n")
            .append("}\n");

        Writer w = filer.createSourceFile(qn).openWriter();
        try (w) {
            w.write(sb.toString());
        }
    }

    private void validateString(Element where, String attr, Map<String, Object> vals, Set<String> allowed, boolean strict, String catalogName) {
        Object v = vals.get(attr);
        if (!(v instanceof String s)) return; // missing => default

        if (!strict && s.trim().isEmpty()) return;

        if (!allowed.contains(s)) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "@Init." + attr + "=\"" + s + "\" is not declared in any @" + catalogName + " class.",
                where
            );
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static AnnotationMirror findAnnotationMirror(Element e, String annFqn) {
        for (AnnotationMirror m : e.getAnnotationMirrors()) {
            Element a = m.getAnnotationType().asElement();
            if (a instanceof TypeElement te && te.getQualifiedName().toString().equals(annFqn)) return m;
        }
        return null;
    }

    private static Map<String, Object> readAnnotationValues(AnnotationMirror mirror) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> en : mirror.getElementValues().entrySet()) {
            out.put(en.getKey().getSimpleName().toString(), en.getValue().getValue());
        }
        return out;
    }
}
