package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Optional;

// Connector's mixin safeguard (2.0.0-beta.5+) cancels loading of all Fabric mods on first launch because of
// ArmorFeatureRendererMixin, even though MixinCancellerExtension cancels it anyway.
// MixinTransformSafeguard is only loaded on first use, so we define our copy of it in Connector's class loader
// before Connector's locator runs, with its trigger() renamed and methods of ConnectorSafeguardTemplate added.
class ConnectorSafeguardPatch {
    private static final String CONNECTOR_MODULE = "org.sinytra.connector";
    private static final String ANCHOR_CLASS = "org.sinytra.connector.locator.ConnectorLocator";
    private static final String SAFEGUARD = "org/sinytra/connector/locator/MixinTransformSafeguard";
    private static final String TEMPLATE = ConnectorSafeguardTemplate.class.getName().replace('.', '/');
    private static final String TRIGGER = "trigger";
    private static final String TRIGGER_DESC = "(Ljava/util/List;)V";
    private static final String ORIGINAL_TRIGGER = ConnectorSafeguardTemplate.ORIGINAL_TRIGGER;

    static void apply() {
        try {
            tryApply();
        } catch (Throwable t) {
            CITResewnNeoPatcherBootstrap.LOGGER.warn("Could not patch Connector mixin safeguard", t);
        }
    }

    private static void tryApply() throws Exception {
        ModuleLayer layer = ConnectorSafeguardPatch.class.getModule().getLayer();
        Optional<Module> connector = layer != null ? layer.findModule(CONNECTOR_MODULE) : Optional.empty();
        if (connector.isEmpty()) {
            CITResewnNeoPatcherBootstrap.LOGGER.warn("Connector module not found, not patching its mixin safeguard");
            return;
        }

        byte[] original = readClass(connector.get(), SAFEGUARD);
        if (original == null) {
            CITResewnNeoPatcherBootstrap.LOGGER.info("Connector has no mixin safeguard, nothing to patch");
            return;
        }

        byte[] patched = patchSafeguard(original);
        if (patched == null) {
            CITResewnNeoPatcherBootstrap.LOGGER.warn("Unexpected Connector mixin safeguard structure, not patching it");
            return;
        }

        ConnectorSafeguardPatch.class.getModule().addReads(connector.get());
        // allowed since Connector is an automatic module, which opens all its packages
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Class.forName(connector.get(), ANCHOR_CLASS), MethodHandles.lookup());
        try {
            lookup.defineClass(patched);
        } catch (LinkageError e) {
            CITResewnNeoPatcherBootstrap.LOGGER.warn("Connector mixin safeguard is already loaded, not patching it: {}", e.toString());
            return;
        }
        CITResewnNeoPatcherBootstrap.LOGGER.info("Patched Connector mixin safeguard");
    }

    private static byte @Nullable [] patchSafeguard(byte[] original) throws IOException {
        ClassNode safeguard = new ClassNode();
        new ClassReader(original).accept(safeguard, 0);

        MethodNode trigger = findMethod(safeguard, TRIGGER, TRIGGER_DESC);
        if (trigger == null || (trigger.access & Opcodes.ACC_STATIC) == 0) return null;
        if (findMethod(safeguard, ORIGINAL_TRIGGER, TRIGGER_DESC) != null) return null;
        trigger.name = ORIGINAL_TRIGGER;

        byte[] templateBytes = readClass(ConnectorSafeguardPatch.class.getModule(), TEMPLATE);
        if (templateBytes == null) throw new IOException("Missing " + TEMPLATE);
        // renaming the template to the safeguard makes calls between copied methods point to the safeguard
        ClassNode template = new ClassNode();
        new ClassReader(templateBytes).accept(new ClassRemapper(template, new SimpleRemapper(Map.of(TEMPLATE, safeguard.name))), 0);

        for (MethodNode method : template.methods) {
            if (method.name.equals("<init>") || method.name.equals(ORIGINAL_TRIGGER)) continue;
            if (findMethod(safeguard, method.name, method.desc) != null) return null;
            safeguard.methods.add(method);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        safeguard.accept(writer);
        return writer.toByteArray();
    }

    private static @Nullable MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) return method;
        }
        return null;
    }

    private static byte @Nullable [] readClass(Module module, String internalName) throws IOException {
        try (InputStream in = module.getResourceAsStream(internalName + ".class")) {
            return in == null ? null : in.readAllBytes();
        }
    }
}
