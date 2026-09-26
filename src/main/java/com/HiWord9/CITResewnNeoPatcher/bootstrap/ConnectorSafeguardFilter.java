package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Gets copied into Connector's package by ConnectorSafeguardPatch, so it can't use other classes of this mod.
// Connector classes are accessed reflectively, as Adapter's audit trail classes changed in Connector 2.0.0-beta.13.
public final class ConnectorSafeguardFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger("citresewn_neopatcher");

    private static final String CITR_DEFAULTS_ID = "citresewn-defaults";
    // cancelled by MixinCancellerExtension
    private static final String CANCELLED_MIXIN = "shcm/shsupercm/fabric/citresewn/defaults/mixin/types/armor/ArmorFeatureRendererMixin";

    public static List<Object> filter(List<Object> failing) {
        try {
            List<Object> remaining = new ArrayList<>();
            for (Object modPath : failing) {
                Object auditTrail = call(modPath, "auditTrail");
                if (isCitrDefaults(modPath)) ignoreCancelledMixin(auditTrail);
                if ((boolean) call(auditTrail, "hasFailingMixins")) remaining.add(modPath);
            }
            return remaining;
        } catch (Throwable t) {
            LOGGER.error("Failed to filter Connector mixin safeguard report", t);
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
                LOGGER.info("Removed {} from Connector mixin safeguard report", CANCELLED_MIXIN);
            }
        }
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
