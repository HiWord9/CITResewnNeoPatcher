package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CITResewnNeoPatcherCandidateLocator implements IModFileCandidateLocator {

    @Override
    public int getPriority() {
        // Must be > 0 to run before ModsFolderLocator (default priority: 0).
        // This ensures we claim the CITResewn jar before ModsFolderLocator can flag it
        // as an incompatible Fabric mod and show the warning screen.
        return 1;
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        try {
            Path citrPath = findOriginalCitrJar();
            if (citrPath != null) {
                // Marking the jar as "already located" causes ModsFolderLocator to skip it
                // silently in its scan, preventing the Fabric incompatibility warning screen.
                // The jar will be patched and loaded via Sinytra Connector as normal.
                context.addLocated(citrPath);
                CITResewnNeoPatcherBootstrap.LOGGER.info(
                        "CITResewnNeoPatcher: pre-claimed '{}' to suppress Fabric mod incompatibility warning",
                        citrPath.getFileName());
            }
        } catch (Exception e) {
            CITResewnNeoPatcherBootstrap.LOGGER.error(
                    "CITResewnNeoPatcher: failed to pre-claim CITResewn jar; " +
                    "Fabric mod incompatibility warning may appear", e);
        }
    }

    // Returns the path of the original citresewn-*.jar as seen by the NIO filesystem,
    // so the path is identity-equal to what ModsFolderLocator would use in its scan.
    private static @Nullable Path findOriginalCitrJar() {
        try (var stream = Files.list(CITRFiles.MODS_DIR_PATH)) {
            return stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(CITRFiles.JAR_SUFFIX);
                    })
                    .filter(p -> CITRFiles.getCitrFabricConfig(p.toFile()) != null)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            CITResewnNeoPatcherBootstrap.LOGGER.error("Error scanning mods directory for CITResewn jar", e);
            return null;
        }
    }
}
