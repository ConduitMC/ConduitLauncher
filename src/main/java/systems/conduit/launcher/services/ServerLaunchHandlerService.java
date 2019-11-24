package systems.conduit.launcher.services;

import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ITransformingClassLoader;
import cpw.mods.modlauncher.api.ITransformingClassLoaderBuilder;
import org.spongepowered.asm.mixin.Mixins;
import systems.conduit.launcher.MainStart;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class ServerLaunchHandlerService implements ILaunchHandlerService {

    @Override
    public String name() {
        return "minecraft-server";
    }

    @Override
    public void configureTransformationClassLoader(final ITransformingClassLoaderBuilder builder) {
        // Add transformation paths
        MainStart.PATHS.forEach(builder::addTransformationPath);
    }

    @Override
    public Callable<Void> launchService(String[] args, ITransformingClassLoader launchClassLoader) {
        // Add mixins to configure
        MainStart.MIXINS.forEach(Mixins::addConfiguration);
        return () -> {
            final Class<?> mcClass = Class.forName("net.minecraft.server.MinecraftServer", true, launchClassLoader.getInstance());
            final Method mcClassMethod = mcClass.getMethod("main", String[].class);
            mcClassMethod.invoke(null, (Object) args);
            return null;
        };
    }

    @Override
    public Path[] getPaths() {
        return MainStart.PATHS.toArray(new Path[]{});
    }
}
