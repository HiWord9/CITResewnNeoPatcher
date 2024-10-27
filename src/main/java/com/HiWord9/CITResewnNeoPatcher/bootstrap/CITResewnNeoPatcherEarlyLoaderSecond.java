package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;

import java.util.List;

public class CITResewnNeoPatcherEarlyLoaderSecond implements IDependencyLocator {
    @Override
    public int getPriority() {
        return -1000 - 1; // right after connector
    }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        try {
            CITResewnNeoPatcherBootstrap.LOGGER.info("Second CITResewnNeoPatcher early load, cleaning up after removing bad mixins");
            BadMixinRemover.cleanUp();
        } catch (Exception e) {
            CITResewnNeoPatcherBootstrap.LOGGER.error(
                    "Exception on Second CITResewnNeoPatcher early load.\n" +
                    "This may be not fatal for current session, " +
                    "but likely will have consequences in future", e
            );
        }
    }
}
