package systems.conduit.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import systems.conduit.launcher.json.download.JsonDefaults;
import systems.conduit.launcher.json.download.JsonDownloadType;
import systems.conduit.launcher.json.manifest.JsonVersionManifest;
import systems.conduit.launcher.json.manifest.JsonVersionManifestType;
import systems.conduit.launcher.json.minecraft.JsonMinecraft;
import systems.conduit.launcher.json.mixins.JsonMixin;
import systems.conduit.launcher.json.mixins.JsonMixins;
import systems.conduit.launcher.json.version.JsonVersion;

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
        List<URL> libs = LibraryProcessor.downloadLibrary("logger libraries", true, Paths.get(".libs", "base"), Arrays.asList(
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
        // Start default classloader for legacylauncher
        Launch launch = new Launch();
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
        // Load all the libraries
        libs.forEach(Launch.classLoader::addURL);
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
                logger.info("Remapping Minecraft (This might take a bit!)");
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
        // Create the mixins folder
        File mixinsFolder = Paths.get(".mixins").toFile();
        if (!mixinsFolder.exists() && !mixinsFolder.mkdirs()) {
            logger.error("Failed to make .mixins directory.");
            return;
        }
        // Dev install if we can
        if (args.length >= 1) {
            if (Arrays.asList(args).contains("dev")) {
                logger.info("Dev mode started");
                // Make sure we have the correct directories
                if (!Paths.get(".minecraft", ".dev").toFile().exists()) {
                    logger.info("Creating dev directory and files");
                    Paths.get(".minecraft", ".dev").toFile().mkdirs();
                }
                // Create our files
                Path launchWrapperPomFile = Paths.get(".minecraft", ".dev").resolve("launchwrapper-dev.pom");
                Path minecraftPomFile = Paths.get(".minecraft", ".dev").resolve("minecraft-dev.pom");
                // Check if windows
                String baseInstallCommand = "mvn ";
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    baseInstallCommand = "cmd.exe /c " + baseInstallCommand;
                }
                // Save poms if does not exist
                // Launchwrapper pom saving
                if (!launchWrapperPomFile.toFile().exists()) {
                    try (InputStream inputStream = MainStart.class.getResourceAsStream("/launchwrapper-dev.pom")) {
                        Files.copy(inputStream, launchWrapperPomFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        logger.fatal("Error copying launchwrapper pom!");
                        e.printStackTrace();
                        System.exit(0);
                    }
                }
                // Minecraft pom saving
                if (!minecraftPomFile.toFile().exists()) {
                    try (InputStream inputStream = MainStart.class.getResourceAsStream("/minecraft-dev.pom")) {
                        Files.copy(inputStream, minecraftPomFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        logger.fatal("Error copying minecraft pom!");
                        e.printStackTrace();
                        System.exit(0);
                    }
                }
                logger.info("Replacing minecraft version in pom");
                // Replace minecraft version in pom
                try {
                    String content = new String(Files.readAllBytes(minecraftPomFile), StandardCharsets.UTF_8);
                    content = content.replaceAll("\\$\\{minecraft-version}", minecraftVersion);
                    Files.write(minecraftPomFile, content.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Install Launchwrapper to maven
                logger.info("Installing launchwrapper to maven");
                try {
                    String dFile = "-Dfile=" + Paths.get(".libs", "base", "io", "github", "lightwayup", "launchwrapper", "1.13").resolve("launchwrapper-1.13.jar").toString();
                    String pomFile = "-DpomFile=" +  launchWrapperPomFile.toString();
                    Process p = Runtime.getRuntime().exec(baseInstallCommand +"install:install-file " + dFile + " " + pomFile);
                    String line;
                    BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                    in.close();
                } catch (IOException e) {
                    logger.fatal("Error with maven install for launchwrapper!");
                    e.printStackTrace();
                    System.exit(0);
                }
                // Install Minecraft to maven
                logger.info("Installing minecraft to maven");
                try {
                    String dFile = "-Dfile=" + Paths.get(".minecraft").resolve(serverFinalJar).toString();
                    String pomFile = "-DpomFile=" +  minecraftPomFile.toString();
                    Process p = Runtime.getRuntime().exec(baseInstallCommand + " install:install-file " + dFile + " " + pomFile);
                    String line;
                    BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                    in.close();
                } catch (IOException e) {
                    logger.fatal("Error with maven install for launchwrapper!");
                    e.printStackTrace();
                    System.exit(0);
                }
                logger.info("Done with dev install!");
                logger.info("To start your server normally remove the dev argument!");
                System.exit(0);
                return;
            }
        }
        // Load Minecraft
        logger.info("Loading Minecraft remapped");
        File minecraftServer = Paths.get(".minecraft").resolve(serverFinalJar).toFile();
        try {
            Launch.classLoader.addURL(minecraftServer.toURI().toURL());
        } catch (MalformedURLException e) {
            e.printStackTrace();
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
        // Download Mixins
        if (!mixins.getMixins().isEmpty()) {
            for (JsonMixin mixin : mixins.getMixins()) {
                try {
                    File file = Paths.get(".mixins").resolve(mixin.getName() + ".jar").toFile();
                    if (!file.exists() && mixin.getUrl() != null && !mixin.getUrl().trim().isEmpty()) {
                        logger.info("Downloading mixin (" + mixin.getName() +")");
                        downloadFile(new URL(mixin.getUrl()), file);
                    }
                } catch (IOException e) {
                    logger.fatal("Error downloading mixin (" + mixin.getName() + ")!");
                    e.printStackTrace();
                    System.exit(0);
                }
            }
        }
        // Load Mixins
        File[] mixinFiles = mixinsFolder.listFiles();
        if (mixinFiles == null) return;
        for (File file : mixinFiles) {
            // Skip folders
            if (!file.isFile()) continue;
            // Make sure that it ends with .jar
            if (!file.getName().endsWith(".jar")) continue;
            // Since it is a file, and it ends with .jar, we can proceed with attempting to load it.
            try{
                Launch.classLoader.addURL(file.toURI().toURL());
            } catch (IOException e) {
                logger.fatal("Error loading mixin (" + file.getName() + ")!");
                e.printStackTrace();
                System.exit(0);
            }
            logger.info("Loaded mixin: " + file.getName());
        }
        // Start launchwrapper
        logger.info("Starting launchwrapper...");
        launch.launch(Stream.concat(Stream.of("--tweakClass", "systems.conduit.tweaker.MixinTweaker"), Arrays.stream(args)).toArray(String[]::new));
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
