package systems.conduit.launcher;

import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ITransformingClassLoader;
import cpw.mods.modlauncher.api.ITransformingClassLoaderBuilder;

import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class ServerLaunchHandlerService implements ILaunchHandlerService {

    public static final String LAUNCH_PROPERTY = "minecraft.server.jar";
    public static final String LAUNCH_PATH_STRING = System.getProperty(LAUNCH_PROPERTY);

    @Override
    public String name() {
        return "minecraft-server";
    }

    @Override
    public void configureTransformationClassLoader(final ITransformingClassLoaderBuilder builder) {
        if (LAUNCH_PATH_STRING == null) {
            throw new IllegalStateException("Missing " + LAUNCH_PROPERTY +" environment property!");
        }
        builder.addTransformationPath(FileSystems.getDefault().getPath(LAUNCH_PATH_STRING));
    }

    @Override
    public Callable<Void> launchService(String[] args, ITransformingClassLoader launchClassLoader) {
        return () -> {
            final Class<?> mcClass = Class.forName("net.minecraft.server.MinecraftServer", true, launchClassLoader.getInstance());
            final Method mcClassMethod = mcClass.getMethod("main", String[].class);
            mcClassMethod.invoke(null, (Object) args);
            return null;
        };
    }

    @Override
    public Path[] getPaths() {
        return new Path[] { FileSystems.getDefault().getPath(LAUNCH_PATH_STRING) };
    }
}
