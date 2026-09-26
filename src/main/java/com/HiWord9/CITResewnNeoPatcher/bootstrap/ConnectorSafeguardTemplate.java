package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Methods of this class get copied into Connector's MixinTransformSafeguard by ConnectorSafeguardPatch, so they
// can only use each other, JDK and slf4j, without static fields or lambdas.
// Connector classes are accessed reflectively, as Adapter's audit trail classes changed in Connector 2.0.0-beta.13.
public final class ConnectorSafeguardTemplate {
    private static final String CITR_DEFAULTS_ID = "citresewn-defaults";
    // cancelled by MixinCancellerExtension
    private static final String CANCELLED_MIXIN = "shcm/shsupercm/fabric/citresewn/defaults/mixin/types/armor/ArmorFeatureRendererMixin";
    static final String ORIGINAL_TRIGGER = "trigger$citresewn_neopatcher";

    public static void trigger(List<Object> failing) {
        List<Object> remaining = filter(failing);
        if (!remaining.isEmpty()) trigger$citresewn_neopatcher(remaining);
    }

    // replaced with Connector's original trigger, name has to be ORIGINAL_TRIGGER
    private static void trigger$citresewn_neopatcher(List<Object> failing) {}

    private static List<Object> filter(List<Object> failing) {
        try {
            List<Object> remaining = new ArrayList<>();
            for (Object modPath : failing) {
                Object auditTrail = call(modPath, "auditTrail");
                if (isCitrDefaults(modPath)) ignoreCancelledMixin(auditTrail);
                if ((boolean) call(auditTrail, "hasFailingMixins")) remaining.add(modPath);
            }
            return remaining;
        } catch (Throwable t) {
            LoggerFactory.getLogger("citresewn_neopatcher").error("Failed to filter Connector mixin safeguard report", t);
            return failing;
        }
    }

    private static boolean isCitrDefaults(Object modPath) throws ReflectiveOperationException {
        Object modMetadata = call(call(call(modPath, "output"), "metadata"), "modMetadata");
        // Connector replaces '-' in Fabric mod ids
        return CITR_DEFAULTS_ID.replace('-', '_').equals(String.valueOf(call(modMetadata, "getId")).replace('-', '_'));
    }

    @SuppressWarnings("unchecked")
    private static void ignoreCancelledMixin(Object auditTrail) throws ReflectiveOperationException {
        Map<Object, Object> candidates = (Map<Object, Object>) call(auditTrail, "getCandidates");
        for (Object candidate : List.copyOf(candidates.keySet())) {
            Object classNode = call(candidate, "classNode");
            if (CANCELLED_MIXIN.equals(classNode.getClass().getField("name").get(classNode))) {
                candidates.remove(candidate);
                LoggerFactory.getLogger("citresewn_neopatcher").info("Removed {} from Connector mixin safeguard report", CANCELLED_MIXIN);
            }
        }
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
