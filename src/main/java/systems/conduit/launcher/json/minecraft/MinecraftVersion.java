package systems.conduit.launcher.json.minecraft;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class MinecraftVersion {

    @Getter private MinecraftDownload downloads = new MinecraftDownload();
    @Getter private List<MinecraftLibrary> libraries = new ArrayList<>();

}
