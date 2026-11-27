package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/**
 * Land claims. One claim is one 16x16 chunk (all heights). The owner and the players he trusts
 * can build and open containers there; everybody else can not.
 * Not covered: explosions, pistons, flowing liquids and mob griefing.
 */
final class ClaimModule implements Listener {
    private static final int BUCKET = 325;
    private static final int FLINT_AND_STEEL = 259;

    private final NEXORASurvival plugin;
    private final Store store;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    ClaimModule(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.store = new Store(new File(plugin.getDataFolder(), "claims.yml"));
    }

    void flush() {
        store.flush();
    }

    private String key(Level level, int chunkX, int chunkZ) {
        return level.getName().replaceAll("[^A-Za-z0-9_-]", "_") + "_" + chunkX + "_" + chunkZ;
    }

    private String ownerOf(String key) {
        return store.cfg.getString("claims." + key + ".owner", "");
    }

    private int claimCount(UUID owner) {
        int count = 0;
        ConfigSection all = store.cfg.getSection("claims");
        for (String k : all.getKeys(false)) {
            if (ownerOf(k).equals(owner.toString())) count++;
        }
        return count;
    }

    private boolean isTrusted(String owner, UUID player) {
        return store.cfg.exists("trust." + owner + "." + player);
    }

    // ---- Protection ----

    private void warn(Player player, String text) {
        long now = System.currentTimeMillis();
        if (now - lastWarning.getOrDefault(player.getUniqueId(), 0L) < 2000L) return;
        lastWarning.put(player.getUniqueId(), now);
        player.sendMessage(PREFIX + text);
    }

    private boolean allowed(Player player, Level level, int x, int z) {
        if (NEXORASurvival.isAdmin(player)) return true;
        if (plugin.teleports().inSpawnProtection(level, x, z)) {
            warn(player, "§cСпавн защищён от строительства.");
            return false;
        }
        String claimKey = key(level, x >> 4, z >> 4);
        String owner = ownerOf(claimKey);
        if (owner.isEmpty() || owner.equals(player.getUniqueId().toString())) return true;
        if (isTrusted(owner, player.getUniqueId())) return true;
        warn(player, "§cЭта территория принадлежит §e"
                + store.cfg.getString("claims." + claimKey + ".ownerName", "другому игроку") + "§c.");
        return false;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!allowed(event.getPlayer(), block.getLevel(), block.getFloorX(), block.getFloorZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!allowed(event.getPlayer(), block.getLevel(), block.getFloorX(), block.getFloorZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getBlock();
        if (block == null) return;
        boolean container = block.getLevel().getBlockEntity(block) instanceof InventoryHolder;
        Item hand = event.getItem();
        boolean risky = hand != null && (hand.getId() == BUCKET || hand.getId() == FLINT_AND_STEEL);
        if (!container && !risky) return;
        if (!allowed(event.getPlayer(), block.getLevel(), block.getFloorX(), block.getFloorZ())) {
            event.setCancelled(true);
        }
    }

    // ---- Commands ----

    boolean handle(CommandSender sender, String cmd, String[] args) {
        if (!List.of("claim", "unclaim", "claims", "trust", "untrust", "claiminfo").contains(cmd)) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игроку.");
            return true;
        }
        switch (cmd) {
            case "claim" -> claim(player);
            case "unclaim" -> unclaim(player);
            case "claims" -> listClaims(player);
            case "trust" -> trust(player, args);
            case "untrust" -> untrust(player, args);
            default -> info(player);
        }
        return true;
    }

    private void claim(Player player) {
        Level level = player.getLevel();
        int cx = player.getFloorX() >> 4;
        int cz = player.getFloorZ() >> 4;
        String claimKey = key(level, cx, cz);
        String owner = ownerOf(claimKey);
        if (!owner.isEmpty()) {
            player.sendMessage(PREFIX + (owner.equals(player.getUniqueId().toString())
                    ? "§7Этот участок уже твой." : "§cЭтот участок уже занят."));
            return;
        }
        if (plugin.teleports().inSpawnProtection(level, player.getFloorX(), player.getFloorZ())) {
            player.sendMessage(PREFIX + "§cВозле спавна приватить нельзя.");
            return;
        }
        int limit = plugin.social().claimsLimit(player);
        if (claimCount(player.getUniqueId()) >= limit) {
            player.sendMessage(PREFIX + "§cЛимит приватов: §e" + limit + "§c. Освободи ненужный: §e/unclaim");
            return;
        }
        long price = plugin.getConfig().getLong("claims.price", 50L);
        if (price > 0 && !NEXORASurvival.isAdmin(player)
                && !plugin.economy().withdraw(player.getUniqueId(), price)) {
            player.sendMessage(PREFIX + "§cНужно " + plugin.economy().money(price) + "§c, у тебя "
                    + plugin.economy().money(plugin.economy().balance(player.getUniqueId())));
            return;
        }
        String base = "claims." + claimKey;
        store.cfg.set(base + ".owner", player.getUniqueId().toString());
        store.cfg.set(base + ".ownerName", player.getName());
        store.cfg.set(base + ".world", level.getName());
        store.cfg.set(base + ".cx", cx);
        store.cfg.set(base + ".cz", cz);
        store.touch();
        player.sendMessage(PREFIX + "§aУчасток §f" + cx + ", " + cz + " §aтеперь твой. Друзей добавляй: §e/trust <игрок>");
    }

    private void unclaim(Player player) {
        Level level = player.getLevel();
        String claimKey = key(level, player.getFloorX() >> 4, player.getFloorZ() >> 4);
        String owner = ownerOf(claimKey);
        if (owner.isEmpty()) {
            player.sendMessage(PREFIX + "§7Этот участок никому не принадлежит.");
            return;
        }
        boolean mine = owner.equals(player.getUniqueId().toString());
        if (!mine && !NEXORASurvival.isAdmin(player)) {
            player.sendMessage(PREFIX + "§cЭто не твой участок.");
            return;
        }
        store.cfg.remove("claims." + claimKey);
        store.touch();
        long refund = plugin.getConfig().getLong("claims.price", 50L)
                * plugin.getConfig().getInt("claims.refund-percent", 50) / 100;
        if (mine && refund > 0) plugin.economy().deposit(player.getUniqueId(), refund);
        player.sendMessage(PREFIX + "§aУчасток освобождён." + (mine && refund > 0
                ? " Возвращено: " + plugin.economy().money(refund) : ""));
    }

    private void listClaims(Player player) {
        List<String> rows = new ArrayList<>();
        ConfigSection all = store.cfg.getSection("claims");
        for (String k : all.getKeys(false)) {
            if (!ownerOf(k).equals(player.getUniqueId().toString())) continue;
            rows.add(store.cfg.getString("claims." + k + ".world", "?") + " §8(§f"
                    + store.cfg.getInt("claims." + k + ".cx", 0) + "§8, §f"
                    + store.cfg.getInt("claims." + k + ".cz", 0) + "§8)");
        }
        player.sendMessage(PREFIX + "§7Твои участки (" + rows.size() + "/" + plugin.social().claimsLimit(player) + "):");
        for (String row : rows) player.sendMessage("§8• §f" + row);
        if (rows.isEmpty()) player.sendMessage("§7Пока нет. Встань на нужный участок и напиши §e/claim");
    }

    private void trust(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(PREFIX + "§e/trust <игрок>");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cТы и так хозяин.");
            return;
        }
        store.cfg.set("trust." + player.getUniqueId() + "." + target.getUniqueId(), target.getName());
        store.touch();
        player.sendMessage(PREFIX + "§f" + target.getName() + " §aможет строить на всех твоих участках.");
        target.sendMessage(PREFIX + "§f" + player.getName() + " §aдал тебе право строить на своих участках.");
    }

    private void untrust(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(PREFIX + "§e/untrust <игрок>");
            return;
        }
        String section = "trust." + player.getUniqueId();
        for (String id : store.cfg.getSection(section).getKeys(false)) {
            String name = store.cfg.getString(section + "." + id, "");
            if (name.equalsIgnoreCase(args[0].toLowerCase(Locale.ROOT))) {
                store.cfg.remove(section + "." + id);
                store.touch();
                player.sendMessage(PREFIX + "§f" + name + " §7больше не может строить на твоих участках.");
                return;
            }
        }
        player.sendMessage(PREFIX + "§cТакого игрока нет в твоём списке доверенных.");
    }

    private void info(Player player) {
        String claimKey = key(player.getLevel(), player.getFloorX() >> 4, player.getFloorZ() >> 4);
        String owner = ownerOf(claimKey);
        if (owner.isEmpty()) {
            player.sendMessage(PREFIX + "§7Участок свободен. Занять: §e/claim");
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : store.cfg.getSection("trust." + owner).getKeys(false)) {
            names.add(store.cfg.getString("trust." + owner + "." + id, "?"));
        }
        player.sendMessage(PREFIX + "§7Хозяин: §f" + store.cfg.getString("claims." + claimKey + ".ownerName", "?")
                + (names.isEmpty() ? "" : "\n§7Доверенные: §f" + String.join("§7, §f", names)));
    }
}
