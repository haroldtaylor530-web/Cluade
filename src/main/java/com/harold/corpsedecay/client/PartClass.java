package com.harold.corpsedecay.client;

import java.util.Locale;

/** What a model part is, judged from its name. Works for modded mobs that use normal names too. */
public enum PartClass {
    HEAD, BODY, LEG, ARM, TAIL, WING, FIN, NECK, EAR, HORN, OUTER, OTHER;

    public static PartClass of(String name) {
        if (name == null) return OTHER;
        String n = name.toLowerCase(Locale.ROOT);
        if (has(n, "leg", "foot", "feet", "haunch", "thigh", "shin", "paw")) return LEG;
        if ((n.contains("arm") && !n.contains("armor") && !n.contains("armour")) || n.contains("hand")) return ARM;
        if (n.contains("tail")) return TAIL;
        if (n.contains("wing")) return WING;
        if (n.contains("fin") && !n.contains("finger")) return FIN;
        if (n.contains("neck")) return NECK;
        if (has(n, "horn", "antler", "tusk")) return HORN;
        if (has(n, "hat", "jacket", "sleeve", "pants", "cape", "cloak", "hair", "mane", "beard", "wool", "fur", "hood", "robe", "coat")) {
            return OUTER;
        }
        if (n.contains("ear")) return EAR;
        if (has(n, "head", "skull", "jaw", "beak", "bill", "snout", "nose", "mouth", "muzzle", "face")) return HEAD;
        if (has(n, "body", "torso", "chest", "belly", "shell", "hip", "pelvis", "rib", "waist")) return BODY;
        return OTHER;
    }

    private static boolean has(String n, String... words) {
        for (String w : words) {
            if (n.contains(w)) return true;
        }
        return false;
    }

    /** Parts that swing loose on a dead body. */
    public boolean flops() {
        return this == HEAD || this == LEG || this == ARM || this == TAIL || this == WING || this == NECK;
    }

    /** How far (radians) a limb can swing away from its normal pose. */
    public float swing() {
        return switch (this) {
            case LEG -> 1.25F;
            case ARM -> 1.45F;
            case HEAD -> 0.95F;
            case NECK -> 0.7F;
            case TAIL -> 1.2F;
            case WING -> 0.9F;
            default -> 0.5F;
        };
    }
}
