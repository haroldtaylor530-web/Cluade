package com.harold.hbmnukeshells;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/hbmnukeshells-common.toml: which HBM bomb each nuclear shell goes off as. */
public final class ShellConfig {
    public static final ForgeConfigSpec SPEC;
    /** Shell entity id → the bomb it becomes. */
    public static final Map<String, ForgeConfigSpec.EnumValue<Bomb>> SHELLS = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Which HBM bomb each nuclear shell detonates as.",
                "Choices: NATIVE (the shell's own explosion), LITTLE_BOY, GADGET, FAT_MAN, IVY_MIKE, TSAR_BOMBA, N2,",
                "MINI_NUKE_LOW, MINI_NUKE, MINI_NUKE_HIGH. Blast sizes come from HBM's own bomb config.")
                .push("shells");
        shell(b, "cbc_enhanced_shells:nuke_shell", "CBC Enhanced Shells: Nuke Shell (fission)", Bomb.LITTLE_BOY);
        shell(b, "cbc_enhanced_shells:nuke_shell_mk2", "CBC Enhanced Shells: Nuke Shell Mk2 (bigger fission)", Bomb.FAT_MAN);
        shell(b, "cbc_enhanced_shells:h_bomb_shell", "CBC Enhanced Shells: H-Bomb Shell (hydrogen)", Bomb.IVY_MIKE);
        shell(b, "cbc_enhanced_shells:h_bomb_mini_shell", "CBC Enhanced Shells: Mini H-Bomb Shell", Bomb.MINI_NUKE_HIGH);
        shell(b, "shupapium:fission_bomb_shell_projectile", "CBC: Warium Projectiles: Fission Bomb Shell", Bomb.FAT_MAN);
        shell(b, "shupapium:fusion_bomb_shell_projectile", "CBC: Warium Projectiles: Fusion Bomb Shell", Bomb.IVY_MIKE);
        b.pop();
        SPEC = b.build();
    }

    private static void shell(ForgeConfigSpec.Builder b, String id, String name, Bomb def) {
        SHELLS.put(id, b.comment(name).defineEnum(id.replace(':', '.'), def));
    }

    private ShellConfig() {
    }

    /** The bomb for a shell entity id, or null if it isn't a shell this mod handles. */
    public static Bomb bombFor(String id) {
        ForgeConfigSpec.EnumValue<Bomb> v = SHELLS.get(id);
        if (v != null) {
            try {
                return v.get();
            } catch (IllegalStateException e) {
                return v.getDefault();
            }
        }
        return null;
    }
}
