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
            case "sell" -> sell(sender, args);
            case "seller" -> seller(sender);
            case "prices" -> prices(sender);
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

    // ---- Selling ----

    private int priceOf(Item item) {
        if (item == null || item.isNull()) return 0;
        if (item.hasCustomName() || item.hasEnchantments()) return 0;
        return plugin.getConfig().getInt("economy.sell-prices." + item.getId() + ".price", 0);
    }

    private void sell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игроку.");
            return;
        }
        if (args.length == 0) {
            openSeller(player);
            return;
        }
        if (args[0].equalsIgnoreCase("hand")) {
            sellHand(player, args);
        } else if (args[0].equalsIgnoreCase("all")) {
            sellAll(player);
        } else {
            player.sendMessage(PREFIX + "§e/sell hand [количество] §7или §e/sell all");
        }
    }

    // ---- Chest-style seller menu ----

    private record SellCategory(String id, String title, String description) { }

    private static final List<SellCategory> SELL_CATEGORIES = List.of(
            new SellCategory("food", "§l§6🍖 ЕДА", "Мясо, хлеб, рыба и готовая еда"),
            new SellCategory("ores", "§l§b💎 РУДА", "Слитки, драгоценности и минералы"),
            new SellCategory("blocks", "§l§7▣ БЛОКИ", "Камень, песок, дерево и стройматериалы"),
            new SellCategory("farm", "§l§a❀ ФЕРМА", "Урожай, семена и растения"),
            new SellCategory("drops", "§l§c✦ ДОБЫЧА", "Трофеи с мобов и полезные материалы")
    );

    private String categoryOf(String id) {
        return switch (id) {
            case "260", "297", "320", "363", "364", "349", "350", "393", "400" -> "food";
            case "263", "265", "266", "264", "388", "331", "351", "406" -> "ores";
            case "1", "4", "5", "12", "13", "17", "45", "49", "82", "87", "121" -> "blocks";
            case "295", "296", "391", "392", "338", "360", "361", "362", "86", "103" -> "farm";
            case "287", "288", "289", "334", "341", "352", "367", "375" -> "drops";
            default -> "other";
        };
    }

    private int sellableCount(Player player, String category) {
        int count = 0;
        for (Item item : player.getInventory().getContents().values()) {
            if (priceOf(item) <= 0) continue;
            if (category == null || category.equals(categoryOf(String.valueOf(item.getId())))) count += item.getCount();
        }
        return count;
    }

    private long sellableValue(Player player, String category) {
        long total = 0L;
        for (Item item : player.getInventory().getContents().values()) {
            int price = priceOf(item);
            if (price <= 0) continue;
            if (category == null || category.equals(categoryOf(String.valueOf(item.getId())))) {
                total += (long) price * item.getCount();
            }
        }
        return total;
    }

    private void seller(CommandSender sender) {
        if (sender instanceof Player player) openSeller(player);
        else sender.sendMessage(PREFIX + "§cСкупщик доступен только игроку.");
    }

    private void openSeller(Player player) {
        FormWindowSimple form = new FormWindowSimple(
                "§l§0✦ NEXORA · СКУПЩИК",
                "§8━━━━━━━━━━━━━━━━━━━━\n§7Добро пожаловать на торговую площадь!\n"
                        + "§7Выбирай отдел или продавай весь подходящий лут сразу.\n§8━━━━━━━━━━━━━━━━━━━━\n"
                        + "§eТвой баланс: §f" + balance(player.getUniqueId()) + " монет\n"
                        + "§aМожно продать: §f" + sellableCount(player, null) + " шт.\n"
                        + "§6Оценка инвентаря: §f" + sellableValue(player, null) + " монет\n\n"
                        + "§8▣ §7Выбери отдел сундука:");
        for (SellCategory category : SELL_CATEGORIES) {
            form.addButton(new ElementButton(category.title() + "\n§8" + category.description()
                    + "\n§eВ наличии: §f" + sellableCount(player, category.id()) + " шт."));
        }
        form.addButton(new ElementButton("§l§a✦ ПРОДАТЬ ВСЁ\n§8Весь подходящий лут · "
                + sellableCount(player, null) + " шт. · " + sellableValue(player, null) + " монет"));
        form.addButton(new ElementButton("§7Прайс-лист"));
        form.addButton(new ElementButton("§8Закрыть"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int index = form.getResponse().getClickedButtonId();
            if (index >= 0 && index < SELL_CATEGORIES.size()) openSellCategory(player, SELL_CATEGORIES.get(index));
            else if (index == SELL_CATEGORIES.size()) {
                sellAll(player);
                openSeller(player);
            } else if (index == SELL_CATEGORIES.size() + 1) openPricesMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openSellCategory(Player player, SellCategory category) {
        ConfigSection section = plugin.getConfig().getSection("economy.sell-prices");
        List<String> ids = new ArrayList<>();
        FormWindowSimple form = new FormWindowSimple("§l§0СКУПЩИК · " + category.title(),
                "§8━━━━━━━━━━━━━━━━━━━━\n§7" + category.description() + "\n"
                        + "§7Весь отдел: §f" + sellableCount(player, category.id()) + " шт.\n"
                        + "§7Общая цена: §e" + sellableValue(player, category.id()) + " монет\n"
                        + "§8Нажми на товар, чтобы продать все его стаки.");
        for (String id : section.getKeys(false)) {
            if (!category.id().equals(categoryOf(id))) continue;
            int count = countInInventory(player, Integer.parseInt(id));
            int price = plugin.getConfig().getInt("economy.sell-prices." + id + ".price", 0);
            ids.add(id);
            form.addButton(new ElementButton("§f" + plugin.getConfig().getString("economy.sell-prices." + id + ".name", id)
                    + "\n§7У тебя: §f" + count + " шт. §8| §e" + price + " мон./шт."));
        }
        form.addButton(new ElementButton("§l§a✦ ПРОДАТЬ ВЕСЬ ОТДЕЛ\n§8Получить "
                + sellableValue(player, category.id()) + " монет"));
        form.addButton(new ElementButton("§c🟥 СТЕКЛО · НАЗАД К ОТДЕЛАМ"));
        form.addButton(new ElementButton("§8⬛ ЗАКРЫТЬ МЕНЮ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int index = form.getResponse().getClickedButtonId();
            if (index >= 0 && index < ids.size()) sellItemId(player, Integer.parseInt(ids.get(index)), category);
            else if (index == ids.size()) {
                sellCategory(player, category.id());
                openSellCategory(player, category);
            } else if (index == ids.size() + 1) openSeller(player);
            else openSeller(player);
        }));
        player.showFormWindow(form);
    }

    private int countInInventory(Player player, int itemId) {
        int count = 0;
        for (Item item : player.getInventory().getContents().values()) {
            if (item.getId() == itemId && priceOf(item) > 0) count += item.getCount();
        }
        return count;
    }

    private void sellItemId(Player player, int itemId, SellCategory category) {
        long total = 0L;
        int count = 0;
        int price = plugin.getConfig().getInt("economy.sell-prices." + itemId + ".price", 0);
        if (price <= 0) return;
        Map<Integer, Item> contents = new HashMap<>(player.getInventory().getContents());
        for (Map.Entry<Integer, Item> slot : contents.entrySet()) {
            Item item = slot.getValue();
            if (item.getId() != itemId || priceOf(item) <= 0) continue;
            count += item.getCount();
            total += (long) price * item.getCount();
            player.getInventory().clear(slot.getKey());
        }
        if (total > 0) {
            deposit(player.getUniqueId(), total);
            player.sendMessage(PREFIX + "§aСкупщик купил §f" + count + " шт. §aза " + money(total));
        } else player.sendMessage(PREFIX + "§cУ тебя нет этого товара.");
        openSellCategory(player, category);
    }

    private void sellCategory(Player player, String category) {
        long total = 0L;
        int count = 0;
        Map<Integer, Item> contents = new HashMap<>(player.getInventory().getContents());
        for (Map.Entry<Integer, Item> slot : contents.entrySet()) {
            Item item = slot.getValue();
            if (priceOf(item) <= 0 || !category.equals(categoryOf(String.valueOf(item.getId())))) continue;
            total += (long) priceOf(item) * item.getCount();
            count += item.getCount();
            player.getInventory().clear(slot.getKey());
        }
        if (total > 0) {
            deposit(player.getUniqueId(), total);
            player.sendMessage(PREFIX + "§aОтдел скупщика принял §f" + count + " шт. §aза " + money(total));
        } else player.sendMessage(PREFIX + "§cВ этом отделе нечего продавать.");
    }

    private void sellHand(Player player, String[] args) {
        Item hand = player.getInventory().getItemInHand();
        int price = priceOf(hand);
        if (price <= 0) {
            player.sendMessage(PREFIX + "§cЭтот предмет нельзя продать. Список цен: §e/prices");
            return;
        }
        int count = hand.getCount();
        if (args.length > 1) {
            long wanted = NEXORASurvival.parseAmount(args[1], false);
            if (wanted < 0) {
                player.sendMessage(PREFIX + "§cКоличество должно быть целым положительным числом.");
                return;
            }
            count = (int) Math.min(count, wanted);
        }
        if (count >= hand.getCount()) {
            player.getInventory().setItemInHand(Item.get(0, 0, 0));
        } else {
            hand.setCount(hand.getCount() - count);
            player.getInventory().setItemInHand(hand);
        }
        long total = (long) price * count;
        deposit(player.getUniqueId(), total);
        player.sendMessage(PREFIX + "§aПродано §f" + count + " шт. §aза " + money(total));
    }

    private void sellAll(Player player) {
        long total = 0;
        int items = 0;
        Map<Integer, Item> contents = new HashMap<>(player.getInventory().getContents());
        for (Map.Entry<Integer, Item> slot : contents.entrySet()) {
            int price = priceOf(slot.getValue());
            if (price <= 0) continue;
            total += (long) price * slot.getValue().getCount();
            items += slot.getValue().getCount();
            player.getInventory().clear(slot.getKey());
        }
        if (total <= 0) {
            player.sendMessage(PREFIX + "§cВ инвентаре нет ничего, что можно продать. Список цен: §e/prices");
            return;
        }
        deposit(player.getUniqueId(), total);
        player.sendMessage(PREFIX + "§aПродано предметов: §f" + items + " §aза " + money(total));
    }

    private void openPricesMenu(Player player) {
        ConfigSection section = plugin.getConfig().getSection("economy.sell-prices");
        StringBuilder text = new StringBuilder("§7Цена за 1 штуку:\n");
        for (String id : section.getKeys(false)) {
            text.append("\n§f").append(plugin.getConfig().getString("economy.sell-prices." + id + ".name", id))
                    .append(" §8— ").append(money(plugin.getConfig().getInt("economy.sell-prices." + id + ".price", 0)));
        }
        FormWindowSimple form = new FormWindowSimple("§l§0ЦЕНЫ СКУПКИ", text.toString());
        form.addButton(new ElementButton("§c🟥 СТЕКЛО · НАЗАД К СКУПЩИКУ"));
        form.addButton(new ElementButton("§8⬛ ЗАКРЫТЬ МЕНЮ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            if (form.getResponse().getClickedButtonId() == 0) openSeller(player);
        }));
        player.showFormWindow(form);
    }

    private void prices(CommandSender sender) {
        ConfigSection section = plugin.getConfig().getSection("economy.sell-prices");
        StringBuilder text = new StringBuilder("§7Цена за 1 штуку:\n");
        for (String id : section.getKeys(false)) {
            text.append("\n§f").append(plugin.getConfig().getString("economy.sell-prices." + id + ".name", id))
                    .append(" §8— ").append(money(plugin.getConfig().getInt("economy.sell-prices." + id + ".price", 0)));
        }
        text.append("\n\n§8Продать: §e/sell hand §8или §e/sell all");
        if (sender instanceof Player player) {
            openPricesMenu(player);
        } else {
            sender.sendMessage(text.toString());
        }
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
