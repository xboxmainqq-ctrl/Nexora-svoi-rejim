package ru.nexora.quests;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowModal;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NEXORAQuests extends PluginBase implements Listener {
    private Config data;
    private static final String[] DAILY = {"stone", "wood", "hunt", "play"};
    private static final String[] WEEKLY = {"miner", "hunter", "adventurer"};

    @Override public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку NEXORA_Quests.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        data = new Config(new File(getDataFolder(), "players.yml"), Config.YAML);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleRepeatingTask(this, this::tickPlaytime, 20 * 60);
        getLogger().info("NEXORA Quests 1.0.0 загружен. Ежедневные и недельные задания активны.");
    }

    @Override public void onDisable() { if (data != null) data.save(); }

    private String path(Player p) { return "players." + p.getUniqueId(); }
    private String dailyPath(Player p) { return path(p) + ".daily"; }
    private String weeklyPath(Player p) { return path(p) + ".weekly"; }
    private String today() { return LocalDate.now().toString(); }
    private String week() {
        LocalDate d = LocalDate.now();
        WeekFields wf = WeekFields.ISO;
        return d.get(wf.weekBasedYear()) + "-W" + d.get(wf.weekOfWeekBasedYear());
    }
    private void prepare(Player p) {
        String root = path(p);
        if (!data.getString(root + ".daily-date", "").equals(today())) {
            data.set(root + ".daily-date", today());
            for (String id : DAILY) data.set(dailyPath(p) + "." + id, 0);
            data.set(dailyPath(p) + ".claimed", "");
        }
        if (!data.getString(root + ".weekly-date", "").equals(week())) {
            data.set(root + ".weekly-date", week());
            for (String id : WEEKLY) data.set(weeklyPath(p) + "." + id, 0);
            data.set(weeklyPath(p) + ".claimed", "");
        }
    }
    private int get(Player p, String period, String id) {
        prepare(p);
        return data.getInt((period.equals("daily") ? dailyPath(p) : weeklyPath(p)) + "." + id, 0);
    }
    private void add(Player p, String period, String id, int amount) {
        prepare(p);
        String base = (period.equals("daily") ? dailyPath(p) : weeklyPath(p)) + "." + id;
        int max = target(period, id);
        data.set(base, Math.min(max, data.getInt(base, 0) + amount));
    }
    private int target(String period, String id) {
        return switch (id) {
            case "stone" -> 32; case "wood" -> 16; case "hunt" -> 5; case "play" -> 30;
            case "miner" -> 250; case "hunter" -> 50; case "adventurer" -> 180;
            default -> 1;
        };
    }
    private String title(String id) {
        return switch (id) {
            case "stone" -> "§f⛏ Каменный промысел";
            case "wood" -> "§a♣ Лесоруб";
            case "hunt" -> "§c⚔ Охотник";
            case "play" -> "§b◷ Время приключений";
            case "miner" -> "§6◆ Великий шахтёр";
            case "hunter" -> "§c✦ Истребитель";
            case "adventurer" -> "§d✧ Путешественник";
            default -> id;
        };
    }
    private String description(String id) {
        return switch (id) {
            case "stone" -> "Добудь 32 каменных блока.";
            case "wood" -> "Сруби 16 блоков древесины.";
            case "hunt" -> "Победи 5 любых монстров.";
            case "play" -> "Проведи в игре 30 минут.";
            case "miner" -> "Добудь 250 каменных блоков за неделю.";
            case "hunter" -> "Победи 50 монстров за неделю.";
            case "adventurer" -> "Проведи в игре 180 минут за неделю.";
            default -> "";
        };
    }
    private int rewardTokens(String id) {
        return switch (id) { case "stone" -> 15; case "wood" -> 15; case "hunt" -> 25; case "play" -> 20;
            case "miner" -> 100; case "hunter" -> 150; case "adventurer" -> 120; default -> 0; };
    }
    private int rewardXp(String id) {
        return switch (id) { case "stone", "wood" -> 30; case "hunt", "play" -> 45;
            case "miner" -> 150; case "hunter" -> 200; case "adventurer" -> 175; default -> 0; };
    }
    private boolean claimed(Player p, String period, String id) {
        String key = period.equals("daily") ? dailyPath(p) + ".claimed" : weeklyPath(p) + ".claimed";
        String value = data.getString(key, "");
        return List.of(value.split(",")).contains(id);
    }
    private void claim(Player p, String period, String id) {
        prepare(p);
        if (get(p, period, id) < target(period, id) || claimed(p, period, id)) return;
        String key = period.equals("daily") ? dailyPath(p) + ".claimed" : weeklyPath(p) + ".claimed";
        String old = data.getString(key, "");
        data.set(key, old.isEmpty() ? id : old + "," + id);
        String root = path(p);
        data.set(root + ".tokens", data.getInt(root + ".tokens", 0) + rewardTokens(id));
        data.save();
        p.addExperience(rewardXp(id));
        p.sendMessage("§6✦ NEXORA §8» §aЗадание выполнено!");
        p.sendMessage("§7Награда: §e+" + rewardTokens(id) + " жетонов §8и §b+" + rewardXp(id) + " XP§7.");
        p.sendMessage("§8Жетоны можно потратить в магазине: /quests → Магазин.");
    }
    private String bar(int value, int max) {
        int filled = Math.min(10, (int)Math.floor(value * 10.0 / max));
        return "§a" + "▰".repeat(filled) + "§8" + "▱".repeat(10 - filled);
    }
    private void openJournal(Player p) {
        prepare(p);
        String root = path(p);
        int tokens = data.getInt(root + ".tokens", 0);
        int done = 0;
        for (String id : DAILY) if (claimed(p, "daily", id)) done++;
        FormWindowSimple form = new FormWindowSimple("§l§0✦ NEXORA · ЖУРНАЛ ЗАДАНИЙ",
                "§8━━━━━━━━━━━━━━━━━━\n§bПУТЬ ПРИКЛЮЧЕНИЙ\n"
                + "§7Ежедневные и недельные испытания\n\n"
                + "§e✦ Жетоны: §f" + tokens + "    §a✔ Сегодня: §f" + done + "/4\n"
                + "§8Обновление ежедневных заданий: §f00:00\n"
                + "§8━━━━━━━━━━━━━━━━━━\n§7Выбери раздел:");
        form.addButton(new ElementButton("§l§e☀ ЕЖЕДНЕВНЫЕ ЗАДАНИЯ\n§8Новые испытания каждый день"));
        form.addButton(new ElementButton("§l§6♛ НЕДЕЛЬНЫЕ ИСПЫТАНИЯ\n§8Большие цели и награды"));
        form.addButton(new ElementButton("§l§b✦ МОИ ЖЕТОНЫ\n§8Баланс и информация"));
        form.addButton(new ElementButton("§l§d✚ МАГАЗИН\n§8Яйца, сферы и припасы за жетоны"));
        form.addButton(new ElementButton("§l§7✕ ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            switch (form.getResponse().getClickedButtonId()) {
                case 0 -> openList(p, "daily");
                case 1 -> openList(p, "weekly");
                case 2 -> openTokens(p);
                case 3 -> openShop(p);
                default -> { }
            }
        }));
        p.showFormWindow(form);
    }

    // ---- Token shop ----

    /** kind: "egg" (arg = rarity id), "orb" (amount = spheres), "wheat" (amount = items). */
    private record ShopItem(String title, String info, int price, String kind, String arg, int amount) { }

    private static final ShopItem[] SHOP = {
            new ShopItem("§7Яйцо: Обычное", "Случайный питомец обычной редкости.", 60, "egg", "common", 1),
            new ShopItem("§aЯйцо: Необычное", "Случайный питомец необычной редкости.", 120, "egg", "uncommon", 1),
            new ShopItem("§bЯйцо: Редкое", "Случайный питомец редкой редкости.", 300, "egg", "rare", 1),
            new ShopItem("§5Яйцо: Эпическое", "Случайный питомец эпической редкости.", 700, "egg", "epic", 1),
            new ShopItem("§6Яйцо: Легендарное", "Случайный питомец легендарной редкости.", 1800, "egg", "legendary", 1),
            new ShopItem("§bЛовчие сферы ×3", "Три сферы, чтобы приручать существ в мире.", 80, "orb", "", 3),
            new ShopItem("§eПшеница ×16", "Корм для тренировки питомцев (25 опыта за штуку).", 20, "wheat", "", 16),
    };

    private void openShop(Player p) {
        int tokens = data.getInt(path(p) + ".tokens", 0);
        FormWindowSimple form = new FormWindowSimple("§l§0✚ МАГАЗИН NEXORA",
                "§e✦ Твои жетоны: §f" + tokens + "\n§8Жетоны выдаются за ежедневные и недельные задания.\n\n§7Выбери товар:");
        for (ShopItem item : SHOP) {
            form.addButton(new ElementButton(item.title() + "\n§8" + item.price() + " жетонов"
                    + (tokens >= item.price() ? "" : " §c(не хватает)")));
        }
        form.addButton(new ElementButton("§7← НАЗАД В ЖУРНАЛ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < SHOP.length) confirmPurchase(p, SHOP[selected]); else openJournal(p);
        }));
        p.showFormWindow(form);
    }

    private void confirmPurchase(Player p, ShopItem item) {
        int tokens = data.getInt(path(p) + ".tokens", 0);
        FormWindowModal form = new FormWindowModal("§l§0ПОКУПКА",
                item.title() + "\n§7" + item.info()
                        + "\n\n§fЦена: §e" + item.price() + " жетонов\n§fУ тебя: §e" + tokens + " жетонов",
                "§aКУПИТЬ", "§7НАЗАД");
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            if (form.getResponse().getClickedButtonId() == 0) buy(p, item);
            openShop(p);
        }));
        p.showFormWindow(form);
    }

    private void buy(Player p, ShopItem item) {
        String key = path(p) + ".tokens";
        int tokens = data.getInt(key, 0);
        if (tokens < item.price()) {
            p.sendMessage("§cНе хватает жетонов: нужно " + item.price() + ", у тебя " + tokens + ".");
            return;
        }
        // Tokens are charged only after the goods were really handed over.
        if (!deliver(p, item)) return;
        data.set(key, tokens - item.price());
        data.save();
        p.sendMessage("§6✦ NEXORA §8» §aПокупка: §f" + item.title() + " §8(−" + item.price() + " жетонов)");
    }

    private boolean deliver(Player p, ShopItem item) {
        switch (item.kind()) {
            case "wheat" -> {
                Item wheat = Item.get(296, 0, item.amount());
                if (!p.getInventory().canAddItem(wheat)) {
                    p.sendMessage("§cНе хватает места в инвентаре. Жетоны не списаны.");
                    return false;
                }
                p.getInventory().addItem(wheat);
                return true;
            }
            case "egg" -> { return callBeasts(p, "giveShopEgg", String.class, item.arg()); }
            case "orb" -> { return callBeasts(p, "giveShopOrbs", int.class, item.amount()); }
            default -> { return false; }
        }
    }

    /** Calls a NEXORA_Beasts API method by reflection; it returns false when the goods did not fit. */
    private boolean callBeasts(Player p, String method, Class<?> argType, Object arg) {
        var beasts = getServer().getPluginManager().getPlugin("NEXORA_Beasts");
        if (beasts == null || !beasts.isEnabled()) {
            p.sendMessage("§cСистема питомцев сейчас недоступна. Жетоны не списаны.");
            return false;
        }
        try {
            Object result = beasts.getClass().getMethod(method, Player.class, argType).invoke(beasts, p, arg);
            if (Boolean.TRUE.equals(result)) return true;
            p.sendMessage("§cНе хватает места в инвентаре. Жетоны не списаны.");
            return false;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getLogger().warning("Магазин: не удалось выдать товар через NEXORA_Beasts: " + ex);
            p.sendMessage("§cНе удалось выдать товар. Жетоны не списаны.");
            return false;
        }
    }
    private void openList(Player p, String period) {
        prepare(p);
        String[] ids = period.equals("daily") ? DAILY : WEEKLY;
        String heading = period.equals("daily") ? "§l§0☀ ЕЖЕДНЕВНЫЕ ЗАДАНИЯ" : "§l§0♛ НЕДЕЛЬНЫЕ ИСПЫТАНИЯ";
        FormWindowSimple form = new FormWindowSimple(heading,
                "§7Выполняй цели, забирай награды и развивайся.\n§8Нажми на задание для подробностей.");
        for (String id : ids) {
            int value = get(p, period, id), max = target(period, id);
            String status = claimed(p, period, id) ? "§a✓ ПОЛУЧЕНО" : value >= max ? "§e★ ГОТОВО К СБОРУ" : "§7В ПРОЦЕССЕ";
            form.addButton(new ElementButton(title(id) + "\n" + bar(value, max) + " §f" + value + "/" + max + "  " + status));
        }
        form.addButton(new ElementButton("§7← НАЗАД В ЖУРНАЛ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < ids.length) openQuest(p, period, ids[selected]); else openJournal(p);
        }));
        p.showFormWindow(form);
    }
    private void openQuest(Player p, String period, String id) {
        int value = get(p, period, id), max = target(period, id);
        boolean isClaimed = claimed(p, period, id), complete = value >= max;
        FormWindowSimple form = new FormWindowSimple("§l§0✦ КАРТОЧКА ЗАДАНИЯ",
                title(id) + "\n\n§7" + description(id) + "\n\n"
                + "§8━━━━━━━━━━━━━━━━━━\n§fПРОГРЕСС\n" + bar(value, max) + " §b" + value + "§7/§f" + max
                + "\n\n§fНАГРАДА\n§e✦ " + rewardTokens(id) + " жетонов\n§b✧ " + rewardXp(id) + " опыта"
                + "\n\n" + (isClaimed ? "§aНаграда уже получена." : complete ? "§eЦель выполнена! Забери награду." : "§7Продолжай, у тебя всё получится!")
                + "\n§8━━━━━━━━━━━━━━━━━━");
        if (complete && !isClaimed) form.addButton(new ElementButton("§l§a✦ ЗАБРАТЬ НАГРАДУ"));
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (complete && !isClaimed && selected == 0) {
                claim(p, period, id);
                openList(p, period);
            } else openList(p, period);
        }));
        p.showFormWindow(form);
    }
    private void openTokens(Player p) {
        int tokens = data.getInt(path(p) + ".tokens", 0);
        FormWindowSimple form = new FormWindowSimple("§l§0✦ КОШЕЛЁК ПРИКЛЮЧЕНЦА",
                "§e✦ ТВОИ ЖЕТОНЫ: §f" + tokens + "\n\n§7Жетоны выдаются за выполненные задания.\n"
                + "§8Трать их в магазине: яйца питомцев, ловчие сферы и корм.");
        form.addButton(new ElementButton("§l§d✚ В МАГАЗИН"));
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            if (form.getResponse().getClickedButtonId() == 0) openShop(p); else openJournal(p);
        }));
        p.showFormWindow(form);
    }
    private void tickPlaytime() {
        for (Player p : getServer().getOnlinePlayers().values()) {
            prepare(p);
            add(p, "daily", "play", 1);
            add(p, "weekly", "adventurer", 1);
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { prepare(e.getPlayer()); data.save(); }
    @EventHandler public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        int id = e.getBlock().getId();
        if (id == 1 || id ==  stoneId()) { add(p, "daily", "stone", 1); add(p, "weekly", "miner", 1); }
        if (id == 17 || id == 162) add(p, "daily", "wood", 1);
    }
    private int stoneId() { return 1; }
    @EventHandler public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity().getLastDamageCause() instanceof cn.nukkit.event.entity.EntityDamageByEntityEvent damage
                && damage.getDamager() instanceof Player p) {
            add(p, "daily", "hunt", 1);
            add(p, "weekly", "hunter", 1);
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("nexora.quests.admin") && !sender.isOp()) {
                sender.sendMessage("§cНедостаточно прав."); return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("tokens")) {
                Player target = getServer().getPlayer(args[2]);
                if (target == null || args.length < 4) { sender.sendMessage("§c/quests admin tokens <игрок> <количество>"); return true; }
                try {
                    int amount = Integer.parseInt(args[3]);
                    if (amount < 0) throw new NumberFormatException();
                    String root = path(target);
                    data.set(root + ".tokens", data.getInt(root + ".tokens", 0) + amount);
                    data.save(); sender.sendMessage("§aВыдано жетонов: " + amount);
                } catch (NumberFormatException ex) { sender.sendMessage("§cКоличество должно быть положительным числом."); }
                return true;
            }
            sender.sendMessage("§e/quests admin tokens <игрок> <количество>"); return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("§cЭта команда доступна только игроку."); return true; }
        if (!p.hasPermission("nexora.quests.use")) { p.sendMessage("§cНет прав."); return true; }
        openJournal(p);
        return true;
    }
}