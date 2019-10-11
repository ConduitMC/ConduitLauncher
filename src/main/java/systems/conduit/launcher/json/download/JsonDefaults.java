package systems.conduit.launcher.json.download;

import java.util.ArrayList;
import java.util.List;

public class JsonDefaults {

    private List<JsonDownloadType> base = new ArrayList<>();
    private List<JsonDownloadType> mixin = new ArrayList<>();

    public List<JsonDownloadType> getBase() {
        return base;
    }

    public List<JsonDownloadType> getMixin() {
        return mixin;
    }
}
