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
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementButtonImageData;
import cn.nukkit.form.element.ElementDropdown;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.form.window.FormWindowModal;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class NEXORABeasts extends PluginBase implements Listener {
    private static final String ORB_NAME = "§bЛовчая сфера NEXORA";
    private static final String EGG_NAME = "§6Яйцо NEXORA";
    private static final String EGG_PREFIX = "§6Яйцо NEXORA · ";
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
    private final Set<UUID> pendingHatches = new HashSet<>();
    private final Map<UUID, Map<Integer, Entity>> activeCompanions = new HashMap<>();
    private static final int HATCH_DELAY_TICKS = 100; // 5 seconds

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку NEXORA_Beasts.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        data = new Config(new File(getDataFolder(), "players.yml"), Config.YAML);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleRepeatingTask(this, this::tickCompanions, 5);
        getLogger().info("NEXORA_Beasts включён. Коллекция, GUI и система питомцев готовы.");
    }

    @Override
    public void onDisable() {
        for (Map<Integer, Entity> companions : activeCompanions.values()) {
            for (Entity entity : companions.values()) {
                if (entity != null && !entity.isClosed()) entity.close();
            }
        }
        activeCompanions.clear();
        if (data != null) data.save();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            if (player.isOnline() && teamCount(player) > 0) summonTeam(player);
        }, 20);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        recallTeam(event.getPlayer(), false);
    }

    private void tickCompanions() {
        for (Player player : getServer().getOnlinePlayers().values()) {
            Map<Integer, Entity> companions = activeCompanions.get(player.getUniqueId());
            if (companions == null || companions.isEmpty()) continue;
            for (Map.Entry<Integer, Entity> entry : companions.entrySet()) {
                Entity pet = entry.getValue();
                if (pet == null || pet.isClosed()) continue;

                if (pet.getLevel() != player.getLevel()) {
                    pet.teleport(player.add(companionOffsetX(entry.getKey()), 0, companionOffsetZ(entry.getKey())));
                    continue;
                }

                cn.nukkit.math.Vector3 target = player.add(
                        companionOffsetX(entry.getKey()), 0, companionOffsetZ(entry.getKey()));
                double dx = target.x - pet.x;
                double dz = target.z - pet.z;
                double distanceSquared = dx * dx + dz * dz;

                if (distanceSquared > 144) {
                    pet.teleport(target);
                } else if (distanceSquared > 2.25) {
                    double distance = Math.sqrt(distanceSquared);
                    double step = Math.min(0.32, distance - 1.0);
                    pet.move(dx / distance * step, 0, dz / distance * step);
                    pet.updateMovement();
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (sender instanceof Player player) openMainMenu(player);
            else sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui" -> {
                if (requirePlayer(sender)) openMainMenu((Player) sender);
            }
            case "collection", "list" -> {
                if (requirePlayer(sender)) openCollectionMenu((Player) sender);
            }
            case "summon", "call" -> {
                if (requirePlayer(sender)) summonTeam((Player) sender);
            }
            case "recall", "hide" -> {
                if (requirePlayer(sender)) recallTeam((Player) sender, true);
            }
            case "team" -> {
                if (requirePlayer(sender)) {
                    if (args.length == 1) openTeamMenu((Player) sender);
                    else if (args.length == 3) setTeamSlot((Player) sender, args[1], args[2]);
                    else sender.sendMessage("§cИспользование: /beasts team <слот 1-3> <ID существа>");
                }
            }
            case "train" -> {
                if (!requirePlayer(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts train <ID>");
                else trainBeast((Player) sender, args[1]);
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
            case "eggs" -> showEggCatalog(sender);
            case "giveeggs" -> {
                if (!requireAdmin(sender)) return true;
                if (args.length < 2) sender.sendMessage("§cИспользование: /beasts giveeggs <игрок>");
                else giveEggShowcase(sender, args[1]);
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
        if (hand == null || hand.getCustomName() == null
                || !(EGG_NAME.equals(hand.getCustomName()) || hand.getCustomName().startsWith(EGG_PREFIX))) return;

        String species = "random";
        String rarity = "common";
        String[] lore = hand.getLore();
        if (lore != null) {
            for (String line : lore) {
                String clean = stripColors(line).trim();
                if (clean.startsWith("NEXORA_SPECIES:")) species = clean.substring("NEXORA_SPECIES:".length()).trim();
                if (clean.startsWith("NEXORA_RARITY:")) rarity = clean.substring("NEXORA_RARITY:".length()).trim().toLowerCase(Locale.ROOT);
            }
        }
        if (!isRarity(rarity)) rarity = "common";
        if (species.equalsIgnoreCase("random")) {
            species = RANDOM_SPECIES[ThreadLocalRandom.current().nextInt(RANDOM_SPECIES.length)];
        }

        event.setCancelled();
        UUID playerId = player.getUniqueId();
        if (pendingHatches.contains(playerId)) {
            player.sendMessage("§eУ тебя уже вылупляется яйцо. Подожди немного.");
            return;
        }

        pendingHatches.add(playerId);
        final String hatchSpecies = species;
        final String hatchRarity = rarity;
        consumeOne(player, hand);
        player.sendMessage("§6§lВылупление началось!");
        player.sendMessage("§7Яйцо заряжается магией... §eПодожди 5 секунд.");
        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            pendingHatches.remove(playerId);
            String id = addBeast(player, normalizeSpecies(hatchSpecies), hatchRarity, "egg");
            if (player.isOnline()) {
                player.sendMessage("§a§lЯЙЦО ВЫЛУПИЛОСЬ!");
                player.sendMessage("§f" + prettySpecies(hatchSpecies) + " §7[" + rarityColor(hatchRarity) + rarityName(hatchRarity) + "§7]");
                player.sendMessage("§7Новое существо добавлено в коллекцию. ID: §f" + id);
                player.sendMessage("§7Характеристики можно посмотреть: §f/beasts info " + id);
            }
        }, HATCH_DELAY_TICKS);
    }

    // -------------------- NEXORA touch-friendly GUI --------------------

    private ElementButton iconButton(String text, String texturePath) {
        return new ElementButton(text, new ElementButtonImageData(
                ElementButtonImageData.IMAGE_DATA_TYPE_PATH, texturePath));
    }

    private void openMainMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0NEXORA: BEASTS", 
                "§7Твоя связь с дикими существами.\n\n"
                + "§fКоллекция: §b" + collectionSize(player) + " §8| §fКоманда: §b" + teamCount(player) + "/3\n"
                + "§7Выбери раздел ниже.");
        form.addButton(iconButton("§l§b✦  МОЯ КОЛЛЕКЦИЯ\n§8Все пойманные существа", "textures/ui/collection"));
        form.addButton(iconButton("§l§6⚔  МОЯ КОМАНДА\n§8Три активных существа", "textures/ui/team"));
        form.addButton(iconButton("§l§d✧  КАТАЛОГ ЯИЦ\n§8Десять уровней редкости", "textures/ui/eggs"));
        form.addButton(new ElementButton("§l§7✕  ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            switch (form.getResponse().getClickedButtonId()) {
                case 0 -> openCollectionMenu(player);
                case 1 -> openTeamMenu(player);
                case 2 -> openEggCatalogMenu(player);
                default -> { }
            }
        }));
        player.showFormWindow(form);
    }

    private void openCollectionMenu(Player player) {
        Map<String, Object> entries = collection(player);
        FormWindowSimple form = new FormWindowSimple("§l§0МОЯ КОЛЛЕКЦИЯ",
                "§7Собрано существ: §b" + entries.size() + "\n§8Нажми на существо, чтобы открыть его карточку.");
        List<String> ids = new ArrayList<>(entries.keySet());
        for (String id : ids) {
            String base = beastPath(player, id);
            String species = data.getString(base + ".species", "unknown");
            String rarity = data.getString(base + ".rarity", "common");
            int level = data.getInt(base + ".level", 1);
            form.addButton(new ElementButton(rarityColor(rarity) + "✦ " + prettySpecies(species)
                    + "\n§8" + rarityName(rarity) + " · Ур. " + level + " · " + id));
        }
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < ids.size()) openBeastCard(player, ids.get(selected));
            else openMainMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openBeastCard(Player player, String id) {
        if (!hasBeast(player, id)) { openCollectionMenu(player); return; }
        String base = beastPath(player, id);
        String species = data.getString(base + ".species", "unknown");
        String rarity = data.getString(base + ".rarity", "common");
        int level = data.getInt(base + ".level", 1);
        String details = "§7" + prettySpecies(species) + "  " + rarityColor(rarity) + "◆ " + rarityName(rarity)
                + "\n\n§fУровень: §a" + level + "\n§fОпыт: §a" + data.getInt(base + ".xp", 0)
                + "§7 / " + xpToNext(level)
                + "\n\n§c❤ §fЗдоровье: §c" + stat(base, "vitality", 20)
                + "\n§6⚔ §fСила: §6" + stat(base, "power", 5)
                + "\n§b⬟ §fЗащита: §b" + stat(base, "defense", 2)
                + "\n\n§8ID: " + id;
        FormWindowSimple form = new FormWindowSimple("§l§0КАРТОЧКА СУЩЕСТВА", details);
        form.addButton(iconButton("§l§a⬆ ТРЕНИРОВАТЬ\n§81 пшеница · +25 XP", "textures/ui/training"));
        form.addButton(new ElementButton("§l§6⚔ В КОМАНДУ\n§8Назначить в один из 3 слотов"));
        form.addButton(iconButton("§l§c✕ ОТПУСТИТЬ\n§8Удалить из коллекции", "textures/ui/release"));
        form.addButton(new ElementButton("§7← К КОЛЛЕКЦИИ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            switch (form.getResponse().getClickedButtonId()) {
                case 0 -> { trainBeast(player, id); openBeastCard(player, id); }
                case 1 -> openAssignSlotMenu(player, id);
                case 2 -> confirmRelease(player, id);
                default -> openCollectionMenu(player);
            }
        }));
        player.showFormWindow(form);
    }

    private void openAssignSlotMenu(Player player, String id) {
        FormWindowCustom form = new FormWindowCustom("§l§0НАЗНАЧИТЬ В КОМАНДУ");
        form.addElement(new ElementDropdown("Выбери слот активной команды", List.of("Слот 1", "Слот 2", "Слот 3")));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int slot = form.getResponse().getDropdownResponse(0).getElementID() + 1;
            setTeamSlot(player, String.valueOf(slot), id);
            openTeamMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openTeamMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0АКТИВНАЯ КОМАНДА",
                "§7Выбери слот, чтобы назначить в него существо из коллекции.\n§8Максимум три существа.");
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (id.isEmpty() || !hasBeast(player, id)) {
                form.addButton(new ElementButton("§e＋ СЛОТ " + slot + " · ПУСТО\n§8Нажми, чтобы выбрать существо"));
            } else {
                String base = beastPath(player, id);
                String species = data.getString(base + ".species", "unknown");
                String rarity = data.getString(base + ".rarity", "common");
                form.addButton(new ElementButton(rarityColor(rarity) + "✦ СЛОТ " + slot + " · " + prettySpecies(species)
                        + "\n§8" + rarityName(rarity) + " · Ур. " + data.getInt(base + ".level", 1)));
            }
        }
        form.addButton(new ElementButton("§a✦ ПРИЗВАТЬ КОМАНДУ"));
        form.addButton(new ElementButton("§c✕ ОТОЗВАТЬ ПИТОМЦЕВ"));
        form.addButton(new ElementButton("§7← ГЛАВНОЕ МЕНЮ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < 3) openSelectBeastForSlot(player, selected + 1);
            else if (selected == 3) summonTeam(player);
            else if (selected == 4) { recallTeam(player, true); openTeamMenu(player); }
            else openMainMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openSelectBeastForSlot(Player player, int slot) {
        Map<String, Object> entries = collection(player);
        FormWindowSimple form = new FormWindowSimple("§l§0ВЫБОР СУЩЕСТВА",
                "§7Назначение в слот " + slot + ".\n§8Выбери существо из своей коллекции.");
        List<String> ids = new ArrayList<>(entries.keySet());
        for (String id : ids) {
            String base = beastPath(player, id);
            String rarity = data.getString(base + ".rarity", "common");
            form.addButton(new ElementButton(rarityColor(rarity) + prettySpecies(data.getString(base + ".species", "unknown"))
                    + "\n§8" + rarityName(rarity) + " · " + id));
        }
        form.addButton(new ElementButton("§7← НАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < ids.size()) setTeamSlot(player, String.valueOf(slot), ids.get(selected));
            openTeamMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void confirmRelease(Player player, String id) {
        FormWindowModal form = new FormWindowModal("§l§0ОТПУСТИТЬ СУЩЕСТВО",
                "§cТы уверен, что хочешь навсегда удалить это существо из коллекции?\n§8Это действие нельзя отменить.",
                "§cОТПУСТИТЬ", "§7ОТМЕНА");
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            if (form.getResponse().getClickedButtonId() == 0) releaseBeast(player, id);
            openCollectionMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void openEggCatalogMenu(Player player) {
        FormWindowSimple form = new FormWindowSimple("§l§0АРХИВ ЯИЦ NEXORA",
                "§7Десять ступеней редкости. Цвет яйца и существа зависит от его ранга.\n§8Яйца можно получить у администрации или найти в игровых событиях.");
        for (int i = 0; i < RARITIES.length; i++) {
            form.addButton(new ElementButton(RARITY_COLORS[i] + "◆ " + RARITY_NAMES[i]
                    + "\n§8" + RARITIES[i].toUpperCase(Locale.ROOT)));
        }
        form.addButton(new ElementButton("§7← ГЛАВНОЕ МЕНЮ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int selected = form.getResponse().getClickedButtonId();
            if (selected < RARITIES.length) {
                player.sendMessage(RARITY_COLORS[selected] + "◆ " + RARITY_NAMES[selected]
                        + " §8— §7редкость яйца NEXORA.");
                openEggCatalogMenu(player);
            } else openMainMenu(player);
        }));
        player.showFormWindow(form);
    }

    // -------------------- Summoned companions --------------------

    private double companionOffsetX(int slot) {
        return slot == 1 ? 1.6 : slot == 2 ? -1.6 : 0.0;
    }

    private double companionOffsetZ(int slot) {
        return slot == 3 ? 1.8 : 0.0;
    }

    private String entityIdentifier(String species) {
        String normalized = species.toLowerCase(Locale.ROOT);
        if (normalized.equals("polarbear")) normalized = "polar_bear";
        return "minecraft:" + normalized;
    }

    private void summonTeam(Player player) {
        int summoned = 0;
        Map<Integer, Entity> companions = activeCompanions.computeIfAbsent(
                player.getUniqueId(), ignored -> new HashMap<>());
        for (int slot = 1; slot <= 3; slot++) {
            String id = data.getString(playerPath(player) + ".team." + slot, "");
            if (id.isEmpty() || !hasBeast(player, id)) continue;
            Entity existing = companions.get(slot);
            if (existing != null && !existing.isClosed()) {
                summoned++;
                continue;
            }
            String base = beastPath(player, id);
            String species = data.getString(base + ".species", "wolf").toLowerCase(Locale.ROOT);
            Entity pet;
            try {
                pet = Entity.createEntity(entityIdentifier(species), player);
            } catch (RuntimeException ex) {
                getLogger().warning("Не удалось создать питомца " + species + ": " + ex.getMessage());
                pet = null;
            }
            if (pet == null) {
                player.sendMessage("§cНе удалось призвать вид: " + species + ". Он пока не поддерживается сервером.");
                continue;
            }
            pet.setNameTag(rarityColor(data.getString(base + ".rarity", "common"))
                    + prettySpecies(species) + " §8[§f" + data.getInt(base + ".level", 1) + "§8]");
            pet.setNameTagVisible(true);
            pet.setNameTagAlwaysVisible(true);
            pet.teleport(player.add(companionOffsetX(slot), 0, companionOffsetZ(slot)));
            pet.spawnToAll();
            companions.put(slot, pet);
            summoned++;
        }
        if (summoned == 0) {
            player.sendMessage("§eСначала добавь существ в активную команду.");
        } else {
            player.sendMessage("§a✦ Призвано питомцев: §f" + summoned + "§a/3. Они будут следовать за тобой.");
        }
    }

    private void recallTeam(Player player, boolean notify) {
        Map<Integer, Entity> companions = activeCompanions.remove(player.getUniqueId());
        int removed = 0;
        if (companions != null) {
            for (Entity pet : companions.values()) {
                if (pet != null && !pet.isClosed()) {
                    pet.close();
                    removed++;
                }
            }
        }
        if (notify) player.sendMessage("§eПитомцы отозваны в коллекцию. §7(" + removed + ")");
    }

    // -------------------- Existing text fallbacks --------------------

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
        Map<Integer, Entity> active = activeCompanions.get(player.getUniqueId());
        if (active != null) {
            Entity previous = active.remove(slot);
            if (previous != null && !previous.isClosed()) previous.close();
        }
        data.set(playerPath(player) + ".team." + slot, id);
        data.save();
        player.sendMessage("§aСущество назначено в слот команды " + slot + ".");
        if (active != null) {
            getServer().getScheduler().scheduleDelayedTask(this, () -> {
                if (player.isOnline()) summonTeam(player);
            }, 1);
        }
    }

    private void showBeastInfo(Player player, String id) {
        if (!hasBeast(player, id)) { player.sendMessage("§cСущество не найдено."); return; }
        String base = beastPath(player, id);
        String species = data.getString(base + ".species", "unknown");
        String rarity = data.getString(base + ".rarity", "common");
        player.sendMessage("§6§lИнформация о существе");
        player.sendMessage("§fВид: §b" + prettySpecies(species));
        player.sendMessage("§fРедкость: " + rarityColor(rarity) + rarityName(rarity));
        int level = data.getInt(base + ".level", 1);
        player.sendMessage("§fУровень: §a" + level);
        player.sendMessage("§fОпыт: §a" + data.getInt(base + ".xp", 0) + "§7 / " + xpToNext(level));
        player.sendMessage("§fЗдоровье: §c" + stat(base, "vitality", 20) + " §8| §fАтака: §c" + stat(base, "power", 5));
        player.sendMessage("§fЗащита: §b" + stat(base, "defense", 2) + " §8| §fПотенциал: §d" + data.getInt(base + ".potential", 0));
        player.sendMessage("§fПолучено: §7" + data.getString(base + ".source", "unknown"));
        player.sendMessage("§fID: §7" + id);
    }

    private void releaseBeast(Player player, String id) {
        if (!hasBeast(player, id)) { player.sendMessage("§cСущество не найдено."); return; }
        Map<Integer, Entity> companions = activeCompanions.get(player.getUniqueId());
        if (companions != null) {
            for (int slot = 1; slot <= 3; slot++) {
                if (id.equals(data.getString(playerPath(player) + ".team." + slot, ""))) {
                    Entity active = companions.remove(slot);
                    if (active != null && !active.isClosed()) active.close();
                }
            }
            if (companions.isEmpty()) activeCompanions.remove(player.getUniqueId());
        }
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
        target.getInventory().addItem(createEgg(species, rarity));
        sender.sendMessage("§aВыдано " + rarityColor(rarity) + rarityName(rarity) + "§a яйцо игроку " + target.getName() + ".");
    }

    private Item createEgg(String species, String rarity) {
        Item egg = Item.get(344, 0, 1);
        egg.setCustomName(EGG_PREFIX + rarityColor(rarity) + rarityName(rarity));
        egg.setLore(
                "§7Вид: §f" + species,
                "§7Редкость: " + rarityColor(rarity) + rarityName(rarity),
                "§eНажми ПКМ, чтобы вылупить существо",
                "§0NEXORA_SPECIES:" + species,
                "§0NEXORA_RARITY:" + rarity,
                "§8NEXORA: BEASTS"
        );
        return egg;
    }

    private void giveEggShowcase(CommandSender sender, String playerName) {
        Player target = getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§cИгрок не найден или не в сети.");
            return;
        }
        for (String rarity : RARITIES) {
            target.getInventory().addItem(createEgg("random", rarity));
        }
        sender.sendMessage("§aВыдан набор из 10 яиц NEXORA игроку " + target.getName() + ".");
        target.sendMessage("§6Тебе выдан набор для просмотра всех 10 редкостей яиц NEXORA!");
    }

    private void showEggCatalog(CommandSender sender) {
        sender.sendMessage("§6§lЯйца NEXORA — каталог редкостей");
        for (int i = 0; i < RARITIES.length; i++) {
            sender.sendMessage("§8• " + RARITY_COLORS[i] + RARITY_NAMES[i] + " §7(" + RARITIES[i] + ")");
        }
        sender.sendMessage("§7Администратор может выдать набор: §f/beasts giveeggs <игрок>");
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
        data.set(base + ".potential", ThreadLocalRandom.current().nextInt(0, 6));
        data.set(base + ".created", System.currentTimeMillis());
        data.save();
        return id;
    }

    private void trainBeast(Player player, String id) {
        if (!hasBeast(player, id)) {
            player.sendMessage("§cСущество не найдено в твоей коллекции.");
            return;
        }
        Item food = Item.get(296, 0, 1); // Wheat
        if (!player.getInventory().contains(food)) {
            player.sendMessage("§cДля тренировки нужна 1 пшеница.");
            return;
        }
        player.getInventory().removeItem(food);
        String base = beastPath(player, id);
        int level = data.getInt(base + ".level", 1);
        int xp = data.getInt(base + ".xp", 0) + 25;
        boolean leveled = false;
        while (level < 100 && xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            leveled = true;
        }
        data.set(base + ".level", level);
        data.set(base + ".xp", xp);
        data.save();
        player.sendMessage("§aТренировка завершена! §f" + prettySpecies(data.getString(base + ".species", "unknown")) + " §aполучил 25 опыта.");
        if (leveled) player.sendMessage("§6§lУРОВЕНЬ ПОВЫШЕН! §eТеперь уровень " + level + ".");
        player.sendMessage("§7Опыт: §a" + xp + "§7 / " + xpToNext(level) + " §8| §7Нужно ещё тренировок: §f" + (int) Math.ceil((xpToNext(level) - xp) / 25.0));
    }

    private int xpToNext(int level) { return 50 + (Math.max(1, level) * 50); }

    private int rarityIndex(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return i;
        return 0;
    }

    private int stat(String base, String key, int fallback) {
        int level = Math.max(1, data.getInt(base + ".level", 1));
        int tier = rarityIndex(data.getString(base + ".rarity", "common"));
        int potential = data.getInt(base + ".potential", 0);
        return switch (key) {
            case "vitality" -> 20 + tier * 8 + (level - 1) * 4 + potential * 2;
            case "power" -> 5 + tier * 3 + (level - 1) * 2 + potential;
            case "defense" -> 2 + tier * 2 + (level - 1) + potential / 2;
            default -> fallback;
        };
    }

    private String stripColors(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "");
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
        if (species.equalsIgnoreCase("polarbear")) return "Polar Bear";
        String value = species.replace('_', ' ');
        if (value.isEmpty()) return "Неизвестное существо";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lNEXORA: BEASTS");
        sender.sendMessage("§e/beasts eggs §7— каталог 10 редкостей яиц");
        sender.sendMessage("§e/beasts collection §7— открыть коллекцию");
        sender.sendMessage("§e/beasts team §7— показать активную команду");
        sender.sendMessage("§e/beasts team <1-3> <ID> §7— назначить существо");
        sender.sendMessage("§e/beasts summon §7— призвать активную команду");
        sender.sendMessage("§e/beasts recall §7— отозвать питомцев");
        sender.sendMessage("§e/beasts info <ID> §7— информация о существе");
        sender.sendMessage("§e/beasts train <ID> §7— тренировка за 1 пшеницу (+25 опыта)");
        sender.sendMessage("§e/beasts release <ID> §7— отпустить существо");
        if (sender.isOp() || sender.hasPermission("nexora.beasts.admin")) {
            sender.sendMessage("§c/beasts giveorb <игрок> [количество] §7— выдать сферы");
            sender.sendMessage("§c/beasts giveegg <игрок> <вид|random> <редкость> §7— выдать яйцо");
            sender.sendMessage("§c/beasts giveeggs <игрок> §7— выдать набор из 10 яиц");
        }
    }
}
