package ru.nexora.dungeons;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.math.Vector3;
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
        final Position arenaCenter;
        final Set<Long> alive = new HashSet<>();
        final Map<Long, Entity> entities = new HashMap<>();
        int wave = 0;
        Run(Player player, Difficulty difficulty, Position arenaCenter) {
            this.player=player;
            this.difficulty=difficulty;
            this.arenaCenter=new Position(arenaCenter.x, arenaCenter.y, arenaCenter.z, arenaCenter.getLevel());
        }
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


    // Builds a large, overgrown abandoned mine around the administrator.
    private void put(cn.nukkit.level.Level level, int x, int y, int z, int id, int meta) {
        level.setBlock(new Vector3(x, y, z), Block.get(id, meta), false, false);
    }

    private void buildMine(Player p) {
        cn.nukkit.level.Level level = p.getLevel();
        int cx = p.getFloorX(), cy = p.getFloorY(), cz = p.getFloorZ();
        Random random = new Random(7319L);
        p.sendMessage("§6✦ NEXORA §8» §eСтрою заброшенную шахту... Это может занять несколько секунд.");
        // Hollow cavern, uneven rocky floor, rough perimeter and a broken ceiling.
        for (int x=-24; x<=24; x++) for (int z=-24; z<=24; z++) {
            double d = Math.sqrt(x*x + z*z);
            if (d > 24) continue;
            int wx=cx+x, wz=cz+z;
            for (int y=0; y<=9; y++) put(level,wx,cy+y,wz,0,0);
            int floor = random.nextInt(100)<13 ? 48 : (random.nextInt(100)<10 ? 13 : 4);
            put(level,wx,cy-1,wz,floor,0);
            if (d >= 21.5) {
                int height=7+random.nextInt(3);
                for (int h=0;h<height;h++) {
                    int rock=random.nextInt(100)<18?48:(random.nextBoolean()?1:4);
                    put(level,wx,cy+h,wz,rock,0);
                }
                if (random.nextInt(100)<72) put(level,wx,cy+9,wz,random.nextInt(100)<20?48:1,0);
                if (random.nextInt(100)<12) put(level,wx,cy+8,wz,89,0);
            } else if (random.nextInt(100)<3) {
                put(level,wx,cy-1,wz,2,0);
                put(level,wx,cy,wz,31,1);
            }
        }
        // Support pillars and crossbeams.
        for (int sx : new int[]{-15,-5,5,15}) for (int sz : new int[]{-15,0,15}) {
            for(int h=0;h<7;h++) {
                put(level,cx+sx,cy+h,cz+sz,17,0);
                put(level,cx+sx+1,cy+h,cz+sz,17,0);
            }
            for(int dx=0;dx<3;dx++) put(level,cx+sx+dx,cy+6,cz+sz,17,0);
            put(level,cx+sx,cy+7,cz+sz,5,0);
            put(level,cx+sx+1,cy+7,cz+sz,5,0);
        }
        // Rusty-looking abandoned rail line with wooden sleepers.
        for(int x=-17;x<=17;x++) {
            if (x%3==0) put(level,cx+x,cy-1,cz-8,5,0);
            put(level,cx+x,cy,cz-8,66,0);
            if (x%3==0) {
                put(level,cx+x,cy,cz-7,5,0);
                put(level,cx+x,cy,cz-9,5,0);
            }
        }
        // Broken mineshaft entrance arch and side tunnel.
        for(int z=-23;z<=-17;z++) {
            put(level,cx-4,cy,cz+z,17,0); put(level,cx+4,cy,cz+z,17,0);
            put(level,cx-4,cy+1,cz+z,17,0); put(level,cx+4,cy+1,cz+z,17,0);
            if(z%2==0) for(int x=-4;x<=4;x++) put(level,cx+x,cy+3,cz+z,5,0);
        }
        // Abandoned miner camp: platform, tent frame, crates and lantern-like glow.
        for(int x=-13;x<=-7;x++) for(int z=9;z<=15;z++) put(level,cx+x,cy-1,cz+z,5,0);
        for(int x : new int[]{-13,-7}) for(int z : new int[]{9,15}) {
            for(int h=0;h<3;h++) put(level,cx+x,cy+h,cz+z,85,0);
            put(level,cx+x,cy+3,cz+z,89,0);
        }
        for(int x=-12;x<=-8;x++) {
            put(level,cx+x,cy+2,cz+9,35,7);
            put(level,cx+x,cy+2,cz+15,35,7);
        }
        put(level,cx-10,cy,cz+11,54,0); put(level,cx-9,cy,cz+11,54,0);
        put(level,cx-11,cy+1,cz+12,50,5);
        // Underground pool and luminous mushroom grove.
        for(int x=10;x<=16;x++) for(int z=8;z<=14;z++) {
            if((x-13)*(x-13)+(z-11)*(z-11)<=9) put(level,cx+x,cy-1,cz+z,9,0);
        }
        for(int i=0;i<18;i++) {
            int x=10+random.nextInt(8), z=7+random.nextInt(9);
            put(level,cx+x,cy,cz+z,random.nextBoolean()?39:40,0);
            if(random.nextInt(3)==0) put(level,cx+x,cy+1,cz+z,89,0);
        }
        // Hanging roots, vines, cobwebs and patches of vegetation.
        for(int i=0;i<95;i++) {
            int x=random.nextInt(43)-21, z=random.nextInt(43)-21;
            if(x*x+z*z>400) continue;
            int y=random.nextInt(5)+3;
            if(random.nextBoolean()) put(level,cx+x,cy+y,cz+z,18,random.nextInt(4));
            else put(level,cx+x,cy+y,cz+z,106,random.nextInt(4));
        }
        for(int i=0;i<24;i++) {
            int x=random.nextInt(39)-19,z=random.nextInt(39)-19;
            put(level,cx+x,cy+1,cz+z,30,0);
        }
        // Ore seams exposed in the cave walls.
        int[] ores={16,15,14,21,56};
        for(int i=0;i<42;i++) {
            double a=random.nextDouble()*Math.PI*2;
            int x=(int)Math.round(Math.cos(a)*22), z=(int)Math.round(Math.sin(a)*22);
            put(level,cx+x,cy+1+random.nextInt(4),cz+z,ores[random.nextInt(ores.length)],0);
        }
        // Torches along the route.
        for(int x=-18;x<=18;x+=6) {
            put(level,cx+x,cy+3,cz-12,50,5);
            put(level,cx+x,cy+3,cz+12,50,5);
        }
        savePosition("spawn", p);
        p.sendMessage("§aШахта готова! §7Точка входа сохранена здесь.");
        p.sendMessage("§7Теперь поставь точку выхода и выполни §e/dungeon setexit§7.");
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
        Run run = new Run(p, difficulty, spawn);
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
            // Always spawn relative to the saved arena center, never the player's
            // moving position. This keeps every wave inside the mine walls.
            double angle = (Math.PI * 2 * i) / Math.max(1, count);
            double radius = bossWave ? 0.0 : Math.min(7.0, 4.5 + count * 0.25);
            Position center = run.arenaCenter;
            Position pos = new Position(center.x + Math.cos(angle) * radius,
                    center.y, center.z + Math.sin(angle) * radius, center.getLevel());
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
            run.entities.put(mob.getId(), mob);
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
            awardBeastXp(run.player, run.difficulty.rewardXp / 2);
        } else run.player.sendMessage("§cПодземелье завершено без награды.");
        Position exit = readPosition("exit");
        if (exit != null) run.player.teleport(exit);
    }

    /** Gives XP to the player's active beast team. Does nothing when NEXORA_Beasts is not installed. */
    private void awardBeastXp(Player player, int amount) {
        var beasts = getServer().getPluginManager().getPlugin("NEXORA_Beasts");
        if (beasts == null || !beasts.isEnabled()) return;
        try {
            beasts.getClass().getMethod("awardTeamXp", Player.class, int.class).invoke(beasts, player, amount);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getLogger().warning("Не удалось начислить опыт питомцам: " + ex);
        }
    }

    private void cleanupMobs(Run run) {
        for (Map.Entry<Long, Entity> entry : new HashMap<>(run.entities).entrySet()) {
            Entity entity = entry.getValue();
            if (entity != null && !entity.isClosed()) entity.close();
            mobOwners.remove(entry.getKey());
        }
        run.entities.clear();
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
                case "buildmine" -> { buildMine(p); return true; }
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
                default -> { p.sendMessage("§e/dungeon §7| §e/dungeon buildmine §7| §e/dungeon setspawn §7| §e/dungeon setexit §7| §e/dungeon leave"); return true; }
            }
        }
        if (!p.hasPermission("nexora.dungeons.use")) { p.sendMessage("§cНет прав."); return true; }
        openMenu(p);
        return true;
    }
}
