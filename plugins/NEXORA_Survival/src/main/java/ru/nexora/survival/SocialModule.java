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

    void flush() { ranks.flush(); kits.flush(); }

    String rankId(UUID id) {
        String rank = ranks.cfg.getString("players." + id, "");
        // Migrate old public rank IDs to the new NEXORA names without resetting players.
        rank = switch (rank.toLowerCase(Locale.ROOT)) {
            case "vip" -> "aurvex";
            case "premium" -> "veloria";
            case "elite" -> "solvex";
            case "legend" -> "nexorion";
            default -> rank;
        };
        if (!rank.isEmpty() && plugin.getConfig().exists("ranks." + rank + ".prefix")) return rank;
        return plugin.getConfig().getString("default-rank", "player");
    }

    // Independent PUA glyph range: clan gradients end at U+F74F.
    private static final int RANK_GLYPH_BASE = 0xF750;
    private static final int RANK_GRADIENT_STEPS = 3;

    String prefix(Player player) {
        String id = rankId(player.getUniqueId()).toLowerCase(Locale.ROOT);
        String label = plugin.getConfig().getString("ranks." + id + ".label", "");
        int offset = rankGlyphOffset(id);
        if (offset < 0 || label.isEmpty()) {
            return plugin.getConfig().getString("ranks." + id + ".prefix", "§7Игрок");
        }
        return rankGradient(label, offset);
    }

    private int rankGlyphOffset(String id) {
        return switch (id) {
            case "aurvex" -> 0;
            case "zyreth" -> 18;
            case "veloria" -> 36;
            case "kryon" -> 57;
            case "nymeris" -> 72;
            case "solvex" -> 93;
            case "eclyra" -> 111;
            case "nexorion" -> 129;
            case "helper" -> 153;
            case "junior_moder" -> 171;
            case "moder" -> 219;
            case "senior_moder" -> 246;
            case "admin" -> 294;
            case "chief_admin" -> 333;
            default -> -1;
        };
    }

    private String rankGradient(String value, int offset) {
        StringBuilder out = new StringBuilder();
        int visibleLength = value.replace(" ", "").length();
        int position = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ' ') {
                out.append(' ');
                continue;
            }
            int step = visibleLength <= 1 ? 0
                    : (int) Math.round((double) position * (RANK_GRADIENT_STEPS - 1) / (visibleLength - 1));
            int codePoint = RANK_GLYPH_BASE + offset + position * RANK_GRADIENT_STEPS + step;
            out.appendCodePoint(codePoint);
            position++;
        }
        return out.toString();
    }

    int homesLimit(Player player) {
        if (NEXORASurvival.isAdmin(player)) return 100;
        return plugin.getConfig().getInt("ranks." + rankId(player.getUniqueId()) + ".homes", 3);
    }

    private void applyDisplay(Player player) {
        player.setDisplayName(player.getName());
        String rankTag = "§8[§r" + prefix(player) + "§8]";
        String clanTag = plugin.clans().tag(player);
        player.setNameTag(rankTag + (clanTag.isEmpty() ? "" : " " + clanTag) + " §f" + player.getName());
    }

    void refreshDisplay(Player player) { applyDisplay(player); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        applyDisplay(player);
        event.setJoinMessage("§8[§a+§8] " + prefix(player) + " §f" + player.getName());
        boolean firstJoin = !kits.cfg.exists("players." + player.getUniqueId() + ".start");
        if (firstJoin && plugin.getConfig().getBoolean("start-kit-on-first-join", true)
                && plugin.getConfig().exists("kits.start.items")) {
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
        String rankTag = "§8[§r" + prefix(player) + "§8]";
        String clanTag = plugin.clans().tag(player);
        String line = rankTag + (clanTag.isEmpty() ? "" : " " + clanTag)
                + " §f" + player.getName() + "§7: §f" + message;
        plugin.getServer().broadcastMessage(line);
    }

    boolean handle(CommandSender sender, String cmd, String[] args) {
        switch (cmd) {
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
