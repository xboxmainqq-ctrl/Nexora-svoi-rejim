package ru.nexora.survival;

import cn.nukkit.utils.Config;

import java.io.File;

/** A YAML file that is written to disk only when something changed. */
final class Store {
    final Config cfg;
    private boolean dirty;

    Store(File file) {
        this.cfg = new Config(file, Config.YAML);
    }

    void touch() {
        dirty = true;
    }

    void flush() {
        if (!dirty) return;
        cfg.save();
        dirty = false;
    }
}
