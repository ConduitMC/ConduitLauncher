package systems.conduit.launcher.json.download;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JsonLibraryInfo {

    @Getter private final String type;
    @Getter private final String groupId;
    @Getter private final String artifactId;
    @Getter private final String version;
    @Getter private final String url;

}
