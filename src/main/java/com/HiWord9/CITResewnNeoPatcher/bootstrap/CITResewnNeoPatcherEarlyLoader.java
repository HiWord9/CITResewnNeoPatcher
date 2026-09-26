package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.locating.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CITResewnNeoPatcherEarlyLoader implements IDependencyLocator {

    @Override
    public int getPriority() {
        return -1000 + 1; // right before connector
    }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        CITResewnNeoPatcherBootstrap.LOGGER.info("CITResewnNeoPatcher early load, patching Connector mixin safeguard and loading main mod");

        try {
            ConnectorSafeguardPatch.apply();
        } catch (LinkageError e) {
            CITResewnNeoPatcherBootstrap.LOGGER.warn("Could not patch Connector mixin safeguard", e);
        }

        try {
            loadMainMod(pipeline);
        } catch (Exception e) {
            CITResewnNeoPatcherBootstrap.LOGGER.error("Exception on CITResewnNeoPatcher early load", e);
            throw new RuntimeException(e);
        }
    }

    private static void loadMainMod(IDiscoveryPipeline pipeline) throws Exception {
        URL url = CITResewnNeoPatcherEarlyLoader.class.getResource("/META-INF/mod/citrnp-mod-file.jar");
        Path path = url != null ? Paths.get(url.toURI()) : null;
        if(path == null || !Files.exists(path)) {
            throw new IllegalStateException("CITResewnNeoPatcher JAR does not exist!");
        }
        CITResewnNeoPatcherBootstrap.LOGGER.info("Loading CITResewnNeoPatcher mod module");
        // Use JarContents so it doesn't detect that the primary JAR is already loaded and skip the path
        pipeline.addJarContent(JarContents.of(path), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ALWAYS);
    }
}
