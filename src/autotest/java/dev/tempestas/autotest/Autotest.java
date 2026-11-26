package dev.tempestas.autotest;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Dev-only test mod: {@link ClientDriver} makes a world and walks the checklist; lines start with TPTEST. */
@Mod("tempestas_autotest")
public class Autotest {
    static final Logger LOG = LogUtils.getLogger();
    static final boolean ON = System.getProperty("tempestas.autotest") != null;
    static int fails;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            LOG.info("TPTEST PASS {} :: {}", name, detail);
        } else {
            fails++;
            LOG.info("TPTEST FAIL {} :: {}", name, detail);
        }
    }

    static void info(String name, String detail) {
        LOG.info("TPTEST INFO {} :: {}", name, detail);
    }
}
