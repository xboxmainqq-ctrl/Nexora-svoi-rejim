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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/**
 * Starter equipment progression. Items are vanilla Bedrock-compatible items with server-side
 * identifiers in lore; combat bonuses are applied by events, so clients need no extra RP.
 */
final class GearModule implements Listener {
    private record Gear(String id, String name, String description, int itemId, int price,
                        double damageBonus, double defensePerPiece, boolean armorSet) { }

    private static final String MARKER = "§0NEXORA_GEAR:";
    private static final List<Gear> WEAPONS = List.of(
            new Gear("rust_blade", "§7Ржавая сабля", "Начальное оружие · +1.0 урона", 268, 250, 1.0, 0, false),
            new Gear("stone_cleaver", "§fКаменный тесак", "Тяжёлый клинок · +1.8 урона", 272, 650, 1.8, 0, false),
            new Gear("iron_fang", "§bЖелезный клык", "Быстрый меч · +2.5 урона", 267, 1400, 2.5, 0, false),
            new Gear("gold_dagger", "§6Золотой кинжал", "Лёгкое оружие · +2.2 урона", 283, 1900, 2.2, 0, false),
            new Gear("diamond_edge", "§dАлмазный разрез", "Редкий клинок · +3.5 урона", 276, 4500, 3.5, 0, false),
            new Gear("war_axe", "§cТопор берсерка", "Тяжёлый удар · +4.0 урона", 258, 5200, 4.0, 0, false),
            new Gear("hunter_bow", "§aЛук охотника", "Охотничье оружие · +2.0 урона", 261, 3200, 2.0, 0, false),
            new Gear("arcane_staff", "§5Посох искр", "Мистический фокус · +3.0 урона",  blazeRodId(), 6000, 3.0, 0, false)
    );

    private static final List<Gear> ARMOR = List.of(
            new Gear("scout", "§7Комплект разведчика", "Кожа · защита каждой части 3%", 298, 900, 0, 0.03, true),
            new Gear("guard", "§fКомплект стража", "Кольчуга · защита каждой части 4%", 302, 2600, 0, 0.04, true),
            new Gear("knight", "§bКомплект рыцаря", "Железо · защита каждой части 5%", 306, 5200, 0, 0.05, true),
            new Gear("raider", "§6Комплект налётчика", "Золото · защита каждой части 4.5%", 314, 6800, 0, 0.045, true),
            new Gear("dragon", "§dКомплект дракона", "Алмаз · защита каждой части 7%", 310, 12500, 0, 0.07, true)
    );

    private final NEXORASurvival plugin;

    GearModule(NEXORASurvival plugin) {
        this.plugin = plugin;
    }

    boolean handle(CommandSender sender, String command, String[] args) {
        if (!command.equals("arsenal") && !command.equals("gear")) return false;
        if (command.equals("gear")) {
            if (!NEXORASurvival.isAdmin(sender)) {
                sender.sendMessage(PREFIX + "§cНедостаточно прав.");
                return true;
            }
            if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
                sender.sendMessage(PREFIX + "§e/gear give <игрок> <id>");
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null || args.length < 3) {
                sender.sendMessage(PREFIX + "§cИгрок не найден. Используй /gear give <игрок> <id>");
                return true;
            }
            Gear gear = find(args[2]);
            if (gear == null) {
                sender.sendMessage(PREFIX + "§cНеизвестный предмет. Открой /arsenal и посмотри ID в описании.");
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

    private static int blazeRodId() { return 369; }

    private Gear find(String id) {
        for (Gear gear : WEAPONS) if (gear.id().equalsIgnoreCase(id)) return gear;
        for (Gear gear : ARMOR) if (gear.id().equalsIgnoreCase(id)) return gear;
        return null;
    }

    private void openRoot(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0✦ NEXORA · АРСЕНАЛ",
                "§8━━━━━━━━━━━━━━━━━━\n§7Экипировка для PvE и PvP\n"
                        + "§7Оружие усиливает удары, броня снижает входящий урон.\n"
                        + "§8━━━━━━━━━━━━━━━━━━\n§eБаланс: §f" + plugin.economy().money(plugin.economy().balance(player.getUniqueId()))
                        + "\n§7Выбери категорию:");
        form.addButton(new ElementButton("§l§c⚔ ОРУЖИЕ\n§8Клинки, топоры, луки и посохи"));
        form.addButton(new ElementButton("§l§b✥ БРОНЯ\n§8Комплекты с защитой"));
        form.addButton(new ElementButton("§7Закрыть"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            if (form.getResponse().getClickedButtonId() == 0) openList(player, WEAPONS, false);
            if (form.getResponse().getClickedButtonId() == 1) openList(player, ARMOR, true);
        }));
        player.showFormWindow(form);
    }

    private void openList(Player player, List<Gear> items, boolean armor) {
        FormWindowSimple form = new FormWindowSimple(
                armor ? "§l§0✥ КОЛЛЕКЦИЯ БРОНИ" : "§l§0⚔ КОЛЛЕКЦИЯ ОРУЖИЯ",
                "§7Выбери экипировку для покупки.\n§8Цены указаны в монетах.");
        for (Gear gear : items) {
            form.addButton(new ElementButton(gear.name() + "\n§8" + gear.description() + "\n§e" + gear.price() + " монет"));
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
        player.sendMessage(PREFIX + (armor ? "§7Выдан полный комплект из 4 частей." : "§7Оружие добавлено в инвентарь."));
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
            item.setLore("§7" + gear.description(), MARKER + gear.id());
            result.add(item);
            return result;
        }
        int[] ids = switch (gear.id()) {
            case "scout" -> new int[]{298, 299, 300, 301};
            case "guard" -> new int[]{302, 303, 304, 305};
            case "knight" -> new int[]{306, 307, 308, 309};
            case "raider" -> new int[]{314, 315, 316, 317};
            default -> new int[]{310, 311, 312, 313};
        };
        String[] names = {"Шлем", "Нагрудник", "Поножи", "Ботинки"};
        for (int i = 0; i < ids.length; i++) {
            Item item = Item.get(ids[i]);
            item.setCount(1);
            item.setCustomName(gear.name() + " §8· " + names[i]);
            item.setLore("§7" + gear.description(), MARKER + gear.id());
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
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.canBeReducedByArmor()) return;
        double reduction = 0;
        for (Item piece : player.getInventory().getArmorContents()) {
            Gear gear = find(marker(piece));
            if (gear != null && gear.armorSet()) reduction += gear.defensePerPiece();
        }
        if (reduction <= 0) return;
        reduction = Math.min(0.35, reduction);
        float base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        event.setDamage(event.getDamage(EntityDamageEvent.DamageModifier.ARMOR) - (float) (base * reduction),
                EntityDamageEvent.DamageModifier.ARMOR);
    }
}
