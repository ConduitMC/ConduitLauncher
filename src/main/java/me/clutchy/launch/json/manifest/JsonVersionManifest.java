package me.clutchy.launch.json.manifest;

import java.util.ArrayList;
import java.util.List;

public class JsonVersionManifest {

    private List<JsonVersionManifestType> versions = new ArrayList<>();

    public List<JsonVersionManifestType> getVersions() {
        return versions;
    }
}
