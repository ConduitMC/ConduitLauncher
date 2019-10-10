package systems.conduit.launcher.json.minecraft;

import systems.conduit.launcher.json.download.JsonDownloadType;

import java.util.ArrayList;
import java.util.List;

public class JsonMinecraft {

    private String version = "";
    private List<JsonDownloadType> minecraft = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public List<JsonDownloadType> getMinecraft() {
        return minecraft;
    }
}
