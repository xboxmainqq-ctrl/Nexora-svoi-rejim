package ru.nexora.regions;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockBurnEvent;
import cn.nukkit.event.block.BlockFromToEvent;
import cn.nukkit.event.block.BlockIgniteEvent;
import cn.nukkit.event.block.BlockPistonEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityExplodeEvent;
import cn.nukkit.event.entity.EntityBlockChangeEvent;
import cn.nukkit.event.entity.EntityInteractEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.math.BlockFace;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.FlameParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static ru.nexora.regions.NEXORARegions.PREFIX;

/**
 * Land claims. One claim is one 16x16 chunk (all heights). The owner and the players he trusts
 * can build and open containers there; everybody else can not.
 * Environmental protection also blocks explosions, piston transfers, liquid flow, fire and mob block interactions.
 */
final class RegionsModule implements Listener {
    private static final int BUCKET = 325;
    private static final int FLINT_AND_STEEL = 259;

    private final NEXORARegions plugin;
    private final RegionsStore store;
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    private final Map<UUID, int[]> selections = new HashMap<>();

    RegionsModule(NEXORARegions plugin) {
        this.plugin = plugin;
        File target = new File(plugin.getDataFolder(), "regions.yml");
        File legacy = new File("plugins/NEXORA_Survival/claims.yml");
        if (!target.exists() && legacy.isFile()) {
            try {
                Files.copy(legacy.toPath(), target.toPath());
                plugin.getLogger().info("Старые приваты перенесены из NEXORA_Survival/claims.yml.");
            } catch (IOException ex) {
                plugin.getLogger().warning("Не удалось перенести старые приваты: " + ex.getMessage());
            }
        }
        this.store = new RegionsStore(target);
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

    private boolean isCoOwner(String claimKey, UUID player) {
        return store.cfg.exists("claims." + claimKey + ".members." + player);
    }

    private String flagValue(String claimKey, String flag) {
        String fallback = switch (flag) {
            case "pvp" -> "allow";
            case "explosions", "fire", "mob-grief" -> "deny";
            default -> "deny";
        };
        return store.cfg.getString("claims." + claimKey + ".flags." + flag, fallback);
    }

    private boolean flagDeniedAt(Level level, int x, int z, String flag) {
        if (level == null) return false;
        if (plugin.inSpawnProtection(level, x, z)) return true;
        String id = claimKeyAt(level, x, z);
        return !id.isEmpty() && "deny".equalsIgnoreCase(flagValue(id, flag));
    }

    private boolean isClaimOwnerOrAdmin(Player player, String claimKey) {
        return NEXORARegions.isAdmin(player)
                || ownerOf(claimKey).equals(player.getUniqueId().toString());
    }

    private void showFlagHelp(Player player) {
        player.sendMessage("§6§l✦ NEXORA • Флаги региона");
        player.sendMessage("§e/rg flag §7— показать все флаги и их назначение.");
        player.sendMessage("§e/rg flag PvP deny §7— запретить урон между игроками.");
        player.sendMessage("§e/rg flag PvP allow §7— разрешить PvP.");
        player.sendMessage("§e/rg flag explosions deny §7— запретить взрывам ломать блоки.");
        player.sendMessage("§e/rg flag explosions allow §7— разрешить взрывам ломать блоки.");
        player.sendMessage("§e/rg flag fire deny §7— запретить огню загораться и распространяться.");
        player.sendMessage("§e/rg flag fire allow §7— разрешить огонь.");
        player.sendMessage("§e/rg flag mob-grief deny §7— запретить мобам менять блоки и нажимать плиты.");
        player.sendMessage("§e/rg flag mob-grief allow §7— разрешить мобам такие взаимодействия.");
        player.sendMessage("§8Значение deny = запрет, allow = разрешение.");
        String id = claimKeyAt(player.getLevel(), player.getFloorX(), player.getFloorZ());
        if (!id.isEmpty()) {
            player.sendMessage("§aФлаги этого региона: §fPvP=" + flagValue(id, "pvp")
                    + " §7| §fexplosions=" + flagValue(id, "explosions")
                    + " §7| §ffire=" + flagValue(id, "fire")
                    + " §7| §fmob-grief=" + flagValue(id, "mob-grief"));
        } else {
            player.sendMessage("§7Встань внутрь своего региона, чтобы изменить его флаги.");
        }
    }

    private void setFlag(Player player, String[] args) {
        if (args.length < 2) {
            showFlagHelp(player);
            return;
        }
        String flag = args[0].toLowerCase(Locale.ROOT);
        if (flag.equals("explosion")) flag = "explosions";
        if (flag.equals("mobgrief") || flag.equals("mob_grief")) flag = "mob-grief";
        if (!List.of("pvp", "explosions", "fire", "mob-grief").contains(flag)) {
            player.sendMessage(PREFIX + "§cНеизвестный флаг. Напиши §e/rg flag§c для списка.");
            return;
        }
        String value = args[1].toLowerCase(Locale.ROOT);
        if (!value.equals("allow") && !value.equals("deny")) {
            player.sendMessage(PREFIX + "§cИспользование: §e/rg flag " + flag + " allow§c или §e/rg flag " + flag + " deny");
            return;
        }
        String id = claimKeyAt(player.getLevel(), player.getFloorX(), player.getFloorZ());
        if (id.isEmpty()) {
            player.sendMessage(PREFIX + "§cТы должен стоять внутри своего региона.");
            return;
        }
        if (!isClaimOwnerOrAdmin(player, id)) {
            player.sendMessage(PREFIX + "§cМенять флаги может только владелец региона или администратор.");
            return;
        }
        store.cfg.set("claims." + id + ".flags." + flag, value);
        store.touch();
        String label = switch (flag) {
            case "pvp" -> "PvP";
            case "explosions" -> "Взрывы";
            case "fire" -> "Огонь";
            default -> "Взаимодействия мобов";
        };
        player.sendMessage(PREFIX + "§aФлаг §f" + label + " §aустановлен: "
                + (value.equals("deny") ? "§cDENY §7(запрещено)" : "§aALLOW §7(разрешено)") + "§a.");
    }

    private String claimKeyAt(Level level, int x, int z) {
        if (level == null) return "";
        ConfigSection all = store.cfg.getSection("claims");
        for (String id : all.getKeys(false)) {
            String base = "claims." + id;
            if (!store.cfg.getString(base + ".world", "").equals(level.getName())) continue;
            if (store.cfg.exists(base + ".minX")) {
                int minX = store.cfg.getInt(base + ".minX"), maxX = store.cfg.getInt(base + ".maxX");
                int minZ = store.cfg.getInt(base + ".minZ"), maxZ = store.cfg.getInt(base + ".maxZ");
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) return id;
            } else if ((x >> 4) == store.cfg.getInt(base + ".cx", Integer.MIN_VALUE)
                    && (z >> 4) == store.cfg.getInt(base + ".cz", Integer.MIN_VALUE)) return id;
        }
        return "";
    }

    private String ownerAt(Level level, int x, int z) {
        String id = claimKeyAt(level, x, z);
        return id.isEmpty() ? "" : ownerOf(id);
    }

    private boolean overlapsClaim(Level level, int minX, int maxX, int minZ, int maxZ) {
        return overlapsClaim(level, minX, maxX, minZ, maxZ, "");
    }

    private boolean overlapsClaim(Level level, int minX, int maxX, int minZ, int maxZ, String excludedId) {
        ConfigSection all = store.cfg.getSection("claims");
        for (String id : all.getKeys(false)) {
            if (id.equals(excludedId)) continue;
            String base = "claims." + id;
            if (!store.cfg.getString(base + ".world", "").equals(level.getName())) continue;
            int a, b, c, d;
            if (store.cfg.exists(base + ".minX")) {
                a = store.cfg.getInt(base + ".minX"); b = store.cfg.getInt(base + ".maxX");
                c = store.cfg.getInt(base + ".minZ"); d = store.cfg.getInt(base + ".maxZ");
            } else {
                a = store.cfg.getInt(base + ".cx", 0) << 4; b = a + 15;
                c = store.cfg.getInt(base + ".cz", 0) << 4; d = c + 15;
            }
            if (minX <= b && maxX >= a && minZ <= d && maxZ >= c) return true;
        }
        return false;
    }

    /** Draws a lightweight temporary-looking outline preview with particles after both points are selected. */
    private void showSelectionBorder(Player player, int[] p) {
        if (p == null || p[0] == Integer.MIN_VALUE || p[2] == Integer.MIN_VALUE) return;
        int minX = Math.min(p[0], p[2]), maxX = Math.max(p[0], p[2]);
        int minZ = Math.min(p[1], p[3]), maxZ = Math.max(p[1], p[3]);
        int y = player.getFloorY() + 1;
        Level level = player.getLevel();
        int stepX = Math.max(1, (maxX - minX) / 40);
        int stepZ = Math.max(1, (maxZ - minZ) / 40);
        for (int x = minX; x <= maxX; x += stepX) {
            level.addParticle(new FlameParticle(new Vector3(x + 0.5, y, minZ + 0.5)));
            if (maxZ != minZ) level.addParticle(new FlameParticle(new Vector3(x + 0.5, y, maxZ + 0.5)));
        }
        for (int z = minZ; z <= maxZ; z += stepZ) {
            level.addParticle(new FlameParticle(new Vector3(minX + 0.5, y, z + 0.5)));
            if (maxX != minX) level.addParticle(new FlameParticle(new Vector3(maxX + 0.5, y, z + 0.5)));
        }
        player.sendMessage(PREFIX + "§bГраница выделения отмечена частицами.");
    }

    // ---- Protection ----

    private void warn(Player player, String text) {
        long now = System.currentTimeMillis();
        if (now - lastWarning.getOrDefault(player.getUniqueId(), 0L) < 2000L) return;
        lastWarning.put(player.getUniqueId(), now);
        player.sendMessage(PREFIX + text);
    }

    private boolean allowed(Player player, Level level, int x, int z) {
        if (NEXORARegions.isAdmin(player)) return true;
        if (plugin.inSpawnProtection(level, x, z)) {
            warn(player, "§cСпавн защищён от строительства.");
            return false;
        }
        String claimKey = claimKeyAt(level, x, z);
        String owner = claimKey.isEmpty() ? "" : ownerOf(claimKey);
        if (owner.isEmpty() || owner.equals(player.getUniqueId().toString())
                || isCoOwner(claimKey, player.getUniqueId())) return true;
        if (isTrusted(owner, player.getUniqueId())) return true;
        warn(player, "§cЭта территория принадлежит §e"
                + store.cfg.getString("claims." + claimKey + ".ownerName", "другому игроку") + "§c.");
        return false;
    }

    /**
     * Returns the protection domain at a block: empty for wilderness, "spawn" for spawn
     * protection, or the UUID of the region owner. Comparing domains prevents mechanisms
     * from moving blocks or fluids across a protected border.
     */
    private String protectionDomain(Level level, int x, int z) {
        // Some Nukkit-MOT liquid updates create transient Block objects without a Level.
        // Treat those as wilderness instead of allowing an event-handler NPE.
        if (level == null) return "";
        if (plugin.inSpawnProtection(level, x, z)) return "spawn";
        return ownerAt(level, x, z);
    }

    private boolean protectedAt(Level level, int x, int z) {
        return !protectionDomain(level, x, z).isEmpty();
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        // Keep the explosion itself, but remove every block inside a claim or spawn protection.
        event.getBlockList().removeIf(block ->
                flagDeniedAt(block.getLevel(), block.getFloorX(), block.getFloorZ(), "explosions"));
        if (event.getIgnitions() != null) {
            event.getIgnitions().removeIf(block ->
                    flagDeniedAt(block.getLevel(), block.getFloorX(), block.getFloorZ(), "fire"));
        }
    }

    @EventHandler
    public void onPiston(BlockPistonEvent event) {
        Block piston = event.getBlock();
        Level level = piston.getLevel();
        String domain = protectionDomain(level, piston.getFloorX(), piston.getFloorZ());
        BlockFace direction = event.getDirection();

        for (Block moved : event.getBlocks()) {
            String sourceDomain = protectionDomain(level, moved.getFloorX(), moved.getFloorZ());
            String targetDomain = protectionDomain(level,
                    moved.getFloorX() + direction.getXOffset(),
                    moved.getFloorZ() + direction.getZOffset());
            if (!domain.equals(sourceDomain) || !sourceDomain.equals(targetDomain)) {
                event.setCancelled(true);
                return;
            }
        }
        for (Block destroyed : event.getDestroyedBlocks()) {
            if (!domain.equals(protectionDomain(level, destroyed.getFloorX(), destroyed.getFloorZ()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onLiquidFlow(BlockFromToEvent event) {
        Block from = event.getFrom();
        Block to = event.getTo();
        if (from == null || to == null) return;
        String sourceDomain = protectionDomain(from.getLevel(), from.getFloorX(), from.getFloorZ());
        String targetDomain = protectionDomain(to.getLevel(), to.getFloorX(), to.getFloorZ());
        if (!sourceDomain.equals(targetDomain)
                && (!sourceDomain.isEmpty() || !targetDomain.isEmpty())) {
            event.setCancelled(true);
        }
    }

    /** Prevent entities from changing blocks inside protected areas (e.g. falling blocks). */
    @EventHandler
    public void onEntityBlockChange(EntityBlockChangeEvent event) {
        Block from = event.getFrom();
        Block to = event.getTo();
        if (flagDeniedAt(from.getLevel(), from.getFloorX(), from.getFloorZ(), "mob-grief")
                || flagDeniedAt(to.getLevel(), to.getFloorX(), to.getFloorZ(), "mob-grief")) {
            event.setCancelled(true);
        }
    }

    /** Mobs cannot trigger physical block interactions such as pressure plates in claims. */
    @EventHandler
    public void onEntityInteract(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block != null && flagDeniedAt(block.getLevel(), block.getFloorX(), block.getFloorZ(), "mob-grief")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (flagDeniedAt(block.getLevel(), block.getFloorX(), block.getFloorZ(), "fire")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (flagDeniedAt(block.getLevel(), block.getFloorX(), block.getFloorZ(), "fire")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || !(event.getDamager() instanceof Player attacker)) return;
        if (flagDeniedAt(victim.getLevel(), victim.getFloorX(), victim.getFloorZ(), "pvp")
                || flagDeniedAt(attacker.getLevel(), attacker.getFloorX(), attacker.getFloorZ(), "pvp")) {
            event.setCancelled(true);
            warn(attacker, "§cPvP отключено флагом региона.");
        }
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
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (block == null) return;
        Item hand = event.getItem();
        if (hand != null && hand.getId() == 271
                && (event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK
                || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)) {
            int[] p = selections.computeIfAbsent(player.getUniqueId(),
                    ignored -> new int[]{Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0});
            if (event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
                p[0] = block.getFloorX(); p[1] = block.getFloorZ();
                player.sendMessage(PREFIX + "§aТочка 1: §f" + p[0] + ", " + p[1]);
            } else {
                p[2] = block.getFloorX(); p[3] = block.getFloorZ();
                player.sendMessage(PREFIX + "§aТочка 2: §f" + p[2] + ", " + p[3]);
            }
            showSelectionBorder(player, p);
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        boolean container = block.getLevel().getBlockEntity(block) instanceof InventoryHolder;
        boolean risky = hand != null && (hand.getId() == BUCKET || hand.getId() == FLINT_AND_STEEL);
        if (!container && !risky) return;
        if (!allowed(player, block.getLevel(), block.getFloorX(), block.getFloorZ())) event.setCancelled(true);
    }

    // ---- Commands ----

    boolean handle(CommandSender sender, String cmd, String[] args) {
        if (!"rg".equals(cmd)) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игроку.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            openMenu(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            help(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("flag")) {
            setFlag(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (args[0].equalsIgnoreCase("expand")) {
            expand(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (args[0].equalsIgnoreCase("transfer")) {
            transfer(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (args[0].equalsIgnoreCase("coowner") || args[0].equalsIgnoreCase("member")) {
            manageCoOwner(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (args[0].equalsIgnoreCase("wand")) {
            player.getInventory().addItem(Item.get(271, 0, 1));
            player.sendMessage(PREFIX + "§aТопор выделения: §eЛКМ §7— точка 1, §eПКМ §7— точка 2.");
            return true;
        }
        if (args[0].equalsIgnoreCase("pos1") || args[0].equalsIgnoreCase("pos2")) {
            int[] p = selections.computeIfAbsent(player.getUniqueId(),
                    ignored -> new int[]{Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0});
            int i = args[0].equalsIgnoreCase("pos1") ? 0 : 2;
            p[i] = player.getFloorX(); p[i + 1] = player.getFloorZ();
            player.sendMessage(PREFIX + "§aТочка " + (i == 0 ? "1" : "2") + " установлена по твоей позиции.");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "claim", "create" -> claim(player, subArgs);
            case "unclaim", "remove" -> unclaim(player);
            case "list", "claims" -> listClaims(player);
            case "trust" -> trust(player, subArgs);
            case "untrust" -> untrust(player, subArgs);
            case "info" -> info(player);
            default -> help(player);
        }
        return true;
    }

    private void openMenu(Player player) {
        NEXORARegions.choose(player, "§l§6NEXORA • РЕГИОНЫ",
                "§7Выдели территорию деревянным топором: ЛКМ — точка 1, ПКМ — точка 2.",
                List.of("§aСоздать регион по выделению", "§cСнять приват здесь", "§bИнформация",
                        "§eМои регионы", "§dДоверенные игроки", "§6Расширить регион",
                        "§bСовладельцы и передача", "§7Инструкция"),
                index -> {
                    switch (index) {
                        case 0 -> claim(player, new String[0]);
                        case 1 -> unclaim(player);
                        case 2 -> info(player);
                        case 3 -> listClaims(player);
                        case 4 -> openTrustMenu(player);
                        case 5 -> {
                            player.sendMessage(PREFIX + "§e/rg expand <north|south|east|west> <блоки>");
                            player.sendMessage("§7Расширяет регион в выбранную сторону за монеты.");
                            player.sendMessage("§7Цена: §e" + plugin.getConfig().getLong("regions.expand-price-per-block", 0L)
                                    + " §7монет за каждый добавленный блок.");
                        }
                        case 6 -> {
                            player.sendMessage(PREFIX + "§e/rg coowner add <игрок> §7— добавить совладельца текущего региона.");
                            player.sendMessage(PREFIX + "§e/rg coowner remove <игрок> §7— убрать совладельца.");
                            player.sendMessage(PREFIX + "§e/rg coowner list §7— список совладельцев.");
                            player.sendMessage(PREFIX + "§e/rg transfer <игрок> §7— передать регион.");
                        }
                        case 7 -> help(player);
                    }
                });
    }

    private void openTrustMenu(Player player) {
        NEXORARegions.choose(player, "§l§dДОВЕРЕННЫЕ ИГРОКИ", "§7Доступ ко всем твоим регионам.",
                List.of("§aДобавить игрока онлайн", "§cЗабрать доступ", "§eНазад"), index -> {
                    if (index == 0) openOnlineTrustMenu(player);
                    else if (index == 1) openTrustedPlayersMenu(player);
                    else openMenu(player);
                });
    }

    private void openOnlineTrustMenu(Player player) {
        List<Player> online = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Player target : plugin.getServer().getOnlinePlayers().values()) {
            if (target.getUniqueId().equals(player.getUniqueId())) continue;
            online.add(target); labels.add("§a" + target.getName());
        }
        if (online.isEmpty()) { player.sendMessage(PREFIX + "§7Нет других игроков онлайн."); openTrustMenu(player); return; }
        NEXORARegions.choose(player, "§l§aВЫБОР ИГРОКА", "§7Кому выдать доступ?", labels, index -> {
            trustTarget(player, online.get(index)); openTrustMenu(player);
        });
    }

    private void openTrustedPlayersMenu(Player player) {
        String section = "trust." + player.getUniqueId();
        List<String> ids = new ArrayList<>(store.cfg.getSection(section).getKeys(false));
        if (ids.isEmpty()) { player.sendMessage(PREFIX + "§7Список доверенных пуст."); openTrustMenu(player); return; }
        List<String> labels = new ArrayList<>();
        for (String id : ids) labels.add("§c" + store.cfg.getString(section + "." + id, id));
        NEXORARegions.choose(player, "§l§cСПИСОК ДОСТУПА", "§7Кого удалить из списка?", labels, index -> {
            untrustId(player, ids.get(index)); openTrustedPlayersMenu(player);
        });
    }

    private void help(Player player) {
        player.sendMessage("§6§l✦ NEXORA • Регион");
        player.sendMessage("§e/rg wand §8— §7получить деревянный топор выделения");
        player.sendMessage("§eЛКМ / ПКМ §8— §7отметить точки 1 и 2");
        player.sendMessage("§e/rg pos1 и /rg pos2 §8— §7отметить точки по своей позиции");
        player.sendMessage("§e/rg create [имя] §8— §7создать регион по выделению");
        player.sendMessage("§e/rg unclaim §8— §7освободить регион под ногами");
        player.sendMessage("§e/rg list §8— §7список твоих приватов");
        player.sendMessage("§e/rg trust <игрок> §8— §7дать доступ к строительству");
        player.sendMessage("§e/rg untrust <игрок> §8— §7забрать доступ");
        player.sendMessage("§e/rg info §8— §7информация о регионе");
        player.sendMessage("§e/rg flag §8— §7список флагов и их описание");
        player.sendMessage("§e/rg flag <флаг> <allow|deny> §8— §7настроить флаг региона");
        player.sendMessage("§e/rg expand <north|south|east|west> <блоки> §8— §7расширить регион за монеты");
        player.sendMessage("§e/rg transfer <игрок> §8— §7передать регион новому владельцу");
        player.sendMessage("§e/rg coowner add <игрок> §8— §7добавить совладельца текущего региона");
        player.sendMessage("§e/rg coowner remove <игрок> §8— §7убрать совладельца");
        player.sendMessage("§e/rg coowner list §8— §7список совладельцев");
    }

    private void claim(Player player, String[] args) {
        Level level = player.getLevel();
        int[] p = selections.get(player.getUniqueId());
        if (p == null || p[0] == Integer.MIN_VALUE || p[2] == Integer.MIN_VALUE) {
            player.sendMessage(PREFIX + "§cОтметь две точки: §e/rg wand §c(ЛКМ/ПКМ) или §e/rg pos1 §cи §e/rg pos2.");
            return;
        }
        int minX = Math.min(p[0], p[2]), maxX = Math.max(p[0], p[2]);
        int minZ = Math.min(p[1], p[3]), maxZ = Math.max(p[1], p[3]);
        long area = (long)(maxX - minX + 1) * (maxZ - minZ + 1);
        if (area > 65536L) { player.sendMessage(PREFIX + "§cМаксимальный размер региона — 256×256 блоков."); return; }
        if (overlapsClaim(level, minX, maxX, minZ, maxZ)) {
            player.sendMessage(PREFIX + "§cРегион пересекается с уже существующим приватом."); return;
        }
        if (plugin.inSpawnProtection(level, minX, minZ)
                || plugin.inSpawnProtection(level, maxX, maxZ)
                || plugin.inSpawnProtection(level, (minX + maxX) / 2, (minZ + maxZ) / 2)) {
            player.sendMessage(PREFIX + "§cНельзя приватить территорию возле спавна."); return;
        }
        int limit = plugin.claimLimit(player);
        if (claimCount(player.getUniqueId()) >= limit) {
            player.sendMessage(PREFIX + "§cЛимит приватов: §e" + limit + "§c. Освободи ненужный: §e/rg unclaim");
            return;
        }
        long price = 0L;
        String name = args.length > 0 ? NEXORARegions.clean(String.join("_", args)) : "region";
        if (name.length() > 24) name = name.substring(0, 24);
        String claimKey = "r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String base = "claims." + claimKey;
        store.cfg.set(base + ".owner", player.getUniqueId().toString());
        store.cfg.set(base + ".ownerName", player.getName());
        store.cfg.set(base + ".world", level.getName());
        store.cfg.set(base + ".name", name);
        store.cfg.set(base + ".minX", minX); store.cfg.set(base + ".maxX", maxX);
        store.cfg.set(base + ".minZ", minZ); store.cfg.set(base + ".maxZ", maxZ);
        store.touch();
        selections.remove(player.getUniqueId());
        player.sendMessage(PREFIX + "§aРегион §f" + name + " §aсоздан: §f" + minX + "," + minZ
                + " §7— §f" + maxX + "," + maxZ + "§a. Защищена вся высота.");
    }

    private void expand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§e/rg expand <north|south|east|west> <блоки>");
            player.sendMessage("§7Стороны: north — север, south — юг, east — восток, west — запад.");
            player.sendMessage("§7Стоимость: §e" + plugin.getConfig().getLong("regions.expand-price-per-block", 0L)
                    + " §7монет за каждый добавленный блок.");
            return;
        }
        String direction = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("north", "south", "east", "west").contains(direction)) {
            player.sendMessage(PREFIX + "§cНаправление: north, south, east или west.");
            return;
        }
        int blocks;
        try {
            blocks = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(PREFIX + "§cКоличество блоков должно быть числом.");
            return;
        }
        if (blocks < 1 || blocks > 64) {
            player.sendMessage(PREFIX + "§cЗа одну покупку можно расширить максимум на 64 блока.");
            return;
        }

        String id = claimKeyAt(player.getLevel(), player.getFloorX(), player.getFloorZ());
        if (id.isEmpty()) {
            player.sendMessage(PREFIX + "§cВстань внутрь своего региона, чтобы его расширить.");
            return;
        }
        if (!isClaimOwnerOrAdmin(player, id)) {
            player.sendMessage(PREFIX + "§cРасширять регион может только владелец или администратор.");
            return;
        }

        String base = "claims." + id;
        int minX, maxX, minZ, maxZ;
        if (store.cfg.exists(base + ".minX")) {
            minX = store.cfg.getInt(base + ".minX");
            maxX = store.cfg.getInt(base + ".maxX");
            minZ = store.cfg.getInt(base + ".minZ");
            maxZ = store.cfg.getInt(base + ".maxZ");
        } else {
            minX = store.cfg.getInt(base + ".cx", 0) << 4;
            maxX = minX + 15;
            minZ = store.cfg.getInt(base + ".cz", 0) << 4;
            maxZ = minZ + 15;
        }

        switch (direction) {
            case "north" -> minZ -= blocks;
            case "south" -> maxZ += blocks;
            case "east" -> maxX += blocks;
            case "west" -> minX -= blocks;
        }
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        long oldArea = (long) (store.cfg.exists(base + ".minX")
                ? store.cfg.getInt(base + ".maxX") - store.cfg.getInt(base + ".minX") + 1 : 16)
                * (store.cfg.exists(base + ".minZ")
                ? store.cfg.getInt(base + ".maxZ") - store.cfg.getInt(base + ".minZ") + 1 : 16);
        long newArea = width * depth;
        if (width > 256 || depth > 256 || newArea > 65536L) {
            player.sendMessage(PREFIX + "§cРазмер региона не может превышать 256×256 блоков.");
            return;
        }

        Level level = player.getLevel();
        if (overlapsClaim(level, minX, maxX, minZ, maxZ, id)) {
            player.sendMessage(PREFIX + "§cРасширение пересекается с другим регионом.");
            return;
        }
        if (plugin.inSpawnProtection(level, minX, minZ)
                || plugin.inSpawnProtection(level, maxX, maxZ)
                || plugin.inSpawnProtection(level, (minX + maxX) / 2, (minZ + maxZ) / 2)) {
            player.sendMessage(PREFIX + "§cНельзя расширять регион в защищённую зону спавна.");
            return;
        }

        long addedBlocks = newArea - oldArea;
        long rate = 0L;
        if (rate > 0 && addedBlocks > Long.MAX_VALUE / rate) {
            player.sendMessage(PREFIX + "§cСтоимость расширения слишком велика.");
            return;
        }
        long price = addedBlocks * rate;

        store.cfg.set(base + ".minX", minX);
        store.cfg.set(base + ".maxX", maxX);
        store.cfg.set(base + ".minZ", minZ);
        store.cfg.set(base + ".maxZ", maxZ);
        store.cfg.set(base + ".world", level.getName());
        store.touch();
        player.sendMessage(PREFIX + "§aРегион расширен на §f" + blocks + " §aблоков в сторону §f"
                + direction.toUpperCase(Locale.ROOT) + "§a. Добавлено блоков: §f" + addedBlocks
                + "§a. Стоимость: " + NEXORARegions.money(0L) + "§a.");
    }

    private void transfer(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(PREFIX + "§e/rg transfer <игрок>");
            return;
        }
        String id = claimKeyAt(player.getLevel(), player.getFloorX(), player.getFloorZ());
        if (id.isEmpty()) {
            player.sendMessage(PREFIX + "§cВстань внутрь региона, который хочешь передать.");
            return;
        }
        if (!ownerOf(id).equals(player.getUniqueId().toString()) && !NEXORARegions.isAdmin(player)) {
            player.sendMessage(PREFIX + "§cПередать регион может только его владелец или администратор.");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(PREFIX + "§cИгрок должен быть онлайн, чтобы передать ему регион.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cТы уже владелец этого региона.");
            return;
        }
        String base = "claims." + id;
        store.cfg.set(base + ".owner", target.getUniqueId().toString());
        store.cfg.set(base + ".ownerName", target.getName());
        store.cfg.remove(base + ".members." + target.getUniqueId());
        store.touch();
        player.sendMessage(PREFIX + "§aРегион §f" + store.cfg.getString(base + ".name", "region")
                + " §aпередан игроку §f" + target.getName() + "§a.");
        target.sendMessage(PREFIX + "§aТеперь ты владелец региона §f"
                + store.cfg.getString(base + ".name", "region") + "§a.");
    }

    private void manageCoOwner(Player player, String[] args) {
        String id = claimKeyAt(player.getLevel(), player.getFloorX(), player.getFloorZ());
        if (id.isEmpty()) {
            player.sendMessage(PREFIX + "§cВстань внутрь своего региона.");
            return;
        }
        if (!ownerOf(id).equals(player.getUniqueId().toString()) && !NEXORARegions.isAdmin(player)) {
            player.sendMessage(PREFIX + "§cУправлять совладельцами может только владелец или администратор.");
            return;
        }
        String base = "claims." + id + ".members";
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            List<String> names = new ArrayList<>();
            for (String uuid : store.cfg.getSection(base).getKeys(false)) {
                names.add(store.cfg.getString(base + "." + uuid, uuid));
            }
            player.sendMessage(PREFIX + "§bСовладельцы региона: §f"
                    + (names.isEmpty() ? "нет" : String.join("§7, §f", names)));
            return;
        }
        if (!action.equals("add") && !action.equals("remove")) {
            player.sendMessage(PREFIX + "§e/rg coowner <add|remove|list> [игрок]");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§e/rg coowner " + action + " <игрок>");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(PREFIX + "§cИгрок должен быть онлайн.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())
                || target.getUniqueId().toString().equals(ownerOf(id))) {
            player.sendMessage(PREFIX + "§cНельзя добавить владельца в совладельцы.");
            return;
        }
        String memberPath = base + "." + target.getUniqueId();
        if (action.equals("add")) {
            if (store.cfg.exists(memberPath)) {
                player.sendMessage(PREFIX + "§eЭтот игрок уже совладелец.");
                return;
            }
            if (store.cfg.getSection(base).getKeys(false).size() >= 10) {
                player.sendMessage(PREFIX + "§cВ одном регионе может быть не больше 10 совладельцев.");
                return;
            }
            store.cfg.set(memberPath, target.getName());
            store.touch();
            player.sendMessage(PREFIX + "§a" + target.getName() + " добавлен в совладельцы региона.");
            target.sendMessage(PREFIX + "§aТебя назначили совладельцем региона §f"
                    + store.cfg.getString("claims." + id + ".name", "region") + "§a.");
        } else {
            if (!store.cfg.exists(memberPath)) {
                player.sendMessage(PREFIX + "§cЭтот игрок не является совладельцем.");
                return;
            }
            store.cfg.remove(memberPath);
            store.touch();
            player.sendMessage(PREFIX + "§e" + target.getName() + " больше не совладелец региона.");
            target.sendMessage(PREFIX + "§cТвои права совладельца региона отозваны.");
        }
    }

    private void unclaim(Player player) {
        Level level = player.getLevel();
        String claimKey = claimKeyAt(level, player.getFloorX(), player.getFloorZ());
        String owner = claimKey.isEmpty() ? "" : ownerOf(claimKey);
        if (owner.isEmpty()) {
            player.sendMessage(PREFIX + "§7Этот участок никому не принадлежит.");
            return;
        }
        boolean mine = owner.equals(player.getUniqueId().toString());
        if (!mine && !NEXORARegions.isAdmin(player)) {
            player.sendMessage(PREFIX + "§cЭто не твой участок.");
            return;
        }
        store.cfg.remove("claims." + claimKey);
        store.touch();
        long refund = 0L;
        player.sendMessage(PREFIX + "§aУчасток освобождён." + (mine && refund > 0
                ? " Возвращено: " + NEXORARegions.money(refund) : ""));
    }

    private void listClaims(Player player) {
        List<String> rows = new ArrayList<>();
        ConfigSection all = store.cfg.getSection("claims");
        for (String k : all.getKeys(false)) {
            if (!ownerOf(k).equals(player.getUniqueId().toString())) continue;
            String base = "claims." + k;
            if (store.cfg.exists(base + ".minX")) rows.add(store.cfg.getString(base + ".name", "region")
                    + " §8[§f" + store.cfg.getInt(base + ".minX") + "," + store.cfg.getInt(base + ".minZ")
                    + " §7— §f" + store.cfg.getInt(base + ".maxX") + "," + store.cfg.getInt(base + ".maxZ") + "§8]");
            else rows.add(store.cfg.getString(base + ".world", "?") + " §8(§f"
                    + store.cfg.getInt(base + ".cx", 0) + "§8, §f" + store.cfg.getInt(base + ".cz", 0) + "§8)");
        }
        player.sendMessage(PREFIX + "§7Твои участки (" + rows.size() + "/" + plugin.claimLimit(player) + "):");
        for (String row : rows) player.sendMessage("§8• §f" + row);
        if (rows.isEmpty()) player.sendMessage("§7Пока нет. Встань на нужный участок и напиши §e/rg claim");
    }

    private void trust(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(PREFIX + "§e/rg trust <игрок>");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(PREFIX + "§cИгрок не найден или не в сети.");
            return;
        }
        trustTarget(player, target);
    }

    private void trustTarget(Player player, Player target) {
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cНельзя добавить самого себя.");
            return;
        }
        String path = "trust." + player.getUniqueId() + "." + target.getUniqueId();
        if (store.cfg.exists(path)) {
            player.sendMessage(PREFIX + "§e" + target.getName() + " §7уже есть в списке доверенных.");
            return;
        }
        store.cfg.set(path, target.getName());
        store.touch();
        player.sendMessage(PREFIX + "§f" + target.getName() + " §aможет строить на всех твоих участках.");
        target.sendMessage(PREFIX + "§f" + player.getName() + " §aдал тебе право строить на своих участках.");
    }

    private void untrust(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(PREFIX + "§e/rg untrust <игрок>");
            return;
        }
        String section = "trust." + player.getUniqueId();
        for (String id : store.cfg.getSection(section).getKeys(false)) {
            String name = store.cfg.getString(section + "." + id, "");
            if (name.equalsIgnoreCase(args[0])) {
                untrustId(player, id);
                return;
            }
        }
        player.sendMessage(PREFIX + "§cТакого игрока нет в твоём списке доверенных.");
    }

    private void untrustId(Player player, String id) {
        String section = "trust." + player.getUniqueId();
        String name = store.cfg.getString(section + "." + id, id);
        if (!store.cfg.exists(section + "." + id)) return;
        store.cfg.remove(section + "." + id);
        store.touch();
        player.sendMessage(PREFIX + "§f" + name + " §7больше не может строить на твоих участках.");
    }

    private void info(Player player) {
        String claimKey = claimKeyAt(player.getLevel(), player.getFloorX(), player.getFloorZ());
        String owner = claimKey.isEmpty() ? "" : ownerOf(claimKey);
        if (owner.isEmpty()) {
            player.sendMessage(PREFIX + "§7Участок свободен. Занять: §e/rg claim");
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : store.cfg.getSection("trust." + owner).getKeys(false)) {
            names.add(store.cfg.getString("trust." + owner + "." + id, "?"));
        }
        List<String> members = new ArrayList<>();
        for (String uuid : store.cfg.getSection("claims." + claimKey + ".members").getKeys(false)) {
            members.add(store.cfg.getString("claims." + claimKey + ".members." + uuid, uuid));
        }
        player.sendMessage(PREFIX + "§7Регион: §f" + store.cfg.getString("claims." + claimKey + ".name", "region"));
        player.sendMessage("§7Хозяин: §f" + store.cfg.getString("claims." + claimKey + ".ownerName", "?"));
        if (!members.isEmpty()) player.sendMessage("§bСовладельцы: §f" + String.join("§7, §f", members));
        if (!names.isEmpty()) player.sendMessage("§7Доверенные: §f" + String.join("§7, §f", names));
    }
}
