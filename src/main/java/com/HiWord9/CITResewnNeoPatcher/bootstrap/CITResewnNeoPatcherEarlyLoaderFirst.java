package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.locating.*;

import java.io.File;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class CITResewnNeoPatcherEarlyLoaderFirst implements IDependencyLocator {

    @Override
    public int getPriority() {
        return -1000 + 1; // right before connector
    }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        CITResewnNeoPatcherBootstrap.LOGGER.info("First CITResewnNeoPatcher early load, starting to remove bad mixins and loading main mod");

        try {
            BadMixinRemover.load();
            loadMainMod(pipeline);
        } catch (Exception e) {
            CITResewnNeoPatcherBootstrap.LOGGER.error("Exception on First CITResewnNeoPatcher early load", e);
            CITResewnNeoPatcherBootstrap.LOGGER.error("Mod files:");
            CITResewnNeoPatcherBootstrap.LOGGER.error("File name | Should be read | Readable");
            for (InspectedModFile inspectedModFile : inspectModFilesOnCrash()) {
                CITResewnNeoPatcherBootstrap.LOGGER.error(inspectedModFile.toString());
            }
            throw new RuntimeException(e);
        }
    }

    private static void loadMainMod(IDiscoveryPipeline pipeline) throws Exception {
        URL url = CITResewnNeoPatcherEarlyLoaderFirst.class.getResource("/META-INF/mod/citrnp-mod-file.jar");
        Path path = url != null ? Paths.get(url.toURI()) : null;
        if(path == null || !Files.exists(path)) {
            throw new IllegalStateException("CITResewnNeoPatcher JAR does not exist!");
        }
        CITResewnNeoPatcherBootstrap.LOGGER.info("Loading CITResewnNeoPatcher mod module");
        // Use JarContents so it doesn't detect that the primary JAR is already loaded and skip the path
        pipeline.addJarContent(JarContents.of(path), ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.WARN_ALWAYS);
    }

    private static List<InspectedModFile> inspectModFilesOnCrash() {
        File[] modFiles = FMLPaths.MODSDIR.get().toFile().listFiles();
        List<InspectedModFile> inspectedModFiles = new ArrayList<>();
        if (modFiles == null) {
            CITResewnNeoPatcherBootstrap.LOGGER.error("FMLPaths.MODSDIR.get().toFile().listFiles() returns null");
            return inspectedModFiles;
        }

        for (File modFile : modFiles) {
            if (modFile.isDirectory()) continue;

            String name = modFile.getName();
            boolean shouldBeRead = BadMixinRemover.shouldReadModFile(name);
            boolean readable;
            try (var fs = FileSystems.newFileSystem(modFile.toPath())) {
                readable = true;
            } catch (Exception e) {
                readable = false;
            }

            inspectedModFiles.add(new InspectedModFile(name, shouldBeRead, readable));
        }

        return inspectedModFiles;
    }

    record InspectedModFile(String name, boolean shouldBeRead, boolean readable) {
        @Override
        public String toString() {
            return String.format("%s: [%s] [%s]", name, shouldBeRead, readable);
        }
    }
}
