package com.HiWord9.CITResewnNeoPatcher.mixin.citr;

import com.HiWord9.CITResewnNeoPatcher.CITResewnNeoPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import shcm.shsupercm.fabric.citresewn.pack.GlobalProperties;

@Mixin(GlobalProperties.class)
public class GlobalPropertiesMixin {
    @ModifyArg(
            method = "callHandlers",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z",
                    ordinal = 0
            )
    )
    private Object injected(Object obj) {
        return CITResewnNeoPatcher.fixId((String) obj);
    }
}
