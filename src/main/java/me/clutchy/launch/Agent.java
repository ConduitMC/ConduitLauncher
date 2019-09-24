package me.clutchy.launch;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

public class Agent {

    private static Instrumentation inst = null;

    // The JRE will call method before launching your main()
    public static void agentmain(final String a, final Instrumentation inst) {
        Agent.inst = inst;
    }

    static void addClassPath(File f) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        try {
            if (inst != null) {
                inst.appendToSystemClassLoaderSearch(new JarFile(f));
                return;
            }
            Method m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            m.setAccessible(true);
            m.invoke(cl, f.toURI().toURL());
        } catch (Throwable e) {
            System.out.println("Add to classpath error!");
            e.printStackTrace();
            System.exit(0);
        }
    }
}
