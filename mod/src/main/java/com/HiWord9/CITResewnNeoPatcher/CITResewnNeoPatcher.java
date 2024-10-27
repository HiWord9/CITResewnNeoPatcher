package com.HiWord9.CITResewnNeoPatcher;

import com.HiWord9.CITResewnNeoPatcher.bootstrap.CITResewnNeoPatcherBootstrap;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CITResewnNeoPatcher.MODID)
public class CITResewnNeoPatcher {
    public static final String MODID = CITResewnNeoPatcherBootstrap.MODID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public CITResewnNeoPatcher(IEventBus modEventBus) {}

    public static String fixId(String obj) {
        return obj.replace('-', '_');
    }
}
