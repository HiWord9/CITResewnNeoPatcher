package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Optional;

// Connector's mixin safeguard (2.0.0-beta.5+) cancels loading of all Fabric mods on first launch because of
// ArmorFeatureRendererMixin, even though MixinCancellerExtension cancels it anyway.
// MixinTransformSafeguard is only loaded on first use, so we define our copy of it in Connector's class loader
// before Connector's locator runs, with trigger() passing the failing mods through ConnectorSafeguardFilter first.
class ConnectorSafeguardPatch {
    private static final String CONNECTOR_MODULE = "org.sinytra.connector";
    private static final String ANCHOR_CLASS = "org.sinytra.connector.locator.ConnectorLocator";
    private static final String SAFEGUARD = "org/sinytra/connector/locator/MixinTransformSafeguard";
    private static final String FILTER = "org/sinytra/connector/locator/CITResewnNeoPatcherSafeguardFilter";
    private static final String FILTER_TEMPLATE = ConnectorSafeguardFilter.class.getName().replace('.', '/');
    private static final String TRIGGER = "trigger";
    private static final String TRIGGER_DESC = "(Ljava/util/List;)V";
    private static final String ORIGINAL_TRIGGER = "trigger$" + CITResewnNeoPatcherBootstrap.MODID;

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

        // filter first, as the patched safeguard links against it
        lookup.defineClass(remapFilter());
        try {
            lookup.defineClass(patched);
        } catch (LinkageError e) {
            CITResewnNeoPatcherBootstrap.LOGGER.warn("Connector mixin safeguard is already loaded, not patching it: {}", e.toString());
            return;
        }
        CITResewnNeoPatcherBootstrap.LOGGER.info("Patched Connector mixin safeguard");
    }

    // Renames trigger(List) and adds a new one: failing = Filter.filter(failing); if (!failing.isEmpty()) original(failing);
    private static byte @Nullable [] patchSafeguard(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode trigger = null;
        for (MethodNode method : node.methods) {
            if (method.name.equals(ORIGINAL_TRIGGER)) return null;
            if (method.name.equals(TRIGGER) && method.desc.equals(TRIGGER_DESC)) trigger = method;
        }
        if (trigger == null || (trigger.access & Opcodes.ACC_STATIC) == 0) return null;

        MethodNode replacement = new MethodNode(trigger.access, TRIGGER, TRIGGER_DESC, trigger.signature,
                trigger.exceptions.toArray(new String[0]));
        trigger.name = ORIGINAL_TRIGGER;
        trigger.access = (trigger.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE;

        InsnList code = replacement.instructions;
        LabelNode notEmpty = new LabelNode();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FILTER, "filter", "(Ljava/util/List;)Ljava/util/List;", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true));
        code.add(new JumpInsnNode(Opcodes.IFEQ, notEmpty));
        code.add(new InsnNode(Opcodes.RETURN));
        code.add(notEmpty);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, ORIGINAL_TRIGGER, TRIGGER_DESC, false));
        code.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(replacement);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] remapFilter() throws IOException {
        byte[] template = readClass(ConnectorSafeguardPatch.class.getModule(), FILTER_TEMPLATE);
        if (template == null) throw new IOException("Missing " + FILTER_TEMPLATE);

        ClassWriter writer = new ClassWriter(0);
        new ClassReader(template).accept(new ClassRemapper(writer, new SimpleRemapper(Map.of(FILTER_TEMPLATE, FILTER))), 0);
        return writer.toByteArray();
    }

    private static byte @Nullable [] readClass(Module module, String internalName) throws IOException {
        try (InputStream in = module.getResourceAsStream(internalName + ".class")) {
            return in == null ? null : in.readAllBytes();
        }
    }
}
