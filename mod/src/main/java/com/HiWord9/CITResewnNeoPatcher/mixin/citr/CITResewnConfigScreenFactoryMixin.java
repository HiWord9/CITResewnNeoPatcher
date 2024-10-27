package com.HiWord9.CITResewnNeoPatcher.mixin.citr;

import com.HiWord9.CITResewnNeoPatcher.CITResewnNeoPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import shcm.shsupercm.fabric.citresewn.config.CITResewnConfigScreenFactory;

@Mixin(CITResewnConfigScreenFactory.class)
public class CITResewnConfigScreenFactoryMixin {
    @ModifyArg(
            method = "create",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/loader/api/FabricLoader;isModLoaded(Ljava/lang/String;)Z"
            )
    )
    private static String injected(String obj) {
        return CITResewnNeoPatcher.fixId(obj);
    }
}

