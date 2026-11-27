package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;
import com.nukkitx.fakeinventories.inventory.ChestFakeInventory;

import java.util.ArrayList;
import java.util.List;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** NEXORA custom equipment. Stats and perks are applied server-side. */
final class GearModule implements Listener {
    private record Gear(String id, String name, String description, int itemId, int price,
                        double damageBonus, double defensePerPiece, boolean armorSet,
                        String perkDescription) { }


    private record ShopItem(String name, String description, int itemId, int meta,
                            int count, int price) { }

    private static final List<ShopItem> FOOD = List.of(
            new ShopItem("§6Хлеб", "§7Еда на каждый день", 297, 0, 16, 45),
            new ShopItem("§6Яблоки", "§7Быстрый перекус", 260, 0, 16, 35),
            new ShopItem("§6Стейк", "§7Сытная еда", 364, 0, 16, 80),
            new ShopItem("§6Жареная свинина", "§7Сытная еда", 320, 0, 16, 75),
            new ShopItem("§6Жареная рыба", "§7Еда для путешествий", 350, 0, 16, 55),
            new ShopItem("§6Печёный картофель", "§7Простой запас еды", 393, 0, 16, 45),
            new ShopItem("§6Золотое яблоко", "§7Редкая еда для PvP", 322, 0, 2, 180),
            new ShopItem("§6Тыквенный пирог", "§7Сладкий запас еды", 400, 0, 8, 55)
    );
    private static final List<ShopItem> BLOCKS = List.of(
            new ShopItem("§7Булыжник", "§7Строительный блок", 4, 0, 64, 35),
            new ShopItem("§7Камень", "§7Строительный блок", 1, 0, 64, 45),
            new ShopItem("§6Дубовые доски", "§7Для строительства и крафта", 5, 0, 64, 55),
            new ShopItem("§6Дубовые брёвна", "§7Материал для строительства", 17, 0, 32, 65),
            new ShopItem("§fСтекло", "§7Для окон и декора", 20, 0, 32, 70),
            new ShopItem("§eПесок", "§7Строительство и плавка", 12, 0, 64, 35),
            new ShopItem("§7Гравий", "§7Строительный материал", 13, 0, 64, 35),
            new ShopItem("§5Обсидиан", "§7Прочный блок для баз", 49, 0, 16, 220),
            new ShopItem("§cНезерак", "§7Блок Нижнего мира", 87, 0, 32, 70),
            new ShopItem("§bБлок железа", "§7Хранение ресурсов",  blockIronId(), 0, 4, 240)
    );
    private static final List<ShopItem> FARMING = List.of(
            new ShopItem("§aСемена пшеницы", "§7Посадка пшеницы", 295, 0, 16, 20),
            new ShopItem("§aСемена тыквы", "§7Посадка тыквы", 361, 0, 8, 25),
            new ShopItem("§aСемена арбуза", "§7Посадка арбуза", 362, 0, 8, 25),
            new ShopItem("§aМорковь", "§7Для посадки и еды",  carrotsId(), 0, 16, 30),
            new ShopItem("§aКартофель", "§7Для посадки и еды",  potatoesId(), 0, 16, 30),
            new ShopItem("§aСахарный тростник", "§7Для фермы и крафта", 338, 0, 16, 35),
            new ShopItem("§aТыква", "§7Для фермы", 86, 0, 8, 30),
            new ShopItem("§aАрбуз", "§7Для фермы", 103, 0, 8, 35),
            new ShopItem("§aКостная мука", "§7Ускоряет рост растений", 351, 15, 16, 45),
            new ShopItem("§aВедро воды", "§7Для полива и ферм",  waterBucketId(), 0, 1, 90)
    );
    private static final List<ShopItem> MATERIALS = List.of(
            new ShopItem("§fЖелезный слиток", "§7Материал для крафта", 265, 0, 16, 110),
            new ShopItem("§6Золотой слиток", "§7Материал для крафта", 266, 0, 8, 100),
            new ShopItem("§bАлмаз", "§7Ценный ресурс", 264, 0, 4, 260),
            new ShopItem("§aИзумруд", "§7Ценный ресурс", 388, 0, 4, 160),
            new ShopItem("§8Уголь", "§7Топливо для печи", 263, 0, 32, 65),
            new ShopItem("§cРедстоун", "§7Для механизмов", 331, 0, 32, 75),
            new ShopItem("§fНить", "§7Для крафта", 287, 0, 16, 45),
            new ShopItem("§cПорох", "§7Для крафта", 289, 0, 16, 70),
            new ShopItem("§fКожа", "§7Для крафта", 334, 0, 16, 65),
            new ShopItem("§aСлизь", "§7Для механизмов", 341, 0, 8, 90)
    );

    private static int blockIronId() { return 42; }
    private static int carrotsId() { return  carrotsItemId(); }
    private static int carrotsItemId() { return  carrotItemId(); }
    private static int carrotItemId() { return  carrotLegacyId(); }
    private static int carrotLegacyId() { return 391; }
    private static int potatoesId() { return  potatoLegacyId(); }
    private static int potatoLegacyId() { return 392; }
    private static int waterBucketId() { return  bucketLegacyId(); }
    private static int bucketLegacyId() { return 326; }

    private static final String MARKER = "§0NEXORA_GEAR:";
    private static final List<Gear> WEAPONS = List.of(
            new Gear("rust_blade", "§7Ржавая сабля", "§7+1 урона · яд на 2 сек.", 268, 250, 1.0, 0, false, "Яд"),
            new Gear("stone_cleaver", "§fКаменный тесак", "§7+1.8 урона · замедление на 2 сек.", 272, 650, 1.8, 0, false, "Замедление"),
            new Gear("iron_fang", "§bЖелезный клык", "§7+2.5 урона · вампиризм 8%", 267, 1400, 2.5, 0, false, "Вампиризм"),
            new Gear("gold_dagger", "§6Золотой кинжал", "§7+2.2 урона · скорость на 2 сек.", 283, 1900, 2.2, 0, false, "Ускорение"),
            new Gear("diamond_edge", "§dАлмазный разрез", "§7+3.5 урона · слабость на 3 сек.", 276, 4500, 3.5, 0, false, "Ослабление"),
            new Gear("war_axe", "§cТопор берсерка", "§7+4 урона · поджог на 2 сек.", 258, 5200, 4.0, 0, false, "Поджог"),
            new Gear("hunter_bow", "§aЛук охотника", "§7+2 урона · замедление на 2 сек.", 261, 3200, 2.0, 0, false, "Охотничья ловушка"),
            new Gear("arcane_staff", "§5Посох искр", "§7+3 урона · отравление на 3 сек.", 369, 6000, 3.0, 0, false, "Магическое отравление")
    );

    // Every set is visually diamond armor; progression comes from server-side protection values.
    private static final List<Gear> ARMOR = List.of(
            new Gear("scout", "§7Комплект разведчика", "§7Алмазная броня · 3% защиты за часть · полный комплект: +4%", 310, 900, 0, 0.03, true, "Базовая защита"),
            new Gear("guard", "§fКомплект стража", "§7Алмазная броня · 4% защиты за часть · полный комплект: +5%", 310, 2600, 0, 0.04, true, "Усиленная защита"),
            new Gear("knight", "§bКомплект рыцаря", "§7Алмазная броня · 5% защиты за часть · полный комплект: +6%", 310, 5200, 0, 0.05, true, "Рыцарская защита"),
            new Gear("raider", "§6Комплект налётчика", "§7Алмазная броня · 6% защиты за часть · полный комплект: +7%", 310, 6800, 0, 0.06, true, "Защита налётчика"),
            new Gear("dragon", "§dКомплект дракона", "§7Алмазная броня · 7% защиты за часть · полный комплект: +8%", 310, 12500, 0, 0.07, true, "Драконья защита")
    );


    private static final List<Gear> MINING_TOOLS = List.of(
            new Gear("stone_pickaxe", "§7Каменная кирка", "§7Для добычи камня и руды", 274, 180, 0, 0, false, "Инструмент"),
            new Gear("iron_pickaxe", "§fЖелезная кирка", "§7Прочная кирка для шахты", 257, 650, 0, 0, false, "Инструмент"),
            new Gear("diamond_pickaxe", "§bАлмазная кирка", "§7Быстрая кирка для шахты", 278, 2400, 0, 0, false, "Инструмент")
    );
    private static final List<Gear> WOOD_TOOLS = List.of(
            new Gear("stone_axe", "§7Каменный топор", "§7Для рубки деревьев", 275, 180, 0, 0, false, "Инструмент"),
            new Gear("iron_axe", "§fЖелезный топор", "§7Прочный топор лесоруба", 258, 650, 0, 0, false, "Инструмент"),
            new Gear("diamond_axe", "§bАлмазный топор", "§7Быстрый топор лесоруба", 279, 2400, 0, 0, false, "Инструмент")
    );
    private static final List<Gear> DIGGING_TOOLS = List.of(
            new Gear("stone_shovel", "§7Каменная лопата", "§7Для земли, песка и гравия", 273, 120, 0, 0, false, "Инструмент"),
            new Gear("iron_shovel", "§fЖелезная лопата", "§7Прочная лопата", 256, 450, 0, 0, false, "Инструмент"),
            new Gear("diamond_shovel", "§bАлмазная лопата", "§7Быстрая лопата", 277, 1600, 0, 0, false, "Инструмент")
    );
    private static final List<Gear> FARM_TOOLS = List.of(
            new Gear("iron_hoe", "§fЖелезная мотыга", "§7Для обработки грядок", 292, 400, 0, 0, false, "Инструмент"),
            new Gear("diamond_hoe", "§bАлмазная мотыга", "§7Прочная мотыга для фермы", 293, 1200, 0, 0, false, "Инструмент")
    );
    private static final List<Gear> UTILITY_TOOLS = List.of(
            new Gear("shears", "§fНожницы", "§7Для шерсти и листвы", 359, 300, 0, 0, false, "Инструмент"),
            new Gear("fishing_rod", "§bУдочка", "§7Для рыбалки", 346, 350, 0, 0, false, "Инструмент"),
            new Gear("flint_steel", "§6Огниво", "§7Для розжига огня", 259, 250, 0, 0, false, "Инструмент")
    );

    private final NEXORASurvival plugin;

    GearModule(NEXORASurvival plugin) {
        this.plugin = plugin;
    }

    boolean handle(CommandSender sender, String command, String[] args) {
        if (!command.equals("shop") && !command.equals("gear")) return false;
        if (command.equals("gear")) {
            if (!NEXORASurvival.isAdmin(sender)) {
                sender.sendMessage(PREFIX + "§cНедостаточно прав.");
                return true;
            }
            if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
                sender.sendMessage(PREFIX + "§e/gear give <игрок> <id>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(PREFIX + "§cИгрок не найден.");
                return true;
            }
            Gear gear = find(args[2]);
            if (gear == null) {
                sender.sendMessage(PREFIX + "§cНеизвестный ID экипировки.");
                return true;
            }
            deliver(target, gear);
            sender.sendMessage(PREFIX + "§aВыдано: " + gear.name());
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cКоманда доступна только игроку.");
            return true;
        }
        openRoot(player);
        return true;
    }

    private Gear find(String id) {
        for (Gear gear : WEAPONS) if (gear.id().equalsIgnoreCase(id)) return gear;
        for (Gear gear : ARMOR) if (gear.id().equalsIgnoreCase(id)) return gear;
        for (List<Gear> group : List.of(MINING_TOOLS, WOOD_TOOLS, DIGGING_TOOLS, FARM_TOOLS, UTILITY_TOOLS))
            for (Gear gear : group) if (gear.id().equalsIgnoreCase(id)) return gear;
        return null;
    }



    private static final int[] SHOP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25
    };

    private Item menuIcon(int id, int meta, int count, String name, String... lore) {
        Item item = Item.get(id, meta, count);
        item.setCustomName(name);
        item.setLore(lore);
        return item;
    }

    private void openChestLater(Player player, Runnable action) {
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, action, 10);
    }

    private void openRoot(Player player) {
        ChestFakeInventory menu = new ChestFakeInventory(null, "§l§0NEXORA §8| §6МАГАЗИН");
        Item pane = menuIcon( stainedGlassPaneId(), 7, 1, "§8 ");
        for (int i = 0; i < 27; i++) menu.setItem(i, menuIcon(stainedGlassPaneId(), 7, 1, "§8 "));
        menu.setItem(4, menuIcon( goldIngotId(), 1, 1, "§6ТВОИ МОНЕТЫ",
                "§7Баланс: §e" + plugin.economy().balance(player.getUniqueId()),
                "§8Покупай предметы за монеты"));
        menu.setItem(10, menuIcon( 260, 0, 1, "§6ЕДА", "§7Хлеб, мясо, яблоки и другое", "§eНажми, чтобы открыть"));
        menu.setItem(11, menuIcon( 276, 0, 1, "§cОРУЖИЕ", "§7Мечи, топоры, луки и особое оружие", "§eНажми, чтобы открыть"));
        menu.setItem(12, menuIcon( 310, 0, 1, "§bБРОНЯ", "§7Комплекты защиты", "§eНажми, чтобы открыть"));
        menu.setItem(13, menuIcon( 278, 0, 1, "§7ИНСТРУМЕНТЫ", "§7Кирки, топоры, лопаты и другое", "§eНажми, чтобы открыть"));
        menu.setItem(14, menuIcon( 4, 0, 1, "§6БЛОКИ", "§7Строительные блоки и укрепления", "§eНажми, чтобы открыть"));
        menu.setItem(15, menuIcon( 295, 0, 1, "§aФЕРМА", "§7Семена, растения и инвентарь", "§eНажми, чтобы открыть"));
        menu.setItem(16, menuIcon( 264, 0, 1, "§dРЕСУРСЫ", "§7Руды и материалы для крафта", "§eНажми, чтобы открыть"));
        menu.setItem(22, menuIcon(  barrierId(), 0, 1, "§cЗакрыть магазин", "§7Нажми, чтобы закрыть"));
        menu.addListener(event -> {
            event.setCancelled();
            int slot = event.getAction().getSlot();
            if (slot < 10 || slot > 16) {
                if (slot == 22) player.removeWindow(menu);
                return;
            }
            player.removeWindow(menu);
            openChestLater(player, () -> {
                switch (slot) {
                    case 10 -> openShopItemCategory(player, FOOD, "§6ЕДА");
                    case 11 -> openShopGearCategory(player, WEAPONS, false);
                    case 12 -> openShopGearCategory(player, ARMOR, true);
                    case 13 -> openShopGearCategory(player, combineTools(), false);
                    case 14 -> openShopItemCategory(player, BLOCKS, "§6БЛОКИ");
                    case 15 -> openShopItemCategory(player, FARMING, "§aФЕРМА");
                    case 16 -> openShopItemCategory(player, MATERIALS, "§dРЕСУРСЫ");
                }
            });
        });
        player.addWindow(menu);
    }

    private void fillShopFrame(ChestFakeInventory menu) {
        for (int i = 0; i < 27; i++) menu.setItem(i, menuIcon(stainedGlassPaneId(), 7, 1, "§8 "));
    }

    private void openShopItemCategory(Player player, List<ShopItem> items, String title) {
        ChestFakeInventory menu = new ChestFakeInventory(null, "§l§0NEXORA §8| " + title);
        fillShopFrame(menu);
        menu.setItem(4, menuIcon(goldIngotId(), 1, 1, "§6ТВОИ МОНЕТЫ",
                "§7Баланс: §e" + plugin.economy().balance(player.getUniqueId())));
        for (int i = 0; i < items.size() && i < SHOP_SLOTS.length; i++) {
            ShopItem entry = items.get(i);
            menu.setItem(SHOP_SLOTS[i], menuIcon(entry.itemId(), entry.meta(), entry.count(),
                    entry.name(), entry.description(), "§7Количество: §f" + entry.count(),
                    "§7Цена: §6" + entry.price() + " монет", "§aНажми, чтобы купить"));
        }
        menu.setItem(18, menuIcon( arrowId(), 0, 1, "§e← Назад", "§7Вернуться в магазин"));
        menu.setItem(26, menuIcon(barrierId(), 0, 1, "§cЗакрыть"));
        menu.addListener(event -> {
            event.setCancelled();
            int slot = event.getAction().getSlot();
            int index = slotIndex(slot);
            if (slot != 18 && slot != 26 && (index < 0 || index >= items.size())) return;
            player.removeWindow(menu);
            if (slot == 26) return;
            openChestLater(player, () -> {
                if (slot == 18) openRoot(player);
                else purchaseShopItem(player, items.get(index), items, title);
            });
        });
        player.addWindow(menu);
    }

    private void openShopGearCategory(Player player, List<Gear> items, boolean armor) {
        ChestFakeInventory menu = new ChestFakeInventory(null, armor
                ? "§l§0NEXORA §8| §bБРОНЯ"
                : (items.size() > 0 && items.get(0).id().equals("rust_blade")
                    ? "§l§0NEXORA §8| §cОРУЖИЕ" : "§l§0NEXORA §8| §7ИНСТРУМЕНТЫ"));
        fillShopFrame(menu);
        menu.setItem(4, menuIcon(goldIngotId(), 1, 1, "§6ТВОИ МОНЕТЫ",
                "§7Баланс: §e" + plugin.economy().balance(player.getUniqueId())));
        for (int i = 0; i < items.size() && i < SHOP_SLOTS.length; i++) {
            Gear entry = items.get(i);
            int iconId = entry.armorSet() ? entry.itemId() : entry.itemId();
            menu.setItem(SHOP_SLOTS[i], menuIcon(iconId, 0, 1, entry.name(),
                    entry.description(), "§7Цена: §6" + entry.price() + " монет",
                    "§aНажми, чтобы купить"));
        }
        menu.setItem(18, menuIcon(arrowId(), 0, 1, "§e← Назад", "§7Вернуться в магазин"));
        menu.setItem(26, menuIcon(barrierId(), 0, 1, "§cЗакрыть"));
        menu.addListener(event -> {
            event.setCancelled();
            int slot = event.getAction().getSlot();
            int index = slotIndex(slot);
            if (slot != 18 && slot != 26 && (index < 0 || index >= items.size())) return;
            player.removeWindow(menu);
            if (slot == 26) return;
            openChestLater(player, () -> {
                if (slot == 18) openRoot(player);
                else purchase(player, items.get(index), items, armor);
            });
        });
        player.addWindow(menu);
    }

    private int slotIndex(int slot) {
        for (int i = 0; i < SHOP_SLOTS.length; i++) if (SHOP_SLOTS[i] == slot) return i;
        return -1;
    }

    private List<Gear> combineTools() {
        List<Gear> all = new ArrayList<>();
        all.addAll(MINING_TOOLS);
        all.addAll(WOOD_TOOLS);
        all.addAll(DIGGING_TOOLS);
        all.addAll(FARM_TOOLS);
        all.addAll(UTILITY_TOOLS);
        return all;
    }

    private static int stainedGlassPaneId() { return 160; }
    private static int goldIngotId() { return 266; }
    private static int barrierId() { return 166; }
    private static int arrowId() { return 262; }

    private void purchaseShopItem(Player player, ShopItem shopItem, List<ShopItem> list, String title) {
        long balance = plugin.economy().balance(player.getUniqueId());
        if (balance < shopItem.price()) {
            player.sendMessage(PREFIX + "§cНе хватает монет. Нужно §e" + shopItem.price()
                    + "§c, у тебя §e" + balance + "§c.");
            openShopItemCategory(player, list, title);
            return;
        }
        Item item = Item.get(shopItem.itemId(), shopItem.meta(), shopItem.count());
        if (!player.getInventory().canAddItem(item)) {
            player.sendMessage(PREFIX + "§cОсвободи место в инвентаре перед покупкой.");
            return;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), shopItem.price())) {
            player.sendMessage(PREFIX + "§cПокупка не прошла: баланс изменился.");
            return;
        }
        player.getInventory().addItem(item);
        player.sendMessage(PREFIX + "§aКуплено: " + shopItem.name() + " §7×" + shopItem.count()
                + " §8за §e" + shopItem.price() + " монет§a.");
        openShopItemCategory(player, list, title);
    }

    private void purchase(Player player, Gear gear, List<Gear> list, boolean armor) {
        long balance = plugin.economy().balance(player.getUniqueId());
        if (balance < gear.price()) {
            player.sendMessage(PREFIX + "§cНе хватает монет. Нужно §e" + gear.price() + "§c, у тебя §e" + balance + "§c.");
            openShopGearCategory(player, list, armor);
            return;
        }
        List<Item> items = createItems(gear);
        for (Item item : items) {
            if (!player.getInventory().canAddItem(item)) {
                player.sendMessage(PREFIX + "§cОсвободи место в инвентаре перед покупкой.");
                return;
            }
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), gear.price())) {
            player.sendMessage(PREFIX + "§cПокупка не прошла: баланс изменился.");
            return;
        }
        for (Item item : items) player.getInventory().addItem(item);
        player.sendMessage(PREFIX + "§aПокупка успешна: " + gear.name() + "§a.");
        player.sendMessage(PREFIX + (armor ? "§7Полный алмазный комплект выдан. Бонус полного комплекта активен только при ношении всех 4 частей." : (gear.perkDescription().equals("Инструмент") ? "§7Инструмент добавлен в инвентарь." : "§7Особый эффект оружия срабатывает при попадании.")));
        openShopGearCategory(player, list, armor);
    }

    private void deliver(Player player, Gear gear) {
        for (Item item : createItems(gear)) {
            if (player.getInventory().canAddItem(item)) player.getInventory().addItem(item);
            else player.getLevel().dropItem(player, item);
        }
    }

    private List<Item> createItems(Gear gear) {
        List<Item> result = new ArrayList<>();
        if (!gear.armorSet()) {
            Item item = Item.get(gear.itemId());
            item.setCount(1);
            item.setCustomName(gear.name());
            item.setLore("§7" + gear.description(), "§7Способность: " + gear.perkDescription(), MARKER + gear.id());
            result.add(item);
            return result;
        }
        // All five sets use diamond armor item IDs: helmet, chestplate, leggings, boots.
        int[] ids = {310, 311, 312, 313};
        String[] names = {"Шлем", "Нагрудник", "Поножи", "Ботинки"};
        for (int i = 0; i < ids.length; i++) {
            Item item = Item.get(ids[i]);
            item.setCount(1);
            item.setCustomName(gear.name() + " §8· " + names[i]);
            item.setLore(gear.description(), "§7Бонус полного комплекта включён при 4/4.", MARKER + gear.id());
            result.add(item);
        }
        return result;
    }

    private String marker(Item item) {
        if (item == null || item.isNull()) return "";
        for (String line : item.getLore()) {
            if (line != null && line.startsWith(MARKER)) return line.substring(MARKER.length());
        }
        return "";
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeaponHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        String id = marker(player.getInventory().getItemInHand());
        Gear gear = find(id);
        if (gear == null || gear.armorSet() || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        event.setDamage(event.getDamage() + (float) gear.damageBonus());
        Entity target = event.getEntity();
        switch (id) {
            case "rust_blade", "arcane_staff" ->
                    target.addEffect(Effect.getEffect(Effect.POISON).setDuration(id.equals("rust_blade") ? 40 : 60).setAmplifier(0));
            case "stone_cleaver", "hunter_bow" ->
                    target.addEffect(Effect.getEffect(Effect.SLOWNESS).setDuration(40).setAmplifier(0));
            case "iron_fang" -> player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + (float) (event.getDamage() * 0.08)));
            case "gold_dagger" ->
                    player.addEffect(Effect.getEffect(Effect.SPEED).setDuration(40).setAmplifier(0));
            case "diamond_edge" ->
                    target.addEffect(Effect.getEffect(Effect.WEAKNESS).setDuration(60).setAmplifier(0));
            case "war_axe" -> target.setOnFire(2);
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.canBeReducedByArmor()) return;
        String[] setIds = {"scout", "guard", "knight", "raider", "dragon"};
        for (String setId : setIds) {
            int count = 0;
            Gear set = find(setId);
            for (Item piece : player.getInventory().getArmorContents()) {
                if (setId.equals(marker(piece))) count++;
            }
            if (count == 0) continue;
            double reduction = count * set.defensePerPiece();
            if (count == 4) reduction += switch (setId) {
                case "scout" -> 0.04;
                case "guard" -> 0.05;
                case "knight" -> 0.06;
                case "raider" -> 0.07;
                case "dragon" -> 0.08;
                default -> 0;
            };
            reduction = Math.min(0.45, reduction);
            float armorModifier = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR);
            float base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
            event.setDamage(armorModifier - (float) (base * reduction), EntityDamageEvent.DamageModifier.ARMOR);
            break;
        }
    }
}
