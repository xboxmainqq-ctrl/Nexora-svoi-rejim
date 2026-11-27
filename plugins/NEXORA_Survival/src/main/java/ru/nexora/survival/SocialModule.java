package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** Ranks (prefix and limits), chat format and kits. */
final class SocialModule implements Listener {
    private final NEXORASurvival plugin;
    private final Store ranks;
    private final Store kits;

    SocialModule(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.ranks = new Store(new File(plugin.getDataFolder(), "ranks.yml"));
        this.kits = new Store(new File(plugin.getDataFolder(), "kits.yml"));
    }

    void flush() {
        ranks.flush();
        kits.flush();
    }

    // ---- Ranks ----

    String rankId(UUID id) {
        String rank = ranks.cfg.getString("players." + id, "");
        if (!rank.isEmpty() && plugin.getConfig().exists("ranks." + rank + ".prefix")) return rank;
        return plugin.getConfig().getString("default-rank", "player");
    }

    String prefix(Player player) {
        return plugin.getConfig().getString("ranks." + rankId(player.getUniqueId()) + ".prefix", "§7Игрок");
    }

    int homesLimit(Player player) {
        if (NEXORASurvival.isAdmin(player)) return 100;
        return plugin.getConfig().getInt("ranks." + rankId(player.getUniqueId()) + ".homes", 3);
    }

    int claimsLimit(Player player) {
        if (NEXORASurvival.isAdmin(player)) return 1000;
        return plugin.getConfig().getInt("ranks." + rankId(player.getUniqueId()) + ".claims", 4);
    }

    private void applyDisplay(Player player) {
        player.setDisplayName(player.getName());
        String rankTag = "§8[§r" + prefix(player) + "§8]";
        String clanTag = plugin.clans().tag(player);
        player.setNameTag(rankTag + (clanTag.isEmpty() ? "" : " " + clanTag) + " §f" + player.getName());
    }

    void refreshDisplay(Player player) { applyDisplay(player); }

    // ---- Events ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyDisplay(player);
        event.setJoinMessage("§8[§a+§8] " + prefix(player) + " §f" + player.getName());
        boolean firstJoin = !kits.cfg.exists("players." + player.getUniqueId() + ".start");
        if (firstJoin && plugin.getConfig().getBoolean("start-kit-on-first-join", true)
                && plugin.getConfig().exists("kits.start.items")) {
            // Wait a second so the client is fully in the world.
            plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
                if (!player.isOnline()) return;
                plugin.teleports().sendToSpawn(player);
                giveKit(player, "start");
            }, 20);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.setQuitMessage("§8[§c-§8] " + prefix(player) + " §f" + player.getName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(PlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String message = NEXORASurvival.clean(event.getMessage());
        if (message.isEmpty()) return;
        // {message} goes last so text typed by a player is never treated as a placeholder.
        String format = plugin.getConfig().getString("chat.format",
                "§8[§r{prefix}§8] {clan} §f{name}§7: §f{message}");
        // Older config.yml files may not have the {clan} placeholder.
        // Insert it automatically after the rank so clan tags remain visible
        // without requiring server owners to delete their existing config.
        if (!format.contains("{clan}")) {
            format = format.replace("{prefix}", "{prefix} {clan}");
        }
        String line = format
                .replace("{prefix}", prefix(player))
                .replace("{clan}", plugin.clans().tag(player))
                .replace("{name}", player.getName())
                .replace("{message}", message);
        plugin.getServer().broadcastMessage(line);
    }

    // ---- Commands ----

    boolean handle(CommandSender sender, String cmd, String[] args) {
        switch (cmd) {
            case "rank" -> rank(sender, args);
            case "kit" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cКоманда доступна только игроку.");
                    return true;
                }
                kitCommand(player, args);
            }
            default -> { return false; }
        }
        return true;
    }

    private void rank(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                sender.sendMessage(PREFIX + "§6Ранги:");
                for (String id : plugin.getConfig().getSection("ranks").getKeys(false)) {
                    sender.sendMessage("§8• §f" + id + " §8— " + plugin.getConfig().getString("ranks." + id + ".prefix", id)
                            + " §8(дома: §f" + plugin.getConfig().getInt("ranks." + id + ".homes", 3)
                            + "§8, приваты: §f" + plugin.getConfig().getInt("ranks." + id + ".claims", 4) + "§8)");
                }
            }
            case "info" -> {
                Player target = args.length > 1 ? plugin.getServer().getPlayer(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
                    return;
                }
                sender.sendMessage(PREFIX + "§f" + target.getName() + "§7: ранг §f" + rankId(target.getUniqueId())
                        + " §8(" + prefix(target) + "§8)");
            }
            case "set" -> {
                if (!NEXORASurvival.isAdmin(sender)) {
                    sender.sendMessage("§cНедостаточно прав.");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + "§e/rank set <игрок> <ранг>");
                    return;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                String rank = args[2].toLowerCase(Locale.ROOT);
                if (target == null) {
                    sender.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
                    return;
                }
                if (!plugin.getConfig().exists("ranks." + rank + ".prefix")) {
                    sender.sendMessage(PREFIX + "§cТакого ранга нет. Список: §e/rank list");
                    return;
                }
                ranks.cfg.set("players." + target.getUniqueId(), rank);
                ranks.touch();
                ranks.flush();
                applyDisplay(target);
                sender.sendMessage(PREFIX + "§aРанг игрока §f" + target.getName() + " §a— §f" + rank);
                target.sendMessage(PREFIX + "§aТвой ранг теперь: " + prefix(target));
            }
            default -> sender.sendMessage(PREFIX + "§e/rank list §7| §e/rank info [игрок] §7| §e/rank set <игрок> <ранг>");
        }
    }

    // ---- Kits ----

    private boolean kitAllowed(Player player, String kit) {
        if (NEXORASurvival.isAdmin(player)) return true;
        return plugin.getConfig().getStringList("ranks." + rankId(player.getUniqueId()) + ".kits").contains(kit);
    }

    private boolean kitReady(Player player, String kit) {
        int hours = plugin.getConfig().getInt("kits." + kit + ".cooldown-hours", 24);
        long last = kits.cfg.getLong("players." + player.getUniqueId() + "." + kit, 0L);
        if (last <= 0) return true;
        if (hours <= 0) return false;
        return System.currentTimeMillis() >= last + hours * 3_600_000L;
    }

    private String kitStatus(Player player, String kit) {
        int hours = plugin.getConfig().getInt("kits." + kit + ".cooldown-hours", 24);
        long last = kits.cfg.getLong("players." + player.getUniqueId() + "." + kit, 0L);
        if (last <= 0) return "§aготов";
        if (hours <= 0) return "§7уже получен";
        long left = last + hours * 3_600_000L - System.currentTimeMillis();
        if (left <= 0) return "§aготов";
        long minutes = left / 60_000L + 1;
        return "§eчерез " + (minutes / 60) + " ч " + (minutes % 60) + " мин";
    }

    private void kitCommand(Player player, String[] args) {
        List<String> names = new ArrayList<>();
        ConfigSection all = plugin.getConfig().getSection("kits");
        for (String kit : all.getKeys(false)) {
            if (plugin.getConfig().exists("kits." + kit + ".items") && kitAllowed(player, kit)) names.add(kit);
        }
        if (args.length > 0) {
            String kit = args[0].toLowerCase(Locale.ROOT);
            if (!names.contains(kit)) {
                player.sendMessage(PREFIX + "§cТакого набора нет или он тебе недоступен. Список: §e/kit");
                return;
            }
            giveKit(player, kit);
            return;
        }
        if (names.isEmpty()) {
            player.sendMessage(PREFIX + "§7Тебе пока недоступны наборы.");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (String kit : names) {
            labels.add("§l" + plugin.getConfig().getString("kits." + kit + ".name", kit) + "\n§8" + kitStatus(player, kit));
        }
        NEXORASurvival.choose(player, "§l§0НАБОРЫ", "§7Выбери набор:", labels,
                index -> giveKit(player, names.get(index)));
    }

    private void giveKit(Player player, String kit) {
        String base = "kits." + kit;
        List<String> items = plugin.getConfig().getStringList(base + ".items");
        if (items.isEmpty()) {
            player.sendMessage(PREFIX + "§cНабор не найден.");
            return;
        }
        if (!kitReady(player, kit)) {
            player.sendMessage(PREFIX + "§cНабор пока недоступен: " + kitStatus(player, kit));
            return;
        }
        for (String spec : items) {
            Item item = parseItem(spec);
            if (item == null) {
                plugin.getLogger().warning("Набор " + kit + ": неверный предмет «" + spec + "», ожидается id:meta:count");
                continue;
            }
            // Whatever does not fit is dropped next to the player, nothing is lost.
            for (Item leftover : player.getInventory().addItem(item)) {
                player.getLevel().dropItem(player, leftover);
            }
        }
        kits.cfg.set("players." + player.getUniqueId() + "." + kit, System.currentTimeMillis());
        kits.touch();
        kits.flush();
        player.sendMessage(PREFIX + "§aТы получил набор §f" + plugin.getConfig().getString(base + ".name", kit) + "§a.");
    }

    private Item parseItem(String spec) {
        String[] parts = spec.split(":");
        if (parts.length != 3) return null;
        try {
            int id = Integer.parseInt(parts[0].trim());
            int meta = Integer.parseInt(parts[1].trim());
            int count = Integer.parseInt(parts[2].trim());
            if (id <= 0 || count <= 0 || count > 64) return null;
            return Item.get(id, meta, count);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
