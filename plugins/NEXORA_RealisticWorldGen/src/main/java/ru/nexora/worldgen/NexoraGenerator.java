package ru.nexora.worldgen;

import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Normal;

import java.util.Map;

/**
 * NEXORA terrain layer. It keeps Nukkit-MOT's native cave, ore, biome and
 * structure population pipeline, then shapes the surface into broader
 * mountain chains, lowlands and occasional river corridors.
 */
public final class NexoraGenerator extends Normal {

    public NexoraGenerator() {
        super();
    }

    public NexoraGenerator(Map<String, Object> options) {
        super(options);
    }

    @Override
    public String getName() {
        return "nexora";
    }

    @Override
    public void generateChunk(int chunkX, int chunkZ) {
        super.generateChunk(chunkX, chunkZ);

        BaseFullChunk chunk = getChunkManager().getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }

        long seed = getChunkManager().getSeed();
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = worldX + x;
                int wz = worldZ + z;

                double broad = valueNoise(wx / 180.0, wz / 180.0, seed);
                double detail = valueNoise(wx / 48.0, wz / 48.0, seed ^ 0x5DEECE66DL);
                double ridge = 1.0 - Math.abs(valueNoise(wx / 260.0, wz / 260.0, seed ^ 0x9E3779B97F4A7C15L));
                double mountain = Math.pow(Math.max(0.0, ridge - 0.52), 2.0) * 330.0;
                double riverSignal = Math.abs(valueNoise(wx / 95.0, wz / 95.0, seed ^ 0xC2B2AE3D27D4EB4FL));
                double river = riverSignal < 0.075 ? (0.075 - riverSignal) * 115.0 : 0.0;

                int target = (int) Math.round(66.0 + broad * 24.0 + detail * 8.0 + mountain - river);
                target = Math.max(-48, Math.min(300, target));

                int surfaceY = findSurface(chunk, x, z);
                if (surfaceY < -64) {
                    continue;
                }

                if (target > surfaceY) {
                    for (int y = surfaceY + 1; y <= target; y++) {
                        chunk.setBlockId(x, y, z, STONE);
                    }
                    chunk.setBlockId(x, target, z, GRASS);
                    for (int y = target - 1; y >= Math.max(surfaceY + 1, target - 3); y--) {
                        chunk.setBlockId(x, y, z, DIRT);
                    }
                } else if (target < surfaceY - 2) {
                    for (int y = surfaceY; y > target; y--) {
                        chunk.setBlockId(x, y, z, AIR);
                    }
                    if (target < 62) {
                        for (int y = target + 1; y <= 62; y++) {
                            chunk.setBlockId(x, y, z, STILL_WATER);
                        }
                    } else {
                        chunk.setBlockId(x, target, z, GRASS);
                    }
                }
            }
        }
    }

    private int findSurface(BaseFullChunk chunk, int x, int z) {
        for (int y = 319; y >= -64; y--) {
            int id = chunk.getBlockId(x, y, z);
            if (id != AIR && id != STILL_WATER) {
                return y;
            }
        }
        return -65;
    }

    private static double valueNoise(double x, double z, long seed) {
        int x0 = floor(x);
        int z0 = floor(z);
        double tx = fade(x - x0);
        double tz = fade(z - z0);

        double a = hash(x0, z0, seed);
        double b = hash(x0 + 1, z0, seed);
        double c = hash(x0, z0 + 1, seed);
        double d = hash(x0 + 1, z0 + 1, seed);

        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hash(int x, int z, long seed) {
        long n = seed ^ (x * 0x632BE59BD9B4E019L) ^ (z * 0x9E3779B97F4A7C15L);
        n = (n ^ (n >>> 30)) * 0xBF58476D1CE4E5B9L;
        n = (n ^ (n >>> 27)) * 0x94D049BB133111EBL;
        n ^= n >>> 31;
        return ((n >>> 11) * 0x1.0p-52) - 1.0;
    }
}