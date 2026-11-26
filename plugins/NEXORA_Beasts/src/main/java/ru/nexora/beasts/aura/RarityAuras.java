package ru.nexora.beasts.aura;

import cn.nukkit.level.Level;
import cn.nukkit.level.ParticleEffect;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.level.particle.FlameParticle;
import cn.nukkit.math.Vector3;

/**
 * Per-rarity particle auras drawn around a player.
 *
 * Every rarity has its own shape and colour. Purely visual: nothing here touches
 * combat, targeting or entity state. Call once per scheduler step with a growing
 * {@code phase} (radians); the shape rotates with it.
 */
public final class RarityAuras {
    private static final double TAU = Math.PI * 2.0;

    private static final int[][] COLORS = {
            {170, 172, 180},   // common     steel
            {80, 225, 110},    // uncommon   green
            {70, 175, 255},    // rare       blue
            {185, 95, 255},    // epic       violet
            {255, 195, 55},    // legendary  gold
            {255, 120, 205},   // mythic     pink
            {65, 220, 205},    // ancient    jade
            {255, 240, 175},   // divine     pearl-gold
            {235, 55, 65},     // forbidden  crimson
            {200, 185, 255},   // absolute   astral
    };

    private RarityAuras() {
    }

    /** @param tier rarity index 0..9 (common..absolute) */
    public static void render(Level level, double x, double y, double z, int tier, double phase) {
        if (level == null) return;
        tier = Math.max(0, Math.min(9, tier));
        switch (tier) {
            case 0 -> common(level, x, y, z, phase);
            case 1 -> uncommon(level, x, y, z, phase);
            case 2 -> rare(level, x, y, z, phase);
            case 3 -> epic(level, x, y, z, phase);
            case 4 -> legendary(level, x, y, z, phase);
            case 5 -> mythic(level, x, y, z, phase);
            case 6 -> ancient(level, x, y, z, phase);
            case 7 -> divine(level, x, y, z, phase);
            case 8 -> forbidden(level, x, y, z, phase);
            default -> absolute(level, x, y, z, phase);
        }
    }

    // ------------------------------------------------------------ helpers

    private static void dust(Level level, double x, double y, double z, int[] c) {
        level.addParticle(new DustParticle(new Vector3(x, y, z), c[0], c[1], c[2]));
    }

    private static void ring(Level level, double cx, double cy, double cz, double r, int n, double phase, int[] c) {
        for (int i = 0; i < n; i++) {
            double a = phase + TAU * i / n;
            dust(level, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, c);
        }
    }

    private static void sparkle(Level level, double x, double y, double z) {
        level.addParticleEffect(new Vector3(x, y, z), ParticleEffect.ENDROD);
    }

    // -------------------------------------------------------------- auras

    /** Common: three grey motes drifting around the feet. */
    private static void common(Level l, double x, double y, double z, double p) {
        ring(l, x, y + 0.2, z, 0.75, 3, p * 0.8, COLORS[0]);
    }

    /** Uncommon: a slowly turning green ring on the ground. */
    private static void uncommon(Level l, double x, double y, double z, double p) {
        ring(l, x, y + 0.1, z, 0.95, 8, p * 0.6, COLORS[1]);
    }

    /** Rare: two blue orbs circling at waist height, each with a short trail. */
    private static void rare(Level l, double x, double y, double z, double p) {
        for (int orb = 0; orb < 2; orb++) {
            for (int t = 0; t < 3; t++) {
                double a = p * 1.4 + orb * Math.PI - t * 0.28;
                dust(l, x + Math.cos(a) * 0.9, y + 1.0 + Math.sin(p + orb) * 0.12,
                        z + Math.sin(a) * 0.9, COLORS[2]);
            }
        }
    }

    /** Epic: a double violet helix rising around the player. */
    private static void epic(Level l, double x, double y, double z, double p) {
        for (int strand = 0; strand < 2; strand++) {
            for (int i = 0; i < 6; i++) {
                double a = p + strand * Math.PI + i * 0.55;
                dust(l, x + Math.cos(a) * 0.7, y + 0.15 + i * 0.36, z + Math.sin(a) * 0.7, COLORS[3]);
            }
        }
    }

    /** Legendary: golden ground ring, counter-rotating waist ring and rising flames. */
    private static void legendary(Level l, double x, double y, double z, double p) {
        ring(l, x, y + 0.1, z, 1.05, 10, p * 0.7, COLORS[4]);
        ring(l, x, y + 1.0, z, 0.75, 6, -p * 1.1, COLORS[4]);
        for (int i = 0; i < 3; i++) {
            double a = p * 0.5 + TAU * i / 3.0;
            l.addParticle(new FlameParticle(new Vector3(x + Math.cos(a) * 1.05, y + 0.15, z + Math.sin(a) * 1.05)));
        }
    }

    /** Mythic: two counter-rotating orbits of pink petals with a sparkle above. */
    private static void mythic(Level l, double x, double y, double z, double p) {
        ring(l, x, y + 0.6, z, 0.9, 5, p, COLORS[5]);
        ring(l, x, y + 1.4, z, 0.8, 5, -p * 1.2, COLORS[5]);
        sparkle(l, x + Math.cos(p * 2) * 0.3, y + 2.1, z + Math.sin(p * 2) * 0.3);
    }

    /** Ancient: a slowly turning teal rune hexagon on the ground. */
    private static void ancient(Level l, double x, double y, double z, double p) {
        double r = 1.15, base = p * 0.35;
        for (int i = 0; i < 6; i++) {
            double a0 = base + TAU * i / 6.0, a1 = base + TAU * (i + 1) / 6.0;
            double x0 = x + Math.cos(a0) * r, z0 = z + Math.sin(a0) * r;
            double x1 = x + Math.cos(a1) * r, z1 = z + Math.sin(a1) * r;
            dust(l, x0, y + 0.1, z0, COLORS[6]);
            dust(l, (x0 + x1) / 2, y + 0.1, (z0 + z1) / 2, COLORS[6]);
        }
        dust(l, x, y + 0.1, z, COLORS[6]);
    }

    /** Divine: a golden halo above the head and light feathers falling down. */
    private static void divine(Level l, double x, double y, double z, double p) {
        ring(l, x, y + 2.3, z, 0.45, 8, p * 0.9, COLORS[7]);
        for (int i = 0; i < 2; i++) {
            double a = p * 0.7 + i * Math.PI;
            double fall = ((p * 0.6 + i * 0.5) % 1.0);
            sparkle(l, x + Math.cos(a) * 0.8, y + 2.2 - fall * 2.0, z + Math.sin(a) * 0.8);
        }
    }

    /** Forbidden: a crimson vortex that widens as it rises, with embers at its base. */
    private static void forbidden(Level l, double x, double y, double z, double p) {
        for (int arm = 0; arm < 3; arm++) {
            for (int i = 0; i < 6; i++) {
                double a = p * 1.5 + TAU * arm / 3.0 + i * 0.6;
                double r = 0.4 + i * 0.16;
                dust(l, x + Math.cos(a) * r, y + 0.1 + i * 0.13, z + Math.sin(a) * r, COLORS[8]);
            }
        }
        double a = -p;
        l.addParticle(new FlameParticle(new Vector3(x + Math.cos(a) * 0.9, y + 0.1, z + Math.sin(a) * 0.9)));
    }

    /** Absolute: three tilted astral orbit rings around the player and a star above. */
    private static void absolute(Level l, double x, double y, double z, double p) {
        double[] tilt = {0.0, 1.05, -1.05};
        int[][] cols = {COLORS[9], {150, 105, 255}, {255, 255, 255}};
        for (int ring = 0; ring < 3; ring++) {
            double yaw = p * (0.8 + ring * 0.25) + ring * 2.1;
            for (int i = 0; i < 6; i++) {
                double a = TAU * i / 6.0 + p * 1.3;
                double lx = Math.cos(a) * 1.05;                   // position inside the ring plane
                double ly = Math.sin(a) * 1.05 * Math.sin(tilt[ring]);
                double lz = Math.sin(a) * 1.05 * Math.cos(tilt[ring]);
                double wx = lx * Math.cos(yaw) - lz * Math.sin(yaw);
                double wz = lx * Math.sin(yaw) + lz * Math.cos(yaw);
                dust(l, x + wx, y + 1.0 + ly, z + wz, cols[ring]);
            }
        }
        sparkle(l, x, y + 2.2 + Math.sin(p * 2) * 0.1, z);
    }
}
