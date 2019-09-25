package me.clutchy.launch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.clutchy.launch.json.mixins.JsonMixin;
import me.clutchy.launch.json.mixins.JsonMixins;
import me.clutchy.launch.json.download.JsonDefaults;
import me.clutchy.launch.json.download.JsonDownloadType;
import me.clutchy.launch.json.manifest.JsonVersionManifest;
import me.clutchy.launch.json.manifest.JsonVersionManifestType;
import me.clutchy.launch.json.minecraft.JsonMinecraft;
import me.clutchy.launch.json.version.JsonVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

public class MainStart {

    private static final String MANIFEST_ENDPOINT = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    public static void main(String[] args) {
        System.out.println("Starting launcher...");
        // Load logger libraries
        List<String> libs = LibraryProcessor.downloadLibrary("logger libraries", true, Paths.get(".libs", "base"), Arrays.asList(
                new JsonDownloadType("maven", "org.apache.logging.log4j", "log4j-api", "2.8.1", ""),
                new JsonDownloadType("maven", "org.apache.logging.log4j", "log4j-core", "2.8.1", "")
        ));
        Logger logger = LogManager.getLogger("Launcher");
        // Load json library
        libs.addAll(LibraryProcessor.downloadLibrary("json library", false, Paths.get(".libs", "base"), Collections.singletonList(
                new JsonDownloadType("maven", "com.google.code.gson", "gson", "2.8.0", "")
        )));
        // Load base libraries from json to class
        JsonDefaults defaults = new JsonDefaults();
        try (Reader reader = new InputStreamReader(MainStart.class.getResourceAsStream("/defaults.json"), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            defaults = gson.fromJson(reader, JsonDefaults.class);
        } catch (IOException e) {
            logger.fatal("Error reading base libraries json!");
            e.printStackTrace();
            System.exit(0);
        }
        // Download all the base libraries
        libs.addAll(LibraryProcessor.downloadLibrary("base libraries", false, Paths.get(".libs", "base"), defaults.getBase()));
        // Download all the mixin libraries
        libs.addAll(LibraryProcessor.downloadLibrary("mixin libraries", false, Paths.get(".libs", "mixin"), defaults.getMixin()));
        // Add Minecraft json if does not exist
        Path minecraftJsonFile = Paths.get("minecraft.json");
        if (!minecraftJsonFile.toFile().exists()) {
            try (InputStream inputStream = MainStart.class.getResourceAsStream("/minecraft.json")) {
                Files.copy(inputStream, minecraftJsonFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.fatal("Error copying Minecraft json!");
                e.printStackTrace();
                System.exit(0);
            }
        }
        // Load Minecraft from json to class
        JsonMinecraft minecraft = new JsonMinecraft();
        try (BufferedReader reader = new BufferedReader(new FileReader("minecraft.json"))) {
            Gson gson = new GsonBuilder().create();
            minecraft = gson.fromJson(reader, JsonMinecraft.class);
        } catch (IOException e) {
            logger.fatal("Error reading Minecraft libraries json!");
            e.printStackTrace();
            System.exit(0);
        }
        // Download all the Minecraft libraries
        libs.addAll(LibraryProcessor.downloadLibrary("Minecraft libraries", false, Paths.get(".minecraft", ".libs"), minecraft.getMinecraft()));
        // Minecraft info
        String minecraftVersion = minecraft.getVersion();
        String serverJar = "server-" + minecraftVersion + ".jar";
        String serverFinalJar = "server-" + minecraftVersion + "-remapped.jar";
        String serverMappings = "mappings.txt";
        String serverMappingsConverted = "mappings-converted.txt";
        // Make sure we have the correct directories
        Paths.get(".minecraft").toFile().mkdirs();
        // Download Minecraft and patch if we don't have the file
        if (!Paths.get(".minecraft").resolve(serverFinalJar).toFile().exists()) {
            String versionUrl = "";
            // Read manifest and get version url
            try (InputStreamReader reader = new InputStreamReader(new URL(MANIFEST_ENDPOINT).openStream())) {
                JsonVersionManifest manifest = new Gson().fromJson(reader, JsonVersionManifest.class);
                Optional<JsonVersionManifestType> version = getVersion(manifest, minecraftVersion);
                if (version.isPresent()) {
                    versionUrl = version.get().getUrl();
                } else {
                    logger.fatal("Unable to get version info for Minecraft (" + minecraftVersion + ")!");
                    System.exit(0);
                }
            } catch (IOException e) {
                logger.fatal("Error reading Minecraft manifest url!");
                e.printStackTrace();
                System.exit(0);
            }
            // Read version json and get server info
            if (!versionUrl.isEmpty()) {
                String serverUrl = "";
                String serverMappingsUrl = "";
                try (InputStreamReader reader = new InputStreamReader(new URL(versionUrl).openStream())) {
                    JsonVersion version = new Gson().fromJson(reader, JsonVersion.class);
                    serverUrl = version.getDownloads().getServer().getUrl();
                    serverMappingsUrl = version.getDownloads().getServerMappings().getUrl();
                } catch (IOException e) {
                    logger.fatal("Error reading Minecraft version url!");
                    e.printStackTrace();
                    System.exit(0);
                }
                // Download server
                if (!serverUrl.isEmpty()) {
                    try {
                        logger.info("Downloading Minecraft server (" + minecraftVersion + ")");
                        downloadFile(new URL(serverUrl), Paths.get(".minecraft").resolve(serverJar).toFile());
                    } catch (IOException e) {
                        logger.fatal("Error creating server url!");
                        e.printStackTrace();
                        System.exit(0);
                    }
                } else {
                    logger.fatal("Error reading Minecraft server url!");
                    System.exit(0);
                }
                // Cleanup Minecraft
                logger.info("Cleaning up Minecraft");
                deleteMinecraftTrash(Paths.get(".minecraft").resolve(serverJar).toFile());
                logger.info("Cleaned up Minecraft");
                // Download server mappings
                if (!serverMappingsUrl.isEmpty()) {
                    try {
                        logger.info("Downloading server mappings");
                        downloadFile(new URL(serverMappingsUrl), Paths.get(".minecraft").resolve(serverMappings).toFile());
                    } catch (IOException e) {
                        logger.fatal("Error creating server mappings url!");
                        e.printStackTrace();
                        System.exit(0);
                    }
                } else {
                    logger.fatal("Error reading Minecraft server mappings url!");
                    System.exit(0);
                }
                // Convert Minecraft mappings
                logger.info("Converting Minecraft mappings");
                Mojang2Tsrg m2t = new Mojang2Tsrg();
                File mappingsFile = Paths.get(".minecraft").resolve(serverMappings).toFile();
                File mappingsConvertedFile = Paths.get(".minecraft").resolve(serverMappingsConverted).toFile();
                try {
                    m2t.loadClasses(mappingsFile);
                    m2t.writeTsrg(mappingsFile, mappingsConvertedFile);
                } catch (IOException e) {
                    logger.fatal("Error converting Minecraft server mappings!");
                    e.printStackTrace();
                    System.exit(0);
                }
                mappingsFile.delete();
                // Remapping Minecraft
                logger.info("Remapping Minecraft (This make take a bit!)");
                String[] specialSourceArgs = Stream.of(
                        "--in-jar", Paths.get(".minecraft").resolve(serverJar).toFile().getAbsolutePath(),
                        "--out-jar", Paths.get(".minecraft").resolve(serverFinalJar).toFile().getAbsolutePath(),
                        "--srg-in", Paths.get(".minecraft").resolve(serverMappingsConverted).toFile().getAbsolutePath(),
                        "--quiet"
                ).toArray(String[]::new);
                try {
                    Class<?> cls = Class.forName("net.md_5.specialsource.SpecialSource", true, ClassLoader.getSystemClassLoader());
                    Method method = cls.getMethod("main", String[].class);
                    method.invoke(null, (Object) specialSourceArgs);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
                    logger.fatal("Error remapping Minecraft!");
                    e.printStackTrace();
                    System.exit(0);
                }
                Paths.get(".minecraft").resolve(serverJar).toFile().delete();
                mappingsConvertedFile.delete();
                logger.info("Remapped Minecraft");
            } else {
                logger.fatal("Unable to get version url for Minecraft (" + minecraftVersion + ")!");
                System.exit(0);
            }
        }
        // Load Minecraft
        logger.info("Loading Minecraft remapped");
        File minecraftServer = Paths.get(".minecraft").resolve(serverFinalJar).toFile();
        Agent.addClassPath(minecraftServer);
        try {
            libs.add(minecraftServer.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            logger.fatal("Unable to load Minecraft server!");
            System.exit(0);
        }
        logger.info("Loaded Minecraft remapped");
        // Load mixins json
        JsonMixins mixins = new JsonMixins();
        try (Reader reader = new InputStreamReader(MainStart.class.getResourceAsStream("/mixins.json"), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            mixins = gson.fromJson(reader, JsonMixins.class);
        } catch (IOException e) {
            logger.fatal("Error reading mixins json!");
            e.printStackTrace();
            System.exit(0);
        }
        // Make sure we have the correct directories
        Paths.get(".mixins").toFile().mkdirs();
        // Download and load Mixins
        if (!mixins.getMixins().isEmpty()) {
            for (JsonMixin mixin : mixins.getMixins()) {
                try {
                    File file = Paths.get(".mixins").resolve(mixin.getName() + ".jar").toFile();
                    if (!file.exists()) {
                        logger.info("Downloading Minecraft mixin (" + mixin.getName() +")");
                        downloadFile(new URL(mixin.getUrl()), file);
                    }
                    logger.info("Loading Minecraft mixin (" + mixin.getName() +")");
                    Agent.addClassPath(file);
                    try {
                        libs.add(file.toURI().toURL().toString());
                    } catch (MalformedURLException e) {
                        logger.fatal("Unable to load Minecraft mixin (" + mixin.getName() + ")!");
                        System.exit(0);
                    }
                    logger.info("Loaded Minecraft mixin (" + mixin.getName() +")");
                } catch (IOException e) {
                    logger.fatal("Error downloading mixin(" + mixin.getName() + ")!");
                    e.printStackTrace();
                    System.exit(0);
                }
            }
        }
        // Start launchwrapper
        logger.info("Starting launchwrapper...");
        args = Stream.concat(Stream.of(libs.toString(), "--tweakClass", "me.clutchy.tweaker.MixinTweaker"), Arrays.stream(args)).toArray(String[]::new);
        try {
            Class<?> cls = Class.forName("net.minecraft.launchwrapper.Launch", true, ClassLoader.getSystemClassLoader());
            Method method = cls.getMethod("main", String[].class);
            method.invoke(null, (Object) args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            logger.fatal("Problems launching Minecraft! Closing...");
            e.printStackTrace();
            System.exit(0);
        }
    }

    private static Optional<JsonVersionManifestType> getVersion(JsonVersionManifest manifest, String version) {
        return manifest.getVersions().stream().filter(type -> type.getId().equals(version)).findFirst();
    }

    private static void deleteMinecraftTrash(File file) {
        Map<String, String> zipProperties = new HashMap<>();
        zipProperties.put("create", "false");
        try (FileSystem zipFS = FileSystems.newFileSystem(URI.create("jar:" + file.toURI().toString()), zipProperties)) {
            Path[] allTheTrash = new Path[] {
                    zipFS.getPath("com"), zipFS.getPath("io"), zipFS.getPath("it"),
                    zipFS.getPath("javax"), zipFS.getPath("joptsimple"), zipFS.getPath("org")
            };
            for (Path trash : allTheTrash) {
                delete(trash);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void delete(Path directory) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    static void downloadFile(URL url, File location) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            Files.copy(inputStream, location.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
