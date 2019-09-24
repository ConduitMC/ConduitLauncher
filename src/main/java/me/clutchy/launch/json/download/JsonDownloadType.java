package me.clutchy.launch.json.download;

public class JsonDownloadType {

    private String type;
    private String groupId;
    private String artifactId;
    private String version;
    private String url;

    public JsonDownloadType(String type, String groupId, String artifactId, String version, String url) {
        this.type = type;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }
}
