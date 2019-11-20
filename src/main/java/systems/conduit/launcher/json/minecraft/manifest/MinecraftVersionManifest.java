package systems.conduit.launcher.json.minecraft.manifest;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class MinecraftVersionManifest {

    @Getter private List<MinecraftVersionManifestType> versions = new ArrayList<>();

}
