package systems.conduit.launcher;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {

    // Default version. Will get changed by the launcher!
    public static String MINECRAFT_VERSION = "1.14.4";

    public static String LOGGER_NAME = "Launcher";

    public static final Path MINECRAFT_JSON_PATH = Paths.get("minecraft.json");

    public static final Path LIBRARIES_PATH = Paths.get(".libs");

    public static final Path MINECRAFT_PATH = Paths.get(".minecraft");
    public static Path SERVER_JAR_PATH;
    public static Path SERVER_MAPPED_JAR_PATH;
    public static Path SERVER_MAPPINGS_PATH;
    public static Path SERVER_MAPPINGS_CONVERTED_PATH;

    public static final Path DEV_PATH = Paths.get(".minecraft", ".dev");
    public static final Path BUILD_GRADLE_PATH = DEV_PATH.resolve("minecraft").resolve("build.gradle");

    public static final Path MIXINS_PATH = Paths.get(".mixins");

    public static final String VERSION_MANIFEST_ENDPOINT = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public static final String DEFAULT_REPO = "http://repo.maven.apache.org/maven2/";

    public static void setMinecraftVersion() {
        SERVER_JAR_PATH = MINECRAFT_PATH.resolve("server-" + MINECRAFT_VERSION + ".jar");
        SERVER_MAPPED_JAR_PATH = MINECRAFT_PATH.resolve("server-" + MINECRAFT_VERSION + "-remapped.jar");
        SERVER_MAPPINGS_PATH = MINECRAFT_PATH.resolve("server-" + MINECRAFT_VERSION + "-mappings.txt");
        SERVER_MAPPINGS_CONVERTED_PATH =  MINECRAFT_PATH.resolve("server-" + MINECRAFT_VERSION + "-mappings-converted.txt");
    }
}
