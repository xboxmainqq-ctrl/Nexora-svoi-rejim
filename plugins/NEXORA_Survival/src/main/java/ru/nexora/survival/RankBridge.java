package ru.nexora.survival;

import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

/** Optional-at-compile-time bridge to the two standalone rank plugins. */
final class RankBridge {
    private final NEXORASurvival plugin;
    private final Plugin donate;
    private final Plugin staff;

    RankBridge(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.donate = plugin.getServer().getPluginManager().getPlugin("NEXORA_Donate");
        this.staff = plugin.getServer().getPluginManager().getPlugin("NEXORA_AdminRanks");
    }

    boolean ready() { return donate != null && staff != null && donate.isEnabled() && staff.isEnabled(); }

    private Object call(Plugin target, String method, Class<?>[] types, Object... args) {
        if (target == null || !target.isEnabled()) return null;
        try {
            Method m = target.getClass().getMethod(method, types);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Не удалось вызвать API " + target.getName() + "." + method + ": " + ex.getMessage());
            return null;
        }
    }

    private String id(Plugin target, String method, UUID uuid) {
        Object value = call(target, method, new Class<?>[]{UUID.class}, uuid);
        return value instanceof String s ? s : "";
    }

    String rankId(UUID uuid) {
        String staffId = id(staff, "getAdminRankId", uuid);
        if (!staffId.isEmpty()) return staffId;
        String donateId = id(donate, "getRankId", uuid);
        return donateId.isEmpty() ? "player" : donateId;
    }

    String prefix(UUID uuid) {
        String staffPrefix = (String) call(staff, "getPrefix", new Class<?>[]{UUID.class}, uuid);
        if (staffPrefix != null && !staffPrefix.isEmpty()) return staffPrefix;
        String donatePrefix = (String) call(donate, "getPrefix", new Class<?>[]{UUID.class}, uuid);
        return donatePrefix == null || donatePrefix.isEmpty() ? "§7Игрок" : donatePrefix;
    }

    int homesLimit(UUID uuid) {
        Object value = call(staff, "getHomesLimit", new Class<?>[]{UUID.class}, uuid);
        if (value instanceof Number n && n.intValue() >= 0) return n.intValue();
        value = call(donate, "getHomesLimit", new Class<?>[]{UUID.class}, uuid);
        return value instanceof Number n && n.intValue() >= 0 ? n.intValue() : 3;
    }

    boolean hasKit(UUID uuid, String kit) {
        Object staffAllowed = call(staff, "hasKit", new Class<?>[]{UUID.class, String.class}, uuid, kit);
        if (Boolean.TRUE.equals(staffAllowed)) return true;
        Object donateAllowed = call(donate, "hasKit", new Class<?>[]{UUID.class, String.class}, uuid, kit);
        if (Boolean.TRUE.equals(donateAllowed)) return true;
        return "player".equals(rankId(uuid)) && ("start".equals(kit) || "daily".equals(kit));
    }

    int migrateLegacy() {
        if (!ready()) {
            plugin.getLogger().error("NEXORA_Donate и NEXORA_AdminRanks должны быть установлены вместе с Survival. Старые назначения не изменены.");
            return -1;
        }
        File markerFile = new File(plugin.getDataFolder(), "rank-migration.yml");
        Config marker = new Config(markerFile, Config.YAML);
        if (marker.getBoolean("completed", false)) return 0;
        File legacyFile = new File(plugin.getDataFolder(), "ranks.yml");
        if (!legacyFile.exists()) {
            marker.set("completed", true); marker.save(); return 0;
        }
        Config legacy = new Config(legacyFile, Config.YAML);
        ConfigSection players = legacy.getSection("players");
        int migrated = 0;
        for (String rawUuid : players.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(rawUuid); } catch (IllegalArgumentException ex) { continue; }
            String old = players.getString(rawUuid, "").toLowerCase(Locale.ROOT);
            old = switch (old) {
                case "vip" -> "aurvex";
                case "premium" -> "veloria";
                case "elite" -> "solvex";
                case "legend" -> "nexorion";
                case "chief_admin" -> "staff";
                default -> old;
            };
            if (old.isEmpty() || old.equals("player")) continue;
            if (isStaff(old)) {
                if (id(staff, "getAdminRankId", uuid).isEmpty()) {
                    call(staff, "setRank", new Class<?>[]{UUID.class, String.class}, uuid, old);
                    if (old.equals(id(staff, "getAdminRankId", uuid))) migrated++;
                }
            } else if (isDonate(old)) {
                if (id(donate, "getRankId", uuid).isEmpty()) {
                    call(donate, "setRank", new Class<?>[]{UUID.class, String.class}, uuid, old);
                    if (old.equals(id(donate, "getRankId", uuid))) migrated++;
                }
            }
        }
        marker.set("completed", true);
        marker.set("migrated-count", migrated);
        marker.save();
        plugin.getLogger().info("Перенос старых рангов завершён. Назначений перенесено: " + migrated + ". Старый ranks.yml оставлен как резервная копия.");
        return migrated;
    }

    private boolean isStaff(String id) {
        return switch (id) {
            case "helper", "junior_moder", "moder", "senior_moder", "admin", "staff", "chief_admin" -> true;
            default -> false;
        };
    }

    private boolean isDonate(String id) {
        return switch (id) {
            case "aurvex", "zyreth", "veloria", "kryon", "nymeris", "solvex", "eclyra", "nexorion" -> true;
            default -> false;
        };
    }
}
