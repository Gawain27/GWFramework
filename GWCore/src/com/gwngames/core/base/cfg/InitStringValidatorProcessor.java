package com.gwngames.core.base.cfg;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
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

    private Messager messager;
    private Elements elements;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.elements = processingEnv.getElementUtils();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(INIT_FQN, COMP_CAT_FQN, SUB_CAT_FQN, PLAT_CAT_FQN, MOD_CAT_FQN);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeElement initAnn = elements.getTypeElement(INIT_FQN);
        if (initAnn == null) return false;

        // Collect allowed string constants from catalogs
        Set<String> allowedComponents   = collectCatalogStrings(roundEnv, COMP_CAT_FQN);
        Set<String> allowedSubComponents= collectCatalogStrings(roundEnv, SUB_CAT_FQN);
        Set<String> allowedPlatforms    = collectCatalogStrings(roundEnv, PLAT_CAT_FQN);
        Set<String> allowedModules      = collectCatalogStrings(roundEnv, MOD_CAT_FQN);

        boolean strict = "true".equalsIgnoreCase(processingEnv.getOptions().get("gw.init.strict"));

        for (Element e : roundEnv.getElementsAnnotatedWith(initAnn)) {
            if (!(e instanceof TypeElement)) continue;

            AnnotationMirror initMirror = findAnnotationMirror(e, INIT_FQN);
            if (initMirror == null) continue;

            Map<String, Object> vals = readAnnotationValues(initMirror);

            // You use String component/platform/subComp in Init
            validateString(e, "component", vals, allowedComponents, strict);
            validateString(e, "subComp",   vals, allowedSubComponents, strict);
            validateString(e, "platform",  vals, allowedPlatforms, strict);
            validateString(e, "module", vals, allowedModules, strict);
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

                // public static final String XXX = "YYY";
                Set<Modifier> mods = f.getModifiers();
                if (!mods.containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))) continue;

                TypeMirror t = f.asType();
                if (!"java.lang.String".equals(t.toString())) continue;

                Object cv = f.getConstantValue();
                if (cv instanceof String s && !s.isBlank()) out.add(s);
            }
        }
        return out;
    }

    private void validateString(Element where, String attr, Map<String, Object> vals, Set<String> allowed, boolean strict) {
        Object v = vals.get(attr);
        if (!(v instanceof String s)) return; // missing => default

        // In non-strict mode, allow empty/blank (lets you keep placeholders)
        if (!strict && s.trim().isEmpty()) return;

        if (!allowed.contains(s)) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "@Init." + attr + "=\"" + s + "\" is not declared in any @" + simpleNameFor(attr) + " class. " +
                    "Declare it as a public static final String in a catalog class, or fix the value.\n" +
                    "Allowed examples: " + sample(allowed),
                where
            );
        }
    }

    private String simpleNameFor(String attr) {
        return switch (attr) {
            case "component" -> "ComponentCatalog";
            case "subComp"   -> "SubComponentCatalog";
            case "platform"  -> "PlatformCatalog";
            case "module"    -> "ModuleCatalog";
            default          -> "Catalog";
        };
    }

    private static String sample(Set<String> set) {
        if (set.isEmpty()) return "(none found)";
        return set.stream().sorted().limit(8).toList().toString();
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
            String name = en.getKey().getSimpleName().toString();
            Object val = en.getValue().getValue();
            out.put(name, val);
        }
        return out;
    }
}
