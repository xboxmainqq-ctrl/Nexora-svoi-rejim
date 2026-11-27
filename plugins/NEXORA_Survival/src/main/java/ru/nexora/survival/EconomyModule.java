package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.Item;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** Balances, transfers and selling resources. Amounts are whole coins. */
final class EconomyModule implements Listener {
    private final NEXORASurvival plugin;
    private final Store store;

    EconomyModule(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.store = new Store(new File(plugin.getDataFolder(), "economy.yml"));
    }

    void flush() {
        store.flush();
    }

    // ---- API ----

    long balance(UUID id) {
        return store.cfg.getLong("balances." + id, plugin.getConfig().getLong("economy.start-balance", 100L));
    }

    private void put(UUID id, long value) {
        store.cfg.set("balances." + id, Math.max(0L, value));
        store.touch();
    }

    void deposit(UUID id, long amount) {
        if (amount <= 0) return;
        put(id, balance(id) + amount);
        store.flush();
    }

    boolean withdraw(UUID id, long amount) {
        long current = balance(id);
        if (amount < 0 || current < amount) return false;
        put(id, current - amount);
        store.flush();
        return true;
    }

    private boolean transfer(UUID from, UUID to, long amount) {
        long current = balance(from);
        if (amount <= 0 || current < amount) return false;
        put(from, current - amount);
        put(to, balance(to) + amount);
        store.flush();
        return true;
    }

    String money(long value) {
        return "§e" + value + " §6" + plugin.getConfig().getString("economy.currency", "монет");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        store.cfg.set("names." + id, player.getName());
        if (!store.cfg.exists("balances." + id)) {
            put(id, plugin.getConfig().getLong("economy.start-balance", 100L));
        }
        store.touch();
    }

    // ---- Commands ----

    boolean handle(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "balance" -> balanceCommand(sender, args);
            case "pay" -> pay(sender, args);
            case "baltop" -> baltop(sender);
            case "eco" -> eco(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void balanceCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
                return;
            }
            sender.sendMessage(PREFIX + "§7Баланс §f" + target.getName() + "§7: " + money(balance(target.getUniqueId())));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c/balance <игрок>");
            return;
        }
        player.sendMessage(PREFIX + "§7Твой баланс: " + money(balance(player.getUniqueId())));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игроку.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§e/pay <игрок> <сумма>");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cСебе переводить нельзя.");
            return;
        }
        long amount = NEXORASurvival.parseAmount(args[1], false);
        if (amount < 0) {
            player.sendMessage(PREFIX + "§cСумма должна быть целым положительным числом.");
            return;
        }
        if (!transfer(player.getUniqueId(), target.getUniqueId(), amount)) {
            player.sendMessage(PREFIX + "§cНе хватает денег. У тебя: " + money(balance(player.getUniqueId())));
            return;
        }
        player.sendMessage(PREFIX + "§aПеревод §f" + target.getName() + "§a: " + money(amount));
        target.sendMessage(PREFIX + "§f" + player.getName() + " §aотправил тебе " + money(amount));
    }

    private void baltop(CommandSender sender) {
        ConfigSection section = store.cfg.getSection("balances");
        List<Map.Entry<String, Long>> rows = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            rows.add(Map.entry(key, store.cfg.getLong("balances." + key, 0L)));
        }
        rows.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        sender.sendMessage(PREFIX + "§6Самые богатые игроки:");
        int place = 1;
        for (Map.Entry<String, Long> row : rows) {
            if (place > 10) break;
            String shown = store.cfg.getString("names." + row.getKey(), row.getKey().substring(0, 8));
            sender.sendMessage("§8" + place + ". §f" + shown + " §8— " + money(row.getValue()));
            place++;
        }
        if (rows.isEmpty()) sender.sendMessage("§7Пока никого нет.");
    }

    // ---- Admin ----

    private void eco(CommandSender sender, String[] args) {
        if (!NEXORASurvival.isAdmin(sender)) {
            sender.sendMessage("§cНедостаточно прав.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(PREFIX + "§e/eco <give|take|set> <игрок> <сумма>");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
            return;
        }
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        long amount = NEXORASurvival.parseAmount(args[2], action.equals("set"));
        if (amount < 0) {
            sender.sendMessage(PREFIX + "§cНеверная сумма.");
            return;
        }
        UUID id = target.getUniqueId();
        switch (action) {
            case "give" -> deposit(id, amount);
            case "take" -> put(id, balance(id) - amount);
            case "set" -> put(id, amount);
            default -> {
                sender.sendMessage(PREFIX + "§e/eco <give|take|set> <игрок> <сумма>");
                return;
            }
        }
        store.flush();
        sender.sendMessage(PREFIX + "§aБаланс §f" + target.getName() + "§a: " + money(balance(id)));
    }
}
