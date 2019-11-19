package systems.conduit.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.modlauncher.Launcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import systems.conduit.launcher.json.download.JsonDownloadType;
import systems.conduit.launcher.json.download.JsonLibraries;
import systems.conduit.launcher.json.manifest.JsonVersionManifest;
import systems.conduit.launcher.json.manifest.JsonVersionManifestType;
import systems.conduit.launcher.json.minecraft.JsonMinecraft;
import systems.conduit.launcher.json.mixins.JsonMixin;
import systems.conduit.launcher.json.mixins.JsonMixins;
import systems.conduit.launcher.json.version.JsonVersion;
import us.tedstar.mojang2tsrg.Mojang2Tsrg;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public class MainStart {

    public static final List<Map.Entry<String, Path>> MIXINS = new ArrayList<>();

    public static void main(String[] args) {
        System.setProperty("http.agent", "Mozilla/5.0");
        //System.setProperty("mixin.debug", "true");
        System.out.println("Starting launcher...");
        // Load logger libraries
        LibraryProcessor.downloadLibrary("logger libraries", true, Arrays.asList(
                new JsonDownloadType("maven", "org.apache.logging.log4j", "log4j-api", "2.8.1", ""),
                new JsonDownloadType("maven", "org.apache.logging.log4j", "log4j-core", "2.8.1", "")
        ));
        Logger logger = LogManager.getLogger(Constants.LOGGER_NAME);
        // Load json library
        LibraryProcessor.downloadLibrary("json library", false, Collections.singletonList(
                new JsonDownloadType("maven", "com.google.code.gson", "gson", "2.8.0", "")
        ));
        // Load default libraries from json to class
        JsonLibraries defaults = new JsonLibraries();
        try (Reader reader = new InputStreamReader(MainStart.class.getResourceAsStream("/defaults.json"), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            defaults = gson.fromJson(reader, JsonLibraries.class);
        } catch (IOException e) {
            logger.fatal("Error reading default libraries json");
            e.printStackTrace();
            System.exit(0);
        }
        // Download all the default libraries
        LibraryProcessor.downloadLibrary("default libraries", false, defaults.getLibs());
        // Add Minecraft json if does not exist
        if (!Constants.MINECRAFT_JSON_PATH.toFile().exists()) {
            try (InputStream inputStream = MainStart.class.getResourceAsStream("/minecraft.json")) {
                Files.copy(inputStream, Constants.MINECRAFT_JSON_PATH, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.fatal("Error copying Minecraft json");
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
            logger.fatal("Error reading Minecraft libraries json");
            e.printStackTrace();
            System.exit(0);
        }
        // Download all the Minecraft libraries
        LibraryProcessor.downloadLibrary("Minecraft libraries", false, minecraft.getMinecraft());
        // Minecraft info
        Constants.MINECRAFT_VERSION = minecraft.getVersion();
        Constants.setMinecraftVersion();
        // Make sure we have the correct directories
        Constants.MINECRAFT_PATH.toFile().mkdirs();
        // Download Minecraft and patch if we don't have the file
        if (!Constants.SERVER_MAPPED_JAR_PATH.toFile().exists()) {
            String versionUrl = "";
            // Read manifest and get version url
            try (InputStreamReader reader = new InputStreamReader(new URL(Constants.VERSION_MANIFEST_ENDPOINT).openStream())) {
                JsonVersionManifest manifest = new Gson().fromJson(reader, JsonVersionManifest.class);
                Optional<JsonVersionManifestType> version = getVersion(manifest, Constants.MINECRAFT_VERSION);
                if (version.isPresent()) {
                    versionUrl = version.get().getUrl();
                } else {
                    logger.fatal("Unable to get version info for Minecraft (" + Constants.MINECRAFT_VERSION + ")");
                    System.exit(0);
                }
            } catch (IOException e) {
                logger.fatal("Error reading Minecraft manifest url");
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
                    logger.fatal("Error reading Minecraft version url");
                    e.printStackTrace();
                    System.exit(0);
                }
                // Download server
                if (!serverUrl.isEmpty()) {
                    try {
                        logger.info("Downloading Minecraft server (" + Constants.MINECRAFT_VERSION + ")");
                        downloadFile(new URL(serverUrl), Constants.SERVER_JAR_PATH.toFile());
                    } catch (IOException e) {
                        logger.fatal("Error creating server url");
                        e.printStackTrace();
                        System.exit(0);
                    }
                } else {
                    logger.fatal("Error reading Minecraft server url");
                    System.exit(0);
                }
                // Cleanup Minecraft
                logger.info("Cleaning up Minecraft");
                deleteMinecraftTrash(Constants.SERVER_JAR_PATH.toFile());
                logger.info("Cleaned up Minecraft");
                // Download server mappings
                if (!serverMappingsUrl.isEmpty()) {
                    try {
                        logger.info("Downloading server mappings");
                        downloadFile(new URL(serverMappingsUrl), Constants.SERVER_MAPPINGS_PATH.toFile());
                    } catch (IOException e) {
                        logger.fatal("Error creating server mappings url");
                        e.printStackTrace();
                        System.exit(0);
                    }
                } else {
                    logger.fatal("Error reading Minecraft server mappings url");
                    System.exit(0);
                }
                // Convert Minecraft mappings
                logger.info("Converting Minecraft mappings");
                Mojang2Tsrg m2t = new Mojang2Tsrg();
                try {
                    m2t.loadClasses(Constants.SERVER_MAPPINGS_PATH.toFile());
                    m2t.writeTsrg(Constants.SERVER_MAPPINGS_PATH.toFile(), Constants.SERVER_MAPPINGS_CONVERTED_PATH.toFile());
                } catch (IOException e) {
                    logger.fatal("Error converting Minecraft server mappings");
                    e.printStackTrace();
                    System.exit(0);
                }
                Constants.SERVER_MAPPINGS_PATH.toFile().delete();
                // Remapping Minecraft
                logger.info("Remapping Minecraft (This might take a bit)");
                String[] specialSourceArgs = Stream.of(
                        "--in-jar", Constants.SERVER_JAR_PATH.toFile().getAbsolutePath(),
                        "--out-jar", Constants.SERVER_MAPPED_JAR_PATH.toFile().getAbsolutePath(),
                        "--srg-in", Constants.SERVER_MAPPINGS_CONVERTED_PATH.toFile().getAbsolutePath(),
                        "--quiet"
                ).toArray(String[]::new);
                try {
                    Class<?> cls = Class.forName("net.md_5.specialsource.SpecialSource", true, ClassLoader.getSystemClassLoader());
                    Method method = cls.getMethod("main", String[].class);
                    method.invoke(null, (Object) specialSourceArgs);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
                    logger.fatal("Error remapping Minecraft");
                    e.printStackTrace();
                    System.exit(0);
                }
                Constants.SERVER_JAR_PATH.toFile().delete();
                Constants.SERVER_MAPPINGS_CONVERTED_PATH.toFile().delete();
                logger.info("Remapped Minecraft");
            } else {
                logger.fatal("Unable to get version url for Minecraft (" + Constants.MINECRAFT_VERSION + ")");
                System.exit(0);
            }
        }
        // Dev install if we can
        if (args.length >= 1) {
            if (Arrays.asList(args).contains("dev")) {
                logger.info("Dev mode started");
                // Create base command
                String platform = System.getProperty("os.name").toLowerCase();
                String baseInstallCommand = "./gradlew ";
                if (platform.contains("win")) baseInstallCommand = "cmd.exe /c gradlew.bat ";
                else if (platform.contains("mac")) baseInstallCommand = "/bin/sh -c ./gradlew ";
                // Extract dev folder
                if (Constants.DEV_PATH.toFile().exists()) {
                    // Make sure we have the correct directories
                    logger.info("Deleting current dev directory");
                    deleteFolder(Constants.DEV_PATH.toFile());
                }
                try {
                    // Make sure we have the correct directories
                    logger.info("Copying dev directory");
                    copyFromJar(File.separator + ".dev", Constants.DEV_PATH.toAbsolutePath());
                } catch (URISyntaxException | IOException e) {
                    e.printStackTrace();
                }
                // Replace minecraft version and jar location in pom
                logger.info("Replacing minecraft version and jar in gradle.build");
                try {
                    String content = new String(Files.readAllBytes(Constants.BUILD_GRADLE_PATH), StandardCharsets.UTF_8);
                    content = content.replace("minecraft-version", Constants.MINECRAFT_VERSION);
                    content = content.replace("jar-location", Constants.SERVER_MAPPED_JAR_PATH.toAbsolutePath().toString().replaceAll("\\\\", "\\\\\\\\"));
                    Files.write(Constants.BUILD_GRADLE_PATH, content.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Install Minecraft to maven
                logger.info("Installing minecraft");
                try {
                    Process p = Runtime.getRuntime().exec(baseInstallCommand + "install", null, Constants.SERVER_DEV_PATH.toFile());
                    String line;
                    BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    while ((line = input.readLine()) != null) {
                        System.out.println(line);
                    }
                    input.close();
                } catch (IOException e) {
                    logger.fatal("Error with install for minecraft");
                    e.printStackTrace();
                    System.exit(0);
                }
                logger.info("Done with dev install");
                logger.info("To start your server normally remove the dev argument");
                System.exit(0);
            }
        }
        // Create the mixins folder
        if (!Constants.MIXINS_PATH.toFile().exists() && !Constants.MIXINS_PATH.toFile().mkdirs()) {
            logger.fatal("Failed to make .mixins directory");
            System.exit(0);
        }
        // Load Minecraft
        logger.info("Loading Minecraft remapped");
        Agent.addClassPath(Constants.SERVER_MAPPED_JAR_PATH.toFile());
        logger.info("Loaded Minecraft remapped");
        // Load mixins json
        JsonMixins mixins = new JsonMixins();
        try (Reader reader = new InputStreamReader(MainStart.class.getResourceAsStream("/mixins.json"), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().create();
            mixins = gson.fromJson(reader, JsonMixins.class);
        } catch (IOException e) {
            logger.fatal("Error reading mixins json");
            e.printStackTrace();
            System.exit(0);
        }
        // Download Mixins
        if (!mixins.getMixins().isEmpty()) {
            for (JsonMixin mixin : mixins.getMixins()) {
                try {
                    File file = Constants.MIXINS_PATH.resolve(mixin.getName() + ".jar").toFile();
                    if (!file.exists() && mixin.getUrl() != null && !mixin.getUrl().trim().isEmpty()) {
                        logger.info("Downloading mixin (" + mixin.getName() +")");
                        downloadFile(new URL(mixin.getUrl()), file);
                    }
                } catch (IOException e) {
                    logger.fatal("Error downloading mixin (" + mixin.getName() + ")");
                    e.printStackTrace();
                    System.exit(0);
                }
            }
        }
        // Load Mixins
        File[] mixinFiles = Constants.MIXINS_PATH.toFile().listFiles();
        if (mixinFiles != null) {
            for (File file : mixinFiles) {
                // Skip folders
                if (!file.isFile()) continue;
                // Make sure that it ends with .jar
                if (!file.getName().endsWith(".jar")) continue;
                // Since it is a file, and it ends with .jar, we can proceed with attempting to load it.
                String properFileName = file.getName().substring(0, file.getName().length() - 4);
                try {
                    // Get jar file
                    JarFile jarFile = new JarFile(file);
                    // Load libraries from json
                    ZipEntry libZip = jarFile.getEntry("libraries.json");
                    if (libZip != null) {
                        logger.info("Found libraries.json: " + properFileName);
                        try (Reader reader = new InputStreamReader(jarFile.getInputStream(libZip))) {
                            Gson gson = new GsonBuilder().create();
                            JsonLibraries libraries = gson.fromJson(reader, JsonLibraries.class);
                            logger.info("Loading libraries.json: " + properFileName);
                            LibraryProcessor.downloadLibrary(properFileName + " libraries", false, libraries.getLibs());
                        }
                    }
                    // Find all mixins for a jar.
                    List<String> mixinsJson = findMixinEntry(jarFile);
                    if (!mixinsJson.isEmpty()) {
                        for (String mixin : mixinsJson) {
                            MIXINS.add(new AbstractMap.SimpleImmutableEntry<>(mixin, file.toPath()));
                        }
                    }
                    // Add to class loader
                    Agent.addClassPath(file);
                } catch (IOException e) {
                    logger.fatal("Error loading mixin (" + properFileName + ")");
                    e.printStackTrace();
                    System.exit(0);
                }
                logger.info("Loaded mixin: " + properFileName);
            }
        }
        // Start modlauncher
        logger.info("Starting modlauncher...");
        Launcher.main(Stream.concat(Stream.of("--launchTarget", "minecraft-server"), Arrays.stream(args)).toArray(String[]::new));
    }

    private static List<String> findMixinEntry(JarFile file) {
        List<String> mixins = new ArrayList<>();
        for (final Enumeration<? extends ZipEntry> e = file.entries(); e.hasMoreElements();) {
            final ZipEntry ze = e.nextElement();
            if (!ze.isDirectory()) {
                final String name = ze.getName();
                if (name.startsWith("mixins.") && name.endsWith(".json")) {
                    mixins.add(name);
                }
            }
        }
        return mixins;
    }

    public static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if(files != null) {
            for(File f: files) {
                if(f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    private static void copyFromJar(String source, final Path target) throws URISyntaxException, IOException {
        URI resource = MainStart.class.getResource("").toURI();
        FileSystem fileSystem = FileSystems.newFileSystem(resource, Collections.<String, String>emptyMap());
        final Path jarPath = fileSystem.getPath(source);
        Files.walkFileTree(jarPath, new SimpleFileVisitor<Path>() {
            private Path currentTarget;
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                currentTarget = target.resolve(jarPath.relativize(dir).toString());
                Files.createDirectories(currentTarget);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(jarPath.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
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
