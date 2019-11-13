package systems.conduit.launcher.services;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import systems.conduit.launcher.MainStart;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MixinTransformationService implements ITransformationService {

    @Override
    public void initialize(IEnvironment environment) {
    }

    @Override
    public void beginScanning(final IEnvironment environment) {
    }

    @Override
    public List<Map.Entry<String, Path>> runScan(final IEnvironment environment) {
        return MainStart.MIXINS;
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public String name() {
        return "conduit";
    }

    @Override
    public List<ITransformer> transformers() {
        return new ArrayList<>();
    }
}
