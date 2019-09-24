package me.clutchy.launch;

import me.clutchy.launch.json.download.JsonDownloadType;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class LibraryProcessor {

    private static final String DEFAULT_REPO = "http://repo.maven.apache.org/maven2/";

    static List<String> downloadLibrary(String type, boolean firstLaunch, Path path, List<JsonDownloadType> libraries) {
        info(firstLaunch, "Loading " + type);
        List<String> loadedJars = new ArrayList<>();
        List<String> loadedLibraries = new ArrayList<>();
        for (JsonDownloadType library : libraries) {
            File libraryPath = new File(path.toFile() + File.separator + getPath(library));
            try {
                Files.createDirectories(libraryPath.toPath());
                File jar = new File(libraryPath, getFileName(library));
                if (!jar.exists()) {
                    if (library.getType() != null && library.getType().trim().equalsIgnoreCase("maven")) {
                        info(firstLaunch, "Downloading " + type + ": " + library.getArtifactId());
                        MainStart.downloadFile(getUrl(library), jar);
                    } else {
                        if (library.getType() == null || !library.getType().trim().equalsIgnoreCase("minecraft")) {
                            info(firstLaunch, "Downloading " + type + ": " + library.getArtifactId());
                            MainStart.downloadFile(new URL(library.getUrl()), jar);
                        }
                    }
                }
                loadedLibraries.add(library.getArtifactId());
                loadedJars.add(jar.toURI().toURL().toString());
                Agent.addClassPath(jar);
            } catch (Exception e) {
                error(firstLaunch, "Error loading " + type + ": " + library.getArtifactId());
                e.printStackTrace();
                System.exit(0);
            }

        }
        if (!loadedLibraries.isEmpty()) LogManager.getLogger("Launcher").info("Loaded " + type + ": " + loadedLibraries);
        return loadedJars;
    }

    private static URL getUrl(JsonDownloadType library) throws MalformedURLException {
        String repo = DEFAULT_REPO;
        if (library.getUrl() != null && !library.getUrl().trim().isEmpty()) repo = library.getUrl().trim();
        return new URL((repo.endsWith("/") ? repo : repo + "/") + getPath(library) + getFileName(library));
    }

    private static String getFileName(JsonDownloadType library) {
        return library.getArtifactId() + "-" + library.getVersion() + ".jar";
    }

    private static String getPath(JsonDownloadType library) {
        return library.getGroupId().replaceAll("\\.", "/") + "/" + library.getArtifactId() + "/" + library.getVersion() + "/";
    }

    private static void info(boolean firstLaunch, String message) {
        if (firstLaunch) {
            System.out.println(message);
        } else {
            LogManager.getLogger("Launcher").info(message);
        }
    }

    private static void error(boolean firstLaunch, String message) {
        if (firstLaunch) {
            System.out.println(message);
        } else {
            LogManager.getLogger("Launcher").fatal(message);
        }
    }
}
