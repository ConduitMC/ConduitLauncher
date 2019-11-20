package systems.conduit.launcher.json.minecraft;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class MinecraftLibrary {

    @Getter private String name = "";
    @Getter private List<MinecraftRule> rules = new ArrayList<>();

    public boolean isMac() {
        for (MinecraftRule rule : rules) {
            if (rule.isMac()) return true;
        }
        return false;
    }
}
