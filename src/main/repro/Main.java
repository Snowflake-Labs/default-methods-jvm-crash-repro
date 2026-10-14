package repro;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Five threads run until the JVM exits:
 *
 * <ol>
 *   <li>Four loaders each define a fresh {@link Victim} in a new class loader, again and again.</li>
 *   <li>One redefiner swaps {@link A} between two bytecode versions ({@code return 1} and {@code return 2}).</li>
 * </ol>
 *
 * Those versions have the same method and different bodies, so each swap frees the old method.
 * A loader can still be reading that method while {@code Victim} is parsed, which crashes the JVM
 * in {@code DefaultMethods::generate_default_methods}.
 *
 * <p>Run with {@code java -jar repro.jar}.
 */
public final class Main {
  private static final String VICTIM = "repro.Victim";
  private static final Path VERSION_2 = Path.of("out/v2");
  private static final int LOADER_THREADS = 4;

  /** Set by {@link #agentmain} before {@link #main}. Required for {@code redefineClasses}. */
  private static volatile Instrumentation instrumentation;

  /** Called from the jar manifest ({@code Launcher-Agent-Class}) so redefine is allowed. */
  public static void agentmain(String args, Instrumentation instrumentation) {
    Main.instrumentation = instrumentation;
  }

  public static void main(String[] args) throws Exception {
    if (instrumentation == null) {
      throw new IllegalStateException("run with java -jar so the agent in the manifest is loaded");
    }

    // Version 1 is the copy inside this jar. Version 2 was compiled by run.sh into out/v2.
    ClassDefinition version1 = new ClassDefinition(A.class, classBytes("repro.A"));
    ClassDefinition version2 = new ClassDefinition(A.class, Files.readAllBytes(VERSION_2.resolve("repro/A.class")));
    byte[] victimBytes = classBytes(VICTIM);

    for (int i = 0; i < LOADER_THREADS; i++) {
      Thread.ofPlatform().name("loader-" + i).daemon().start(() -> defineVictims(victimBytes));
    }
    Thread.ofPlatform().name("redefiner").daemon().start(() -> redefine(version1, version2));
    Thread.sleep(60_000);
    System.out.println("no crash");
  }

  /** Parse Victim repeatedly. Each call uses a new loader, so the JVM parses the class again. */
  private static void defineVictims(byte[] victimBytes) {
    while (true) {
      try {
        Class.forName(VICTIM, false, new VictimLoader(victimBytes));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /** Replace A's method body, then put the previous body back. */
  private static void redefine(ClassDefinition version1, ClassDefinition version2) {
    boolean useVersion2 = true;
    while (true) {
      ClassDefinition next = useVersion2 ? version2 : version1;
      try {
        instrumentation.redefineClasses(next);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      useVersion2 = !useVersion2;
    }
  }

  private static byte[] classBytes(String className) throws IOException {
    String path = className.replace('.', '/') + ".class";
    try (InputStream in = Main.class.getClassLoader().getResourceAsStream(path)) {
      return in.readAllBytes();
    }
  }

  /**
   * Defines only {@code repro.Victim}. {@link A} is loaded by the parent, so every Victim
   * shares the single A that the redefiner is swapping.
   */
  private static final class VictimLoader extends ClassLoader {
    private final byte[] victimBytes;

    VictimLoader(byte[] victimBytes) {
      super(Main.class.getClassLoader());
      this.victimBytes = victimBytes;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name.equals(VICTIM)) {
        return defineClass(name, victimBytes, 0, victimBytes.length);
      }
      return super.loadClass(name, resolve);
    }
  }
}
