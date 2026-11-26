package ru.nexora.dungeons;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NEXORADungeons extends PluginBase implements Listener {
    private Config config;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    private final Map<Long, Run> mobOwners = new ConcurrentHashMap<>();

    private static final class Difficulty {
        final String id, title;
        final int waves, baseMobs, rewardXp, rewardGold, health;
        Difficulty(String id, String title, int waves, int baseMobs, int rewardXp, int rewardGold, int health) {
            this.id=id; this.title=title; this.waves=waves; this.baseMobs=baseMobs;
            this.rewardXp=rewardXp; this.rewardGold=rewardGold; this.health=health;
        }
    }
    private static final Difficulty EASY = new Difficulty("easy","§aЛёгкая",3,2,80,3,20);
    private static final Difficulty NORMAL = new Difficulty("normal","§eОбычная",5,3,160,7,30);
    private static final Difficulty HARD = new Difficulty("hard","§cИспытание",7,4,300,15,45);

    private static final class Run {
        final Player player;
        final Difficulty difficulty;
        final Set<Long> alive = new HashSet<>();
        int wave = 0;
        Run(Player player, Difficulty difficulty) { this.player=player; this.difficulty=difficulty; }
    }

    @Override public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку NEXORA_Dungeons.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        config = new Config(new File(getDataFolder(), "config.yml"), Config.YAML);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NEXORA Dungeons 1.0.0 загружен. Используй /dungeon.");
    }

    @Override public void onDisable() {
        for (Run run : new ArrayList<>(runs.values())) cleanup(run);
        runs.clear();
        mobOwners.clear();
        if (config != null) config.save();
    }

    private Position readPosition(String key) {
        if (!config.exists(key + ".world")) return null;
        String world = config.getString(key + ".world");
        if (!getServer().isLevelLoaded(world)) getServer().loadLevel(world);
        var level = getServer().getLevelByName(world);
        if (level == null) return null;
        return new Position(config.getDouble(key + ".x"), config.getDouble(key + ".y"),
                config.getDouble(key + ".z"), level);
    }

    private void savePosition(String key, Player p) {
        config.set(key + ".world", p.getLevel().getName());
        config.set(key + ".x", p.getX());
        config.set(key + ".y", p.getY());
        config.set(key + ".z", p.getZ());
        config.save();
        p.sendMessage("§6✦ NEXORA §8» §aТочка «" + key + "» сохранена.");
    }

    private void openMenu(Player p) {
        FormWindowSimple form = new FormWindowSimple("§l§0✦ NEXORA · ПОДЗЕМЕЛЬЯ",
                "§8━━━━━━━━━━━━━━━━━━\n§bИСПЫТАНИЕ ГЕРОЯ\n"
                + "§7Пройди волны монстров и одолей финального босса.\n"
                + "§8Чем выше сложность — тем ценнее награда.\n\n"
                + "§aЛЁГКАЯ §8· §f3 волны\n§eОБЫЧНАЯ §8· §f5 волн\n§cИСПЫТАНИЕ §8· §f7 волн\n"
                + "§8━━━━━━━━━━━━━━━━━━\n§7Выбери уровень сложности:");
        form.addButton(new ElementButton("§l§a⚔ ЛЁГКОЕ ПОДЗЕМЕЛЬЕ\n§8Для начинающих"));
        form.addButton(new ElementButton("§l§e⚔ ОБЫЧНОЕ ПОДЗЕМЕЛЬЕ\n§8Настоящее испытание"));
        form.addButton(new ElementButton("§l§c☠ СЛОЖНОЕ ПОДЗЕМЕЛЬЕ\n§8Для подготовленных"));
        form.addButton(new ElementButton("§7✕ ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            switch (form.getResponse().getClickedButtonId()) {
                case 0 -> start(p, EASY);
                case 1 -> start(p, NORMAL);
                case 2 -> start(p, HARD);
                default -> { }
            }
        }));
        p.showFormWindow(form);
    }

    private void start(Player p, Difficulty difficulty) {
        if (runs.containsKey(p.getUniqueId())) {
            p.sendMessage("§cТы уже проходишь подземелье. Используй /dungeon leave.");
            return;
        }
        Position spawn = readPosition("spawn");
        if (spawn == null) {
            p.sendMessage("§cПодземелье ещё не настроено. Администратор должен выполнить /dungeon setspawn и /dungeon setexit.");
            return;
        }
        Run run = new Run(p, difficulty);
        runs.put(p.getUniqueId(), run);
        p.teleport(spawn);
        p.sendMessage("§6✦ NEXORA §8» §fПодземелье: " + difficulty.title);
        p.sendMessage("§7Побеждай всех врагов каждой волны. §eВыход: /dungeon leave");
        spawnWave(run);
    }

    private void spawnWave(Run run) {
        if (!run.player.isOnline()) { cleanup(run); return; }
        run.wave++;
        run.alive.clear();
        boolean bossWave = run.wave == run.difficulty.waves;
        int count = bossWave ? 1 : run.difficulty.baseMobs + (run.wave - 1);
        run.player.sendMessage("§8━━━━━━━━━━━━━━━━━━");
        run.player.sendMessage(bossWave ? "§c☠ ФИНАЛЬНЫЙ БОСС! §7Волна " + run.wave + "/" + run.difficulty.waves
                : "§b⚔ ВОЛНА " + run.wave + "/" + run.difficulty.waves + " §7· Врагов: " + count);
        for (int i=0; i<count; i++) {
            String type = bossWave ? "IronGolem" : switch ((run.wave + i) % 3) {
                case 0 -> "Zombie"; case 1 -> "Skeleton"; default -> "Spider";
            };
            double angle = (Math.PI * 2 * i) / Math.max(1, count);
            Position pos = new Position(run.player.getX() + Math.cos(angle) * 4,
                    run.player.getY(), run.player.getZ() + Math.sin(angle) * 4, run.player.getLevel());
            Entity mob = Entity.createEntity(type, pos);
            if (mob == null) {
                run.player.sendMessage("§cНе удалось создать противника: " + type);
                continue;
            }
            if (bossWave) {
                mob.setNameTag("§c☠ ПОВЕЛИТЕЛЬ ПОДЗЕМЕЛЬЯ");
                mob.setNameTagVisible(true);
                mob.setNameTagAlwaysVisible(true);
                mob.setMaxHealth(run.difficulty.health * 4);
                mob.setHealth(run.difficulty.health * 4);
            } else {
                mob.setNameTag("§cСтраж подземелья");
                mob.setNameTagVisible(true);
            }
            mob.spawnToAll();
            run.alive.add(mob.getId());
            mobOwners.put(mob.getId(), run);
        }
        if (run.alive.isEmpty()) {
            run.player.sendMessage("§cВ этой волне не удалось создать врагов. Подземелье остановлено.");
            finish(run, false);
        }
    }

    @EventHandler public void onMobDeath(EntityDeathEvent event) {
        long id = event.getEntity().getId();
        Run run = mobOwners.remove(id);
        if (run == null || !runs.containsKey(run.player.getUniqueId())) return;
        run.alive.remove(id);
        if (run.alive.isEmpty()) {
            if (run.wave >= run.difficulty.waves) finish(run, true);
            else getServer().getScheduler().scheduleDelayedTask(this, () -> {
                if (runs.get(run.player.getUniqueId()) == run) spawnWave(run);
            }, 40);
        }
    }

    private void finish(Run run, boolean success) {
        cleanupMobs(run);
        runs.remove(run.player.getUniqueId());
        if (!run.player.isOnline()) return;
        if (success) {
            run.player.addExperience(run.difficulty.rewardXp);
            run.player.getInventory().addItem(Item.get(266, 0, run.difficulty.rewardGold));
            run.player.sendMessage("§6✦ NEXORA §8» §aПОДЗЕМЕЛЬЕ ПРОЙДЕНО!");
            run.player.sendMessage("§7Награда: §b+" + run.difficulty.rewardXp + " XP §8и §6+" + run.difficulty.rewardGold + " золотых слитков§7.");
        } else run.player.sendMessage("§cПодземелье завершено без награды.");
        Position exit = readPosition("exit");
        if (exit != null) run.player.teleport(exit);
    }

    private void cleanupMobs(Run run) {
        for (Long id : new HashSet<>(run.alive)) {
            Entity entity = getServer().getEntity(id);
            if (entity != null && !entity.isClosed()) entity.close();
            mobOwners.remove(id);
        }
        run.alive.clear();
    }

    private void cleanup(Run run) {
        cleanupMobs(run);
        runs.remove(run.player.getUniqueId());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Run run = runs.get(event.getPlayer().getUniqueId());
        if (run != null) cleanup(run);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cКоманда доступна только игроку."); return true;
        }
        if (args.length > 0) {
            if (!p.hasPermission("nexora.dungeons.admin") && !p.isOp()) {
                p.sendMessage("§cНедостаточно прав."); return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "setspawn" -> { savePosition("spawn", p); return true; }
                case "setexit" -> { savePosition("exit", p); return true; }
                case "leave" -> {
                    Run run = runs.get(p.getUniqueId());
                    if (run == null) { p.sendMessage("§7Ты сейчас не в подземелье."); return true; }
                    cleanup(run);
                    Position exit = readPosition("exit");
                    if (exit != null) p.teleport(exit);
                    p.sendMessage("§eТы покинул подземелье. Награда не выдана.");
                    return true;
                }
                default -> { p.sendMessage("§e/dungeon §7| §e/dungeon setspawn §7| §e/dungeon setexit §7| §e/dungeon leave"); return true; }
            }
        }
        if (!p.hasPermission("nexora.dungeons.use")) { p.sendMessage("§cНет прав."); return true; }
        openMenu(p);
        return true;
    }
}
