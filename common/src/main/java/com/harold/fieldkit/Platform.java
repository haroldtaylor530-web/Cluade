package com.harold.fieldkit;

import java.nio.file.Path;

/** What the loader (Forge or Fabric) provides. */
public interface Platform {
    String loader();

    String loaderVersion();

    int modCount();

    Path configDir();

    Path gameDir();

    /** F3 screen showing (the API for this differs between 1.20.1 and 1.21). */
    boolean debugScreenOpen();

    /** Singleplayer server's average milliseconds per tick, or -1. */
    float averageTickMs();

    /** Test only: create and open a new singleplayer world. */
    void createTestWorld(String name, long seed);
}
