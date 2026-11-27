package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.Listener;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** Homes, spawn, warps and player-to-player teleport requests. */
final class TeleportModule implements Listener {
    private record Request(UUID from, long expires) { }

    private final NEXORASurvival plugin;
    private final Store homes;
    private final Store warps;
    private final Map<UUID, Request> requests = new HashMap<>();   // key = player who received the request
    private final Set<UUID> pending = new HashSet<>();             // players waiting for a teleport
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    TeleportModule(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.homes = new Store(new File(plugin.getDataFolder(), "homes.yml"));
        this.warps = new Store(new File(plugin.getDataFolder(), "warps.yml"));
    }

    void flush() {
        homes.flush();
        warps.flush();
    }

    // ---- Positions ----

    private void savePos(Store store, String path, Player player) {
        store.cfg.set(path + ".world", player.getLevel().getName());
        store.cfg.set(path + ".x", player.getX());
        store.cfg.set(path + ".y", player.getY());
        store.cfg.set(path + ".z", player.getZ());
        store.cfg.set(path + ".yaw", player.getYaw());
        store.cfg.set(path + ".pitch", player.getPitch());
        store.touch();
    }

    private Location readPos(Store store, String path) {
        if (!store.cfg.exists(path + ".world")) return null;
        String world = store.cfg.getString(path + ".world");
        if (!plugin.getServer().isLevelLoaded(world)) plugin.getServer().loadLevel(world);
        Level level = plugin.getServer().getLevelByName(world);
        if (level == null) return null;
        return new Location(store.cfg.getDouble(path + ".x"), store.cfg.getDouble(path + ".y"),
                store.cfg.getDouble(path + ".z"), store.cfg.getDouble(path + ".yaw"),
                store.cfg.getDouble(path + ".pitch"), level);
    }

    private Location spawnLocation() {
        Location saved = readPos(warps, "spawn");
        if (saved != null) return saved;
        Level level = plugin.getServer().getDefaultLevel();
        if (level == null) return null;
        Position safe = level.getSafeSpawn();
        return new Location(safe.x, safe.y, safe.z, 0, 0, level);
    }

    /** True if the block column is inside the protected area around the spawn. */
    boolean inSpawnProtection(Level level, int x, int z) {
        // BlockFromToEvent may expose a transient block with no attached Level.
        if (level == null) return false;
        int radius = plugin.getConfig().getInt("teleport.spawn-protect-radius", 16);
        if (radius <= 0) return false;
        Location spawn = spawnLocation();
        if (spawn == null || spawn.getLevel() == null) return false;
        if (!spawn.getLevel().getName().equals(level.getName())) return false;
        double dx = x + 0.5 - spawn.getX();
        double dz = z + 0.5 - spawn.getZ();
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /** Instant teleport to the spawn (used for new players). */
    void sendToSpawn(Player player) {
        Location spawn = spawnLocation();
        if (spawn != null) player.teleport(spawn);
    }

    private static String key(String raw) {
        String name = raw.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_-]", "");
        if (name.length() > 16) name = name.substring(0, 16);
        return name;
    }

    private List<String> keys(Store store, String path) {
        ConfigSection section = store.cfg.getSection(path);
        return new ArrayList<>(section.getKeys(false));
    }

    // ---- Teleport with warmup ----

    private void teleport(Player player, Location destination, String label) {
        UUID id = player.getUniqueId();
        boolean admin = NEXORASurvival.isAdmin(player);
        if (pending.contains(id)) {
            player.sendMessage(PREFIX + "§cТелепортация уже идёт.");
            return;
        }
        long cooldownMs = plugin.getConfig().getInt("teleport.cooldown-seconds", 10) * 1000L;
        long left = lastTeleport.getOrDefault(id, 0L) + cooldownMs - System.currentTimeMillis();
        if (!admin && left > 0) {
            player.sendMessage(PREFIX + "§cПодожди ещё §e" + (left / 1000 + 1) + " §cсек.");
            return;
        }
        int warmup = admin ? 0 : plugin.getConfig().getInt("teleport.warmup-seconds", 3);
        if (warmup <= 0) {
            player.teleport(destination);
            lastTeleport.put(id, System.currentTimeMillis());
            player.sendMessage(PREFIX + "§aТелепорт: §f" + label);
            return;
        }
        double sx = player.getX(), sy = player.getY(), sz = player.getZ();
        pending.add(id);
        player.sendMessage(PREFIX + "§7Телепорт через §e" + warmup + " §7сек. Не двигайся.");
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
            pending.remove(id);
            if (!player.isOnline()) return;
            double moved = Math.abs(player.getX() - sx) + Math.abs(player.getY() - sy) + Math.abs(player.getZ() - sz);
            if (moved > 1.5) {
                player.sendMessage(PREFIX + "§cТелепорт отменён: ты сдвинулся с места.");
                return;
            }
            player.teleport(destination);
            lastTeleport.put(id, System.currentTimeMillis());
            player.sendMessage(PREFIX + "§aТелепорт: §f" + label);
        }, warmup * 20);
    }

    // ---- Commands ----

    boolean handle(CommandSender sender, String cmd, String[] args) {
        switch (cmd) {
            case "sethome", "home", "delhome", "homes", "spawn", "warp", "tpa", "tpaccept", "tpdeny" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cКоманда доступна только игроку.");
                    return true;
                }
                playerCommand(player, cmd, args);
                return true;
            }
            case "setspawn", "setwarp", "delwarp" -> {
                adminCommand(sender, cmd, args);
                return true;
            }
            default -> { return false; }
        }
    }

    private void playerCommand(Player player, String cmd, String[] args) {
        switch (cmd) {
            case "sethome" -> setHome(player, args);
            case "home" -> home(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> listHomes(player);
            case "spawn" -> {
                Location spawn = spawnLocation();
                if (spawn == null) player.sendMessage(PREFIX + "§cСпавн ещё не задан.");
                else teleport(player, spawn, "спавн");
            }
            case "warp" -> warp(player, args);
            case "tpa" -> tpa(player, args);
            case "tpaccept" -> tpAccept(player);
            default -> tpDeny(player);
        }
    }

    // Homes

    private String homeBase(Player player) {
        return "homes." + player.getUniqueId();
    }

    private void setHome(Player player, String[] args) {
        String name = key(args.length > 0 ? args[0] : "home");
        if (name.isEmpty()) name = "home";
        String path = homeBase(player) + "." + name;
        List<String> existing = keys(homes, homeBase(player));
        if (!existing.contains(name) && existing.size() >= plugin.social().homesLimit(player)) {
            player.sendMessage(PREFIX + "§cЛимит домов: §e" + plugin.social().homesLimit(player)
                    + "§c. Удали лишний: §e/delhome <имя>");
            return;
        }
        savePos(homes, path, player);
        player.sendMessage(PREFIX + "§aДом §f" + name + " §aсохранён.");
    }

    private void home(Player player, String[] args) {
        List<String> names = keys(homes, homeBase(player));
        if (names.isEmpty()) {
            player.sendMessage(PREFIX + "§cУ тебя нет домов. Создай: §e/sethome");
            return;
        }
        if (args.length > 0) {
            goHome(player, key(args[0]));
        } else if (names.size() == 1) {
            goHome(player, names.get(0));
        } else {
            List<String> labels = new ArrayList<>();
            for (String n : names) labels.add("§l" + n);
            NEXORASurvival.choose(player, "§l§0МОИ ДОМА", "§7Куда телепортироваться?", labels,
                    index -> goHome(player, names.get(index)));
        }
    }

    private void goHome(Player player, String name) {
        Location location = readPos(homes, homeBase(player) + "." + name);
        if (location == null) {
            player.sendMessage(PREFIX + "§cДом §f" + name + " §cне найден. Список: §e/homes");
            return;
        }
        teleport(player, location, "дом " + name);
    }

    private void delHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(PREFIX + "§e/delhome <имя>");
            return;
        }
        String name = key(args[0]);
        String path = homeBase(player) + "." + name;
        if (!homes.cfg.exists(path + ".world")) {
            player.sendMessage(PREFIX + "§cДом не найден.");
            return;
        }
        homes.cfg.remove(path);
        homes.touch();
        player.sendMessage(PREFIX + "§aДом §f" + name + " §aудалён.");
    }

    private void listHomes(Player player) {
        List<String> names = keys(homes, homeBase(player));
        if (names.isEmpty()) {
            player.sendMessage(PREFIX + "§7Домов пока нет. Создай: §e/sethome");
            return;
        }
        player.sendMessage(PREFIX + "§7Дома (" + names.size() + "/" + plugin.social().homesLimit(player)
                + "): §f" + String.join("§7, §f", names));
    }

    // Warps

    private void warp(Player player, String[] args) {
        List<String> names = keys(warps, "warps");
        if (names.isEmpty()) {
            player.sendMessage(PREFIX + "§7Варпов пока нет.");
            return;
        }
        if (args.length > 0) {
            goWarp(player, key(args[0]));
            return;
        }
        List<String> labels = new ArrayList<>();
        for (String n : names) labels.add("§l" + n);
        NEXORASurvival.choose(player, "§l§0ВАРПЫ", "§7Выбери место:", labels,
                index -> goWarp(player, names.get(index)));
    }

    private void goWarp(Player player, String name) {
        Location location = readPos(warps, "warps." + name);
        if (location == null) {
            player.sendMessage(PREFIX + "§cВарп §f" + name + " §cне найден. Список: §e/warp");
            return;
        }
        teleport(player, location, "варп " + name);
    }

    private void adminCommand(CommandSender sender, String cmd, String[] args) {
        if (!NEXORASurvival.isAdmin(sender)) {
            sender.sendMessage("§cНедостаточно прав.");
            return;
        }
        switch (cmd) {
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cКоманда доступна только игроку.");
                    return;
                }
                savePos(warps, "spawn", player);
                warps.flush();
                player.sendMessage(PREFIX + "§aСпавн сохранён.");
            }
            case "setwarp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cКоманда доступна только игроку.");
                    return;
                }
                String name = args.length > 0 ? key(args[0]) : "";
                if (name.isEmpty()) {
                    player.sendMessage(PREFIX + "§e/setwarp <имя>");
                    return;
                }
                savePos(warps, "warps." + name, player);
                warps.flush();
                player.sendMessage(PREFIX + "§aВарп §f" + name + " §aсоздан.");
            }
            default -> {
                String name = args.length > 0 ? key(args[0]) : "";
                if (name.isEmpty() || !warps.cfg.exists("warps." + name + ".world")) {
                    sender.sendMessage(PREFIX + "§cВарп не найден. Использование: §e/delwarp <имя>");
                    return;
                }
                warps.cfg.remove("warps." + name);
                warps.touch();
                warps.flush();
                sender.sendMessage(PREFIX + "§aВарп §f" + name + " §aудалён.");
            }
        }
    }

    // Teleport requests

    private void tpa(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(PREFIX + "§e/tpa <игрок>");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cК себе телепортироваться не нужно.");
            return;
        }
        int seconds = plugin.getConfig().getInt("teleport.tpa-timeout-seconds", 60);
        requests.put(target.getUniqueId(), new Request(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L));
        player.sendMessage(PREFIX + "§aЗапрос отправлен игроку §f" + target.getName() + "§a.");
        target.sendMessage(PREFIX + "§f" + player.getName() + " §7хочет телепортироваться к тебе. "
                + "§a/tpaccept §7или §c/tpdeny §8(" + seconds + " сек.)");
    }

    private void tpAccept(Player me) {
        Request request = requests.remove(me.getUniqueId());
        if (request == null || request.expires() < System.currentTimeMillis()) {
            me.sendMessage(PREFIX + "§cАктивных запросов нет.");
            return;
        }
        Player from = plugin.getServer().getOnlinePlayers().get(request.from());
        if (from == null || !from.isOnline()) {
            me.sendMessage(PREFIX + "§cИгрок уже вышел из игры.");
            return;
        }
        me.sendMessage(PREFIX + "§aЗапрос принят.");
        Location here = new Location(me.getX(), me.getY(), me.getZ(), me.getYaw(), me.getPitch(), me.getLevel());
        teleport(from, here, "к игроку " + me.getName());
    }

    private void tpDeny(Player me) {
        Request request = requests.remove(me.getUniqueId());
        if (request == null || request.expires() < System.currentTimeMillis()) {
            me.sendMessage(PREFIX + "§cАктивных запросов нет.");
            return;
        }
        me.sendMessage(PREFIX + "§7Запрос отклонён.");
        Player from = plugin.getServer().getOnlinePlayers().get(request.from());
        if (from != null) from.sendMessage(PREFIX + "§c" + me.getName() + " отклонил твой запрос.");
    }
}
