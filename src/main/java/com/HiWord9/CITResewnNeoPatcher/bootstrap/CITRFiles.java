package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class CITRFiles {
    final static String FABRIC_MOD_JSON = "fabric.mod.json";
    final static String JAR_SUFFIX = ".jar";
    final static String CITR_ID = "citresewn";
    public final static Path MODS_DIR_PATH = FMLPaths.MODSDIR.get();

    static List<CITRCandidate> findCITRCandidates() {
        List<CITRCandidate> candidates = new ArrayList<>();

        File[] files = MODS_DIR_PATH.toFile().listFiles();
        if (files == null) return candidates;

        for (File modFile : files) {
            if (modFile.isDirectory() || !modFile.getName().endsWith(JAR_SUFFIX)) continue;

            JsonObject fabricConfig = getCitrFabricConfig(modFile);
            if (fabricConfig != null) candidates.add(new CITRCandidate(modFile, fabricConfig));
        }
        return candidates;
    }

    // Reads a mod file's fabric.mod.json and returns it only if the file is the CITResewn mod, null otherwise.
    static @Nullable JsonObject getCitrFabricConfig(File modFile) {
        JsonObject config;
        try {
            config = getFabricConfig(modFile);
        } catch (Exception e) {
            CITResewnNeoPatcherBootstrap.LOGGER.error("Error while reading fabric.mod.json from {}, ignoring", modFile.getName(), e);
            return null;
        }
        if (config == null) return null;
        return isCITR(getId(config)) ? config : null;
    }

    private static @Nullable JsonObject getFabricConfig(File modFile) throws IOException {
        FileSystem mainJar = FileSystems.newFileSystem(modFile.toPath());
        Path currentPath = mainJar.getPath(FABRIC_MOD_JSON);

        if (!Files.exists(currentPath)) {
            // skip non-fabric mod
            mainJar.close();
            return null;
        }

        JsonObject currentFabricConfig = getJsonObject(currentPath);
        mainJar.close();
        return currentFabricConfig;
    }

    private static @Nullable String getId(JsonObject fabricModJson) {
        JsonElement idElement = fabricModJson.get("id");
        if (idElement == null) return null;

        return idElement.getAsString();
    }

    static @Nullable String getVersion(JsonObject fabricModJson) {
        JsonElement idElement = fabricModJson.get("version");
        if (idElement == null) return null;

        return idElement.getAsString().replaceAll("[+].*$", "");
    }

    private static boolean isCITR(String id) {
        return id != null && id.equals(CITR_ID);
    }

    static boolean isCompatible(String version) {
        if (version == null) return false;
        return CITResewnNeoPatcherBootstrap.CITR_VERSION_RANGE.containsVersion(new DefaultArtifactVersion(version));
    }

    private static JsonObject getJsonObject(Path fabricModJson) throws IOException {
        InputStream inputStream = Files.newInputStream(fabricModJson);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        JsonObject jsonObject = new Gson().fromJson(bufferedReader, JsonObject.class);

        inputStream.close();
        bufferedReader.close();
        return jsonObject;
    }

    record CITRCandidate(File file, JsonObject fabricModJson) {}
}
