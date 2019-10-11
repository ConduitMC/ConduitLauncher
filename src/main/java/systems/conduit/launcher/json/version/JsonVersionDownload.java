package systems.conduit.launcher.json.version;

public class JsonVersionDownload {

    private JsonVersionInfo server =  new JsonVersionInfo();
    private JsonVersionInfo server_mappings = new JsonVersionInfo();

    public JsonVersionInfo getServer() {
        return server;
    }

    public JsonVersionInfo getServerMappings() {
        return server_mappings;
    }
}
