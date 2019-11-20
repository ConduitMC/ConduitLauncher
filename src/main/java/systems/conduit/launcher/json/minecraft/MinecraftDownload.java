package systems.conduit.launcher.json.minecraft;

import lombok.Getter;

public class MinecraftDownload {

    @Getter private MinecraftVersionInfo server =  new MinecraftVersionInfo();
    private MinecraftVersionInfo server_mappings = new MinecraftVersionInfo();

    public MinecraftVersionInfo getServerMappings() {
        return server_mappings;
    }
}
