package com.HiWord9.CITResewnNeoPatcher.bootstrap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.locating.ModFileLoadingException;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class BadMixinRemover {
    final static String FABRIC_MOD_JSON = "fabric.mod.json";
    final static String PATCHED_PREFIX = "patched_";
    final static String DISABLED_SUFFIX = ".disabled_by_" + CITResewnNeoPatcherBootstrap.MODID;
    final static String CITR_ID = "citresewn";
    final static Path MODS_DIR_PATH = FMLPaths.MODSDIR.get();
    final static String MIXINS_CONFIG_FILE_NAME = "citresewn-defaults.mixins.json";
    final static Path TEMP_DIR = MODS_DIR_PATH.resolve("." + CITResewnNeoPatcherBootstrap.MODID);
    final static Path ORIGINAL_MIXINS_CONFIG_COPY = TEMP_DIR.resolve("original_" + MIXINS_CONFIG_FILE_NAME);
    final static List<String> BAD_MIXIN_CONFIG_LINES = new ArrayList<>(List.of( // modifiable
            "    \"types.armor.ArmorFeatureRendererMixin\"," // full line
    ));
    final static List<String> BAD_MIXIN_CLASSES = new ArrayList<>(List.of( // modifiable
            "shcm/shsupercm/fabric/citresewn/defaults/mixin/types/armor/ArmorFeatureRendererMixin.class"
    ));

    final static String NOTIF_MESSAGE = String.format("[%s] Partly patching CITR file", CITResewnNeoPatcherBootstrap.MODID);

    static Path currentCitrPath;
    static Path originalCitrPath;
    static Path currentPatchedPath;
    static Path finalDestPatchedPath;
    static JsonObject fabricConfigJson;

    public static void load() throws Exception {
        StartupNotificationManager.addModMessage(NOTIF_MESSAGE);

        discoverCITR();
        prepareDirs();
        removeBadMixin();
    }

    public static void cleanUp() throws Exception {
        Files.delete(finalDestPatchedPath);
        Files.move(currentCitrPath, originalCitrPath);
    }

    private static void discoverCITR() throws Exception {
        ArrayList<CITRCandidate> originalCandidates = new ArrayList<>();
        ArrayList<CITRCandidate> patchedCandidates = new ArrayList<>();
        ArrayList<CITRCandidate> disabledCandidates = new ArrayList<>();

        File[] files = MODS_DIR_PATH.toFile().listFiles();

        assert files != null;
        for (File modFile : files) {
            if (modFile.isDirectory()) continue;

            JsonObject currentFabricConfig = getFabricConfig(modFile);
            if (currentFabricConfig == null) continue;

            if (!isCITR(getId(currentFabricConfig))) continue;

            String fileName = modFile.getName();
            CITRCandidate candidate = new CITRCandidate(modFile, currentFabricConfig);
            if (fileName.startsWith(PATCHED_PREFIX)) {
                patchedCandidates.add(candidate);
            } else if (fileName.endsWith(".jar")) {
                originalCandidates.add(candidate);
            } else if (fileName.endsWith(DISABLED_SUFFIX)) {
                disabledCandidates.add(candidate);
            }
        }

        // fixing possible problems most of which can appear on previously terminated load

        boolean goodEnding = false;

        if (originalCandidates.size() > 1) {
            CITResewnNeoPatcherBootstrap.LOGGER.error("Only one CITResewn Jar have to be in mods folder");
            throw new ModFileLoadingException("Multiple CITResewn Jars found");
        } else if (originalCandidates.isEmpty()) { // original empty
            CITResewnNeoPatcherBootstrap.LOGGER.warn("No CITResewn Jar found, trying to repair previous load if it's possible");
            if (disabledCandidates.size() > 1) {
                CITResewnNeoPatcherBootstrap.LOGGER.error("If no CITResewn Jar is located in mods folder, than only one Disabled CITResewn Jar have to be there");
                throw new ModFileLoadingException("Multiple disabled CITResewn Jar found");
            } else if (disabledCandidates.isEmpty()) {
                if (patchedCandidates.size() == 1) {
                    CITResewnNeoPatcherBootstrap.LOGGER.warn("No other CITResewn Jars found except of only one previously patched, trying to load it");

                    CITRCandidate patchedCandidate = patchedCandidates.getFirst();
                    String version = getVersion(patchedCandidate.fabricModJson());
                    if (!isCompatible(version)) {
                        CITResewnNeoPatcherBootstrap.LOGGER.warn(
                                "Previously patched CITResewn Jar version is not compatible, expected {}, but found {}",
                                CITResewnNeoPatcherBootstrap.CITR_VERSION_RANGE, version
                        );
                        throw new ModFileLoadingException("Incompatible patched CITResewn version");
                    }

                    goodEnding = true;
                    CITResewnNeoPatcherBootstrap.LOGGER.warn("Loading previously patched CITResewn Jar");
                } else if (patchedCandidates.size() > 1) {
                    goodEnding = true;
                    CITResewnNeoPatcherBootstrap.LOGGER.warn("No other CITResewn Jars found except of multiple previously patched ones, this will likely not load correctly but not throwing here");
                } // else if empty throwing at the end
            } else {
                goodEnding = true;
                CITResewnNeoPatcherBootstrap.LOGGER.info("Found previously disabled CITResewn Jar, trying to repair");

                patchedCandidates.forEach(candidate -> candidate.file().delete());

                CITRCandidate disabledCandidate = disabledCandidates.getFirst();
                File enabledFile = new File(
                        disabledCandidate.file().toPath().toString().replaceFirst(
                                (DISABLED_SUFFIX.replace(".", "\\.")) + "$",
                                ""
                        )
                );
                disabledCandidate.file().renameTo(enabledFile);

                populateFields(new CITRCandidate(enabledFile, disabledCandidate.fabricModJson()));
                CITResewnNeoPatcherBootstrap.LOGGER.info("Successfully loaded repaired disabled CITResewn Jar");
            }
        } else {
            CITRCandidate originalCandidate = originalCandidates.getFirst();
            String version = getVersion(originalCandidate.fabricModJson());
            if (!isCompatible(version)) {
                CITResewnNeoPatcherBootstrap.LOGGER.error(
                        "CITResewnNeoPatcher requires CITResewn version range {}, but found {}",
                        CITResewnNeoPatcherBootstrap.CITR_VERSION_RANGE, version
                );
                throw new ModFileLoadingException("Incompatible CITResewn version");
            }

            goodEnding = true;
            populateFields(originalCandidate);

            patchedCandidates.forEach(candidate -> candidate.file().delete());
            disabledCandidates.forEach(candidate -> candidate.file().delete());
        }

        if (!goodEnding) throw new ModFileLoadingException("Could not find any CITResewn Jar file in Mods dir");
    }

    private static void populateFields(CITRCandidate candidate) {
        fabricConfigJson = candidate.fabricModJson();
        originalCitrPath = candidate.file().toPath();
        currentCitrPath = originalCitrPath;
        currentPatchedPath = TEMP_DIR.resolve(PATCHED_PREFIX + currentCitrPath.getFileName());
        finalDestPatchedPath = MODS_DIR_PATH.resolve(currentPatchedPath.getFileName());
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

    private static void prepareDirs() throws IOException {
        if (currentPatchedPath.toFile().exists()) {
            currentPatchedPath.toFile().delete();
        }
        if (ORIGINAL_MIXINS_CONFIG_COPY.toFile().exists()) {
            ORIGINAL_MIXINS_CONFIG_COPY.toFile().delete();
        }
        Files.createDirectories(TEMP_DIR);
    }

    private static void removeBadMixin() throws Exception {
        Files.copy(currentCitrPath, currentPatchedPath);

        FileSystem patchedJar = FileSystems.newFileSystem(currentPatchedPath);

        String defaultsJarDest = getDefaultsJarDest(fabricConfigJson);

        assert defaultsJarDest != null;
        Path defaultsJarPath = patchedJar.getPath(defaultsJarDest);

        FileSystem defaultsJar = FileSystems.newFileSystem(defaultsJarPath);

        Path mixinsConfigPath = defaultsJar.getPath(MIXINS_CONFIG_FILE_NAME);
        if (!Files.exists(mixinsConfigPath)) throw new NoSuchFileException(MIXINS_CONFIG_FILE_NAME);

        patchMixinsConfig(mixinsConfigPath);
        deleteBadMixins(defaultsJar);

        defaultsJar.close();
        patchedJar.close();

        currentCitrPath.toFile().renameTo(
                new File((currentCitrPath = Path.of(currentCitrPath + DISABLED_SUFFIX)).toString())
        );

        Files.copy(currentPatchedPath, finalDestPatchedPath);

        currentPatchedPath.toFile().delete();
        TEMP_DIR.toFile().delete();
    }

    private static void deleteBadMixins(FileSystem defaultsJar) throws IOException {
        for (String badMixinClass : BAD_MIXIN_CLASSES) {
            Path badMixinClassPath = defaultsJar.getPath(badMixinClass);
            if (!Files.exists(badMixinClassPath)) throw new NoSuchFileException(badMixinClass);

            Files.delete(badMixinClassPath);
        }
    }

    private static void patchMixinsConfig(Path mixinsConfigPath) throws IOException {
        Files.copy(mixinsConfigPath, ORIGINAL_MIXINS_CONFIG_COPY);

        try (
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(Files.newInputStream(ORIGINAL_MIXINS_CONFIG_COPY))
                );
                BufferedWriter bw = new BufferedWriter(
                        new OutputStreamWriter(Files.newOutputStream(mixinsConfigPath))
                )
        ) {
            String line;
            while ((line = br.readLine()) != null) {
                if (BAD_MIXIN_CONFIG_LINES.contains(line)) continue;
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ORIGINAL_MIXINS_CONFIG_COPY.toFile().delete();
    }

    private static @Nullable String getDefaultsJarDest(JsonObject jsonObject) {
        String defaultsJarDest = null;
        for (JsonElement jarElement : jsonObject.getAsJsonArray("jars").asList()) {
            defaultsJarDest = jarElement.getAsJsonObject().get("file").getAsString();
            break;
        }
        return defaultsJarDest;
    }

    private static @Nullable String getId(JsonObject fabricModJson) {
        JsonElement idElement = fabricModJson.get("id");
        if (idElement == null) return null;

        return idElement.getAsString();
    }

    private static @Nullable String getVersion(JsonObject fabricModJson) {
        JsonElement idElement = fabricModJson.get("version");
        if (idElement == null) return null;

        return idElement.getAsString().replaceAll("[+].*$", "");
    }

    private static boolean isCITR(String id) {
        return id != null && id.equals(CITR_ID);
    }

    private static boolean isCompatible(String version) {
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
    
    protected record CITRCandidate(File file, JsonObject fabricModJson) {}
}