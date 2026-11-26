package ru.nexora.beasts;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEntityEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class NEXORABeasts extends PluginBase implements Listener {
    private static final String ORB_NAME = "§bЛовчая сфера NEXORA";
    private static final String EGG_NAME = "§6Яйцо NEXORA";
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final String[] RARITIES = {
            "common", "uncommon", "rare", "epic", "legendary",
            "mythic", "ancient", "divine", "forbidden", "absolute"
    };
    private static final String[] RARITY_NAMES = {
            "Обычное", "Необычное", "Редкое", "Эпическое", "Легендарное",
            "Мифическое", "Древнее", "Божественное", "Запретное", "Абсолютное"
    };
    private static final String[] RARITY_COLORS = {
            "§7", "§a", "§b", "§5", "§6", "§d", "§3", "§e", "§c", "§f"
    };
    private static final int[] WILD_RARITY_WEIGHTS = {500, 220, 130, 70, 35, 20, 12, 7, 4, 2};
    private static final String[] RANDOM_SPECIES = {
            "Wolf", "Fox", "Rabbit", "Cat", "Bee", "Axolotl", "Horse",
            "Panda", "PolarBear", "Ocelot", "Turtle", "Dolphin", "Parrot"
    };

    private Config data;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку NEXORA_Beasts.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        data = new Config(new File(getDataFolder(), "players.yml"), Config.YAML);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NEXORA_Beasts включён. Система коллекции, яиц и приручения готова.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "collection", "list" -> {
                if (requirePlayer(sender)) showCollection((Player) sender);
            }
            case "team" -> {
                if (requirePlayer(sender)) {
                    if (args.length == 1) showTeam((Player) sender);
                    else if (args.length == 3) setTeamSlot((Player) sender, args[1], args[2]);
                    else sender.sendMessage("§cИспользование: /beasts team <слот 1-3> <ID существа>");
                }
            }
            case "info" -> {
                if (!requirePlayer(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts info <ID>");
                else showBeastInfo((Player) sender, args[1]);
            }
            case "release" -> {
                if (!requirePlayer(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts release <ID>");
                else releaseBeast((Player) sender, args[1]);
            }
            case "giveorb" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts giveorb <игрок> [количество]");
                else giveOrb(sender, args);
            }
            case "giveegg" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 4) sender.sendMessage("§cИспользование: /beasts giveegg <игрок> <вид|random> <редкость>");
                else giveEgg(sender, args);
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getEntity();
        if (!(target instanceof EntityLiving) || target instanceof Player) return;

        Item hand = player.getInventory().getItemInHand();
        if (hand == null || !ORB_NAME.equals(hand.getCustomName())) return;

        event.setCancelled();
        String species = normalizeSpecies(target.getName());
        String rarity = rollWildRarity();
        String id = addBeast(player, species, rarity, "wild");
        consumeOne(player, hand);
        target.close();

        player.sendMessage("§aТы приручил существо!");
        player.sendMessage("§f" + prettySpecies(species) + " §7[" + rarityColor(rarity) + rarityName(rarity) + "§7]");
        player.sendMessage("§7ID существа: §f" + id + " §8| §7Коллекция: §f" + collectionSize(player));
    }

    @EventHandler
    public void onEggUse(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                && event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Item hand = player.getInventory().getItemInHand();
        if (hand == null || !EGG_NAME.equals(hand.getCustomName())) return;

        String species = "random";
        String rarity = "common";
        String[] lore = hand.getLore();
        if (lore != null) {
            for (String line : lore) {
                if (line.startsWith("Вид: ")) species = line.substring("Вид: ".length()).trim();
                if (line.startsWith("Редкость: ")) rarity = line.substring("Редкость: ".length()).trim().toLowerCase(Locale.ROOT);
            }
        }
        if (!isRarity(rarity)) rarity = "common";
        if (species.equalsIgnoreCase("random")) {
            species = RANDOM_SPECIES[ThreadLocalRandom.current().nextInt(RANDOM_SPECIES.length)];
        }

        event.setCancelled();
        String id = addBeast(player, normalizeSpecies(species), rarity, "egg");
        consumeOne(player, hand);
        player.sendMessage("§6Яйцо вылупилось!");
        player.sendMessage("§f" + prettySpecies(species) + " §7[" + rarityColor(rarity) + rarityName(rarity) + "§7]");
        player.sendMessage("§7Новое существо добавлено в коллекцию. ID: §f" + id);
    }

    private void showCollection(Player player) {
        Map<String, Object> entries = collection(player);
        player.sendMessage("§6§lКоллекция NEXORA §7(" + entries.size() + ")");
        if (entries.isEmpty()) {
            player.sendMessage("§7Коллекция пуста. Получи яйцо или приручи существо ловчей сферой.");
            return;
        }
        int shown = 0;
        for (String id : entries.keySet()) {
            String base = beastPath(player, id);
            String species = data.getString(base + ".species", "unknown");
            String rarity = data.getString(base + ".rarity", "common");
            int level = data.getInt(base + ".level", 1);
            player.sendMessage("§f" + id + " §8| " + rarityColor(rarity) + prettySpecies(species)
                    + " §7[" + rarityName(rarity) + "] §8Ур. " + level);
            if (++shown >= 30) {
                if (entries.size() > shown) player.sendMessage("§7Показаны первые 30. Используй /beasts info <ID>.");
                break;
            }
        }
        player.sendMessage("§7Активная команда: §f" + teamCount(player) + "/3");
    }

    private void showTeam(Player player) {
        player.sendMessage("§6§lАктивная команда §7(максимум 3)");
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (id.isEmpty() || !hasBeast(player, id)) {
                player.sendMessage("§e" + slot + ". §7Пусто");
            } else {
                String base = beastPath(player, id);
                String species = data.getString(base + ".species", "unknown");
                String rarity = data.getString(base + ".rarity", "common");
                player.sendMessage("§e" + slot + ". §f" + prettySpecies(species) + " §7[" + rarityColor(rarity)
                        + rarityName(rarity) + "§7] §8ID: " + id);
            }
        }
        player.sendMessage("§7Назначить: §f/beasts team <слот 1-3> <ID>");
    }

    private void setTeamSlot(Player player, String slotText, String id) {
        int slot;
        try { slot = Integer.parseInt(slotText); }
        catch (NumberFormatException ex) { player.sendMessage("§cСлот должен быть числом от 1 до 3."); return; }
        if (slot < 1 || slot > 3) { player.sendMessage("§cМожно использовать только слоты 1, 2 и 3."); return; }
        if (!hasBeast(player, id)) { player.sendMessage("§cТакого существа нет в твоей коллекции."); return; }
        for (int i = 1; i <= 3; i++) {
            if (i != slot && id.equals(data.getString(playerPath(player) + ".team." + i, ""))) {
                player.sendMessage("§cЭто существо уже стоит в слоте " + i + "."); return;
            }
        }
        data.set(playerPath(player) + ".team." + slot, id);
        data.save();
        player.sendMessage("§aСущество назначено в слот команды " + slot + ".");
    }

    private void showBeastInfo(Player player, String id) {
        if (!hasBeast(player, id)) { player.sendMessage("§cСущество не найдено."); return; }
        String base = beastPath(player, id);
        String species = data.getString(base + ".species", "unknown");
        String rarity = data.getString(base + ".rarity", "common");
        player.sendMessage("§6§lИнформация о существе");
        player.sendMessage("§fВид: §b" + prettySpecies(species));
        player.sendMessage("§fРедкость: " + rarityColor(rarity) + rarityName(rarity));
        player.sendMessage("§fУровень: §a" + data.getInt(base + ".level", 1));
        player.sendMessage("§fОпыт: §a" + data.getInt(base + ".xp", 0));
        player.sendMessage("§fПолучено: §7" + data.getString(base + ".source", "unknown"));
        player.sendMessage("§fID: §7" + id);
    }

    private void releaseBeast(Player player, String id) {
        if (!hasBeast(player, id)) { player.sendMessage("§cСущество не найдено."); return; }
        String base = beastPath(player, id);
        data.remove(base);
        for (int slot = 1; slot <= 3; slot++) {
            if (id.equals(data.getString(playerPath(player) + ".team." + slot, ""))) {
                data.remove(playerPath(player) + ".team." + slot);
            }
        }
        data.save();
        player.sendMessage("§eСущество удалено из коллекции.");
    }

    private void giveOrb(CommandSender sender, String[] args) {
        Player target = getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cИгрок не найден или не в сети."); return; }
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ex) { sender.sendMessage("§cКоличество должно быть числом."); return; }
        }
        Item orb = Item.get(368, 0, amount);
        orb.setCustomName(ORB_NAME);
        orb.setLore("§7Нажми по существу, чтобы попытаться приручить его.", "§8NEXORA: BEASTS");
        target.getInventory().addItem(orb);
        sender.sendMessage("§aВыдано ловчих сфер: " + amount + " игроку " + target.getName() + ".");
    }

    private void giveEgg(CommandSender sender, String[] args) {
        Player target = getServer().getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cИгрок не найден или не в сети."); return; }
        String species = args[2];
        String rarity = args[3].toLowerCase(Locale.ROOT);
        if (!species.equalsIgnoreCase("random") && !SAFE_ID.matcher(species).matches()) {
            sender.sendMessage("§cВид должен содержать только латиницу, цифры, _ или -.");
            return;
        }
        if (!isRarity(rarity)) { sender.sendMessage("§cНеизвестная редкость. Используй: " + String.join(", ", RARITIES)); return; }
        Item egg = Item.get(344, 0, 1);
        egg.setCustomName(EGG_NAME);
        egg.setLore("Вид: " + species, "Редкость: " + rarity, "§8NEXORA: BEASTS");
        target.getInventory().addItem(egg);
        sender.sendMessage("§aЯйцо выдано игроку " + target.getName() + ".");
    }

    private String addBeast(Player player, String species, String rarity, String source) {
        String id;
        do { id = UUID.randomUUID().toString().substring(0, 8); }
        while (hasBeast(player, id));
        String base = beastPath(player, id);
        data.set(base + ".species", species);
        data.set(base + ".rarity", rarity);
        data.set(base + ".level", 1);
        data.set(base + ".xp", 0);
        data.set(base + ".source", source);
        data.set(base + ".created", System.currentTimeMillis());
        data.save();
        return id;
    }

    private String rollWildRarity() {
        int roll = ThreadLocalRandom.current().nextInt(1000);
        int sum = 0;
        for (int i = 0; i < WILD_RARITY_WEIGHTS.length; i++) {
            sum += WILD_RARITY_WEIGHTS[i];
            if (roll < sum) return RARITIES[i];
        }
        return "common";
    }

    private void consumeOne(Player player, Item item) {
        int count = item.getCount();
        if (count <= 1) player.getInventory().setItemInHand(Item.get(0, 0, 0));
        else {
            item.setCount(count - 1);
            player.getInventory().setItemInHand(item);
        }
    }

    private Map<String, Object> collection(Player player) {
        return data.getSection(playerPath(player) + ".collection");
    }

    private boolean hasBeast(Player player, String id) {
        return data.exists(beastPath(player, id) + ".species");
    }

    private int collectionSize(Player player) { return collection(player).size(); }

    private int teamCount(Player player) {
        int count = 0;
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (!id.isEmpty() && hasBeast(player, id)) count++;
        }
        return count;
    }

    private String playerPath(Player player) { return "players." + player.getUniqueId().toString(); }
    private String beastPath(Player player, String id) { return playerPath(player) + ".collection." + id; }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sender.sendMessage("§cЭта команда доступна только игроку.");
        return false;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.isOp() || sender.hasPermission("nexora.beasts.admin")) return true;
        sender.sendMessage("§cНедостаточно прав.");
        return false;
    }

    private boolean isRarity(String rarity) {
        for (String value : RARITIES) if (value.equals(rarity)) return true;
        return false;
    }

    private String rarityName(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return RARITY_NAMES[i];
        return RARITY_NAMES[0];
    }

    private String rarityColor(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return RARITY_COLORS[i];
        return "§7";
    }

    private String normalizeSpecies(String species) {
        String value = species.trim().replace(' ', '_').toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "unknown" : value;
    }

    private String prettySpecies(String species) {
        String value = species.replace('_', ' ');
        if (value.isEmpty()) return "Неизвестное существо";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lNEXORA: BEASTS");
        sender.sendMessage("§e/beasts collection §7— открыть коллекцию");
        sender.sendMessage("§e/beasts team §7— показать активную команду");
        sender.sendMessage("§e/beasts team <1-3> <ID> §7— назначить существо");
        sender.sendMessage("§e/beasts info <ID> §7— информация о существе");
        sender.sendMessage("§e/beasts release <ID> §7— отпустить существо");
        if (sender.isOp() || sender.hasPermission("nexora.beasts.admin")) {
            sender.sendMessage("§c/beasts giveorb <игрок> [количество] §7— выдать сферы");
            sender.sendMessage("§c/beasts giveegg <игрок> <вид|random> <редкость> §7— выдать яйцо");
        }
    }
}
