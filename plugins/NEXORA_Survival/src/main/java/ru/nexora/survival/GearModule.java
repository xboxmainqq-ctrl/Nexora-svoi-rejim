package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.Item;
import cn.nukkit.potion.Effect;

import java.util.ArrayList;
import java.util.List;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** NEXORA custom equipment. Stats and perks are applied server-side. */
final class GearModule implements Listener {
    private record Gear(String id, String name, String description, int itemId, int price,
                        double damageBonus, double defensePerPiece, boolean armorSet,
                        String perkDescription) { }

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
        if (!command.equals("weapons") && !command.equals("gear")) return false;
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
                sender.sendMessage(PREFIX + "§cНеизвестный ID. Посмотри список в /weapons.");
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

    private void openRoot(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0✦ NEXORA · АРСЕНАЛ",
                "§8━━━━━━━━━━━━━━━━━━\n§7Экипировка для PvE и PvP\n"
                        + "§7Бонусы оружия срабатывают при попадании. Защита брони работает при получении урона.\n"
                        + "§8━━━━━━━━━━━━━━━━━━\n§eБаланс: §f" + plugin.economy().balance(player.getUniqueId())
                        + "\n§7Выбери категорию:");
        form.addButton(new ElementButton("§l§c⚔ ОРУЖИЕ\n§8Клинки, топоры, луки и посохи"));
        form.addButton(new ElementButton("§l§b✥ БРОНЯ\n§8Пять алмазных комплектов с разной защитой"));
        form.addButton(new ElementButton("§l§7⛏ ДОБЫЧА\n§8Кирки"));
        form.addButton(new ElementButton("§l§6⚒ ЛЕСОРУБ\n§8Топоры"));
        form.addButton(new ElementButton("§l§e▰ ЗЕМЛЕКОП\n§8Лопаты"));
        form.addButton(new ElementButton("§l§a♣ ФЕРМА\n§8Мотыги"));
        form.addButton(new ElementButton("§l§b✦ ИНСТРУМЕНТЫ\n§8Ножницы, удочка, огниво"));
        form.addButton(new ElementButton("§7Закрыть"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected == 0) openList(player, WEAPONS, false);
            if (selected == 1) openList(player, ARMOR, true);
            if (selected == 2) openList(player, MINING_TOOLS, false);
            if (selected == 3) openList(player, WOOD_TOOLS, false);
            if (selected == 4) openList(player, DIGGING_TOOLS, false);
            if (selected == 5) openList(player, FARM_TOOLS, false);
            if (selected == 6) openList(player, UTILITY_TOOLS, false);
        }));
        player.showFormWindow(form);
    }

    private void openList(Player player, List<Gear> items, boolean armor) {
        FormWindowSimple form = new FormWindowSimple(
                armor ? "§l§0✥ АЛМАЗНАЯ БРОНЯ" : "§l§0⚔ КОЛЛЕКЦИЯ ОРУЖИЯ",
                "§7Выбери предмет. Его указанный эффект действительно работает.\n§8Цены в монетах.");
        for (Gear gear : items) {
            form.addButton(new ElementButton(gear.name() + "\n" + gear.description() + "\n§e" + gear.price() + " монет"));
        }
        form.addButton(new ElementButton("§7← Назад"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int index = form.getResponse().getClickedButtonId();
            if (index >= 0 && index < items.size()) purchase(player, items.get(index), items, armor);
            else openRoot(player);
        }));
        player.showFormWindow(form);
    }

    private void purchase(Player player, Gear gear, List<Gear> list, boolean armor) {
        long balance = plugin.economy().balance(player.getUniqueId());
        if (balance < gear.price()) {
            player.sendMessage(PREFIX + "§cНе хватает монет. Нужно §e" + gear.price() + "§c, у тебя §e" + balance + "§c.");
            openList(player, list, armor);
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
        openList(player, list, armor);
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
