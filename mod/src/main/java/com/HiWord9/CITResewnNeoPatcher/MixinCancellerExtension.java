package com.HiWord9.CITResewnNeoPatcher;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.SortedSet;

// Removes mixins from the ones about to be applied to a class, same as MixinSquared's MixinCanceller does.
// Mixin has no API for that, so it is done through TargetClassContext.mixins.
public class MixinCancellerExtension implements IExtension {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> CANCELLED_MIXINS = Set.of(
            // replaced by restored.HumanoidArmorLayerMixin
            // Connector mixin safeguard is patched to ignore it in bootstrap's ConnectorSafeguardFilter
            "shcm.shsupercm.fabric.citresewn.defaults.mixin.types.armor.ArmorFeatureRendererMixin"
    );

    private volatile Field mixinsField;
    private volatile boolean failed;

    static void install() {
        try {
            Extensions extensions = (Extensions) ((IMixinTransformer) MixinEnvironment.getDefaultEnvironment().getActiveTransformer()).getExtensions();
            extensions.add(new MixinCancellerExtension());
            // active extensions are selected before mixin config plugins are loaded
            extensions.select(MixinEnvironment.getCurrentEnvironment());
        } catch (Throwable t) {
            LOGGER.error("Failed to install mixin canceller, {} will not be cancelled", CANCELLED_MIXINS, t);
        }
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(ITargetClassContext context) {
        SortedSet<?> mixins = getMixins(context);
        if (mixins == null) return;

        mixins.removeIf(mixin -> {
            if (!(mixin instanceof IMixinInfo info) || !CANCELLED_MIXINS.contains(info.getClassName())) return false;
            LOGGER.info("Cancelled mixin {}", info.getClassName());
            return true;
        });
    }

    private SortedSet<?> getMixins(ITargetClassContext context) {
        if (failed) return null;
        try {
            Field field = mixinsField;
            if (field == null) {
                field = context.getClass().getDeclaredField("mixins");
                field.setAccessible(true);
                mixinsField = field;
            }
            return (SortedSet<?>) field.get(context);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LOGGER.error("Failed to access mixins of {}, {} will not be cancelled", context.getClass().getName(), CANCELLED_MIXINS, e);
            return null;
        }
    }

    @Override
    public void postApply(ITargetClassContext context) {}

    @Override
    public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {}
}
