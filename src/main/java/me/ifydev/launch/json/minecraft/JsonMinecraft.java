package me.ifydev.launch.json.minecraft;

import me.ifydev.launch.json.download.JsonDownloadType;

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
