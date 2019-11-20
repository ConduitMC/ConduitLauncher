package systems.conduit.launcher.json.minecraft;

import lombok.Getter;

public class MinecraftRule {

    @Getter private String action = "";
    @Getter private MinecraftOS os = new MinecraftOS();

    protected boolean isMac() {
        return action.equals("allow") && os != null && os.getName().equals("osx");
    }
}
