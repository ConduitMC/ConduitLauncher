package me.clutchy.launch;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class MainStart {

    public static void main(String[] args) throws ClassNotFoundException {
        loadJars(new File(".libs"));
        String[] args2 = Stream.concat(Stream.of("--launchTarget", "minecraftserver"), Arrays.stream(args)).toArray(String[]::new);
        try {
            Class<?> cls = Class.forName("cpw.mods.modlauncher.Launcher", true, ClassLoader.getSystemClassLoader());
            Method method = cls.getMethod("main", String[].class);
            method.invoke(null, (Object) args2);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            System.out.println("Problems launching Minecraft! Closing...");
            e.printStackTrace();
            System.exit(0);
        }
    }

    private static void loadJars(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            Arrays.stream(Objects.requireNonNull(directory.listFiles())).filter(File::isFile).forEach(Agent::addClassPath);
        }
    }
}
