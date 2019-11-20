package systems.conduit.launcher;

import org.apache.logging.log4j.LogManager;
import systems.conduit.launcher.json.download.JsonLibraryInfo;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LibraryProcessor {

    private static ArrayList<String> loadedArtifacts = new ArrayList<>();

    public static void downloadLibrary(String type, boolean firstLaunch, List<JsonLibraryInfo> libraries) {
        info(firstLaunch, "Loading " + type);
        List<String> loadedLibraries = new ArrayList<>();
        for (JsonLibraryInfo library : libraries) {
            if (loadedArtifacts.contains(library.getGroupId() + ":" + library.getArtifactId())) continue;
            File libraryPath = new File(Constants.LIBRARIES_PATH.toFile() + File.separator + getPath(library));
            try {
                Files.createDirectories(libraryPath.toPath());
                File jar = new File(libraryPath, getFileName(library));
                if (!jar.exists() && library.getType() != null) {
                    if (library.getType().trim().equalsIgnoreCase("maven")) {
                        info(firstLaunch, "Downloading " + type + ": " + library.getArtifactId());
                        MainStart.downloadFile(getUrl(library), jar);
                    } else if (!library.getType().trim().equalsIgnoreCase("minecraft")) {
                        info(firstLaunch, "Downloading " + type + ": " + library.getArtifactId());
                        MainStart.downloadFile(new URL(library.getUrl()), jar);
                    }
                }
                loadedLibraries.add(library.getArtifactId());
                loadedArtifacts.add(library.getGroupId() + ":" + library.getArtifactId());
                Agent.addClassPath(jar);
            } catch (Exception e) {
                error(firstLaunch, "Error loading " + type + ": " + library.getArtifactId());
                e.printStackTrace();
                System.exit(0);
            }

        }
        if (!loadedLibraries.isEmpty()) LogManager.getLogger(Constants.LOGGER_NAME).info("Loaded " + type + ": " + loadedLibraries);
    }

    private static URL getUrl(JsonLibraryInfo library) throws MalformedURLException {
        String repo = Constants.DEFAULT_REPO;
        if (library.getUrl() != null && !library.getUrl().trim().isEmpty()) repo = library.getUrl().trim();
        return new URL((repo.endsWith("/") ? repo : repo + "/") + getPath(library) + getFileName(library));
    }

    private static String getFileName(JsonLibraryInfo library) {
        return library.getArtifactId() + "-" + library.getVersion() + ".jar";
    }

    private static String getPath(JsonLibraryInfo library) {
        return library.getGroupId().replaceAll("\\.", "/") + "/" + library.getArtifactId() + "/" + library.getVersion() + "/";
    }

    private static void info(boolean firstLaunch, String message) {
        if (firstLaunch) {
            System.out.println(message);
        } else {
            LogManager.getLogger(Constants.LOGGER_NAME).info(message);
        }
    }

    private static void error(boolean firstLaunch, String message) {
        if (firstLaunch) {
            System.out.println(message);
        } else {
            LogManager.getLogger(Constants.LOGGER_NAME).fatal(message);
        }
    }
}
