package com.harold.corpsedecay.client;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

final class CorpseDecayClientLog {
    private static final Logger LOG = LogUtils.getLogger();

    private CorpseDecayClientLog() {
    }

    static void trace(Throwable t) {
        LOG.warn("[corpsedecay] trace", t);
    }

    static void warn(String msg) {
        LOG.warn("[corpsedecay] {}", msg);
    }
}
