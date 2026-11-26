package ru.nexora.raids;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
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
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NEXORARaids extends PluginBase implements Listener {
    private Config config;
    private final Map<UUID, RaidRun> runs = new ConcurrentHashMap<>();
    private final Map<Long, RaidRun> owners = new ConcurrentHashMap<>();

    private static final class Difficulty {
        final String id, title;
        final int hp, xp, diamonds, adds;
        Difficulty(String id, String title, int hp, int xp, int diamonds, int adds) {
            this.id=id; this.title=title; this.hp=hp; this.xp=xp; this.diamonds=diamonds; this.adds=adds;
        }
    }
    private static final Difficulty STORY = new Difficulty("story","§aСюжетный", 100, 250, 2, 2);
    private static final Difficulty HERO = new Difficulty("hero","§eГероический", 180, 500, 4, 3);
    private static final Difficulty NIGHTMARE = new Difficulty("nightmare","§cКошмар", 300, 900, 8, 4);

    private static final class RaidRun {
        final Player player;
        final Difficulty difficulty;
        final Position center;
        final Map<Long, Entity> mobs = new HashMap<>();
        final Set<Long> alive = new HashSet<>();
        int stage = 0;
        RaidRun(Player player, Difficulty difficulty, Position center) {
            this.player=player; this.difficulty=difficulty;
            this.center=new Position(center.x, center.y, center.z, center.getLevel());
        }
    }

    @Override public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку NEXORA_Raids.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        config = new Config(new File(getDataFolder(), "config.yml"), Config.YAML);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NEXORA_Raids 1.0.0 загружен. Используй /raid.");
    }

    @Override public void onDisable() {
        for (RaidRun run : new ArrayList<>(runs.values())) cleanup(run);
        runs.clear(); owners.clear();
        if (config != null) config.save();
    }

    private Position readPosition(String key) {
        if (!config.exists(key + ".world")) return null;
        String world = config.getString(key + ".world");
        if (!getServer().isLevelLoaded(world)) getServer().loadLevel(world);
        var level = getServer().getLevelByName(world);
        if (level == null) return null;
        return new Position(config.getDouble(key+".x"), config.getDouble(key+".y"),
                config.getDouble(key+".z"), level);
    }

    private void savePosition(String key, Player p) {
        config.set(key+".world", p.getLevel().getName());
        config.set(key+".x", p.getX()); config.set(key+".y", p.getY()); config.set(key+".z", p.getZ());
        config.save();
        p.sendMessage("§6✦ NEXORA §8» §aТочка рейда «"+key+"» сохранена.");
    }

    private void put(cn.nukkit.level.Level level, int x, int y, int z, int id, int meta) {
        level.setBlock(new Vector3(x,y,z), Block.get(id,meta), false, false);
    }

    private void buildRaid(Player p) {
        var level=p.getLevel(); int cx=p.getFloorX(), cy=p.getFloorY(), cz=p.getFloorZ();
        Random r=new Random(44221L);
        p.sendMessage("§6✦ NEXORA §8» §eСоздаю Зал древнего стража...");
        // Main circular hall with a dark stone floor and a high, broken perimeter.
        for(int x=-18;x<=18;x++) for(int z=-18;z<=18;z++) {
            double d=Math.sqrt(x*x+z*z); if(d>18) continue;
            put(level,cx+x,cy-1,cz+z,(r.nextInt(5)==0?4:1),0);
            for(int h=0;h<8;h++) put(level,cx+x,cy+h,cz+z,0,0);
            if(d>15.5) for(int h=0;h<7+(r.nextInt(3));h++)
                put(level,cx+x,cy+h,cz+z,(r.nextInt(4)==0?48:1),0);
            if(d>15.5 && r.nextInt(100)<28) put(level,cx+x,cy+7,cz+z,4,0);
        }
        // Four ruined pillars, ribs and a raised boss dais.
        for(int[] q:new int[][]{{-11,-11},{-11,11},{11,-11},{11,11}}) {
            for(int h=0;h<7;h++) { put(level,cx+q[0],cy+h,cz+q[1],4,0); put(level,cx+q[0]+1,cy+h,cz+q[1],4,0); }
            for(int k=-2;k<=2;k++) put(level,cx+q[0]+k,cy+7,cz+q[1],17,0);
            put(level,cx+q[0],cy+8,cz+q[1],89,0);
        }
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) {
            if(Math.abs(x)==4||Math.abs(z)==4) put(level,cx+x,cy,cz+z,98,0);
            else if(x*x+z*z<=9) put(level,cx+x,cy,cz+z,89,0);
        }
        // Moss, roots, cracked stones, cobwebs and atmospheric lights.
        for(int i=0;i<100;i++) {
            int x=r.nextInt(31)-15,z=r.nextInt(31)-15;
            if(x*x+z*z>225) continue;
            if(r.nextBoolean()) put(level,cx+x,cy-1,cz+z,48,0);
            else put(level,cx+x,cy+7,cz+z,18,r.nextInt(4));
        }
        for(int i=0;i<18;i++) {
            int x=r.nextInt(29)-14,z=r.nextInt(29)-14;
            put(level,cx+x,cy+1,cz+z,30,0);
        }
        for(int a=0;a<8;a++) {
            double angle=Math.PI*2*a/8.0;
            int x=(int)Math.round(Math.cos(angle)*13), z=(int)Math.round(Math.sin(angle)*13);
            put(level,cx+x,cy+3,cz+z,89,0);
            put(level,cx+x,cy+2,cz+z,50,5);
        }
        // Entrance gate.
        for(int y=0;y<5;y++) { put(level,cx-3,cy+y,cz-17,17,0); put(level,cx+3,cy+y,cz-17,17,0); }
        for(int x=-3;x<=3;x++) put(level,cx+x,cy+5,cz-17,17,0);
        savePosition("spawn",p);
        p.sendMessage("§aЗал рейда построен! §7Выход установи командой §e/raid setexit§7.");
    }

    private void openMenu(Player p) {
        FormWindowSimple form=new FormWindowSimple("§l§0✦ NEXORA · РЕЙДЫ",
                "§8━━━━━━━━━━━━━━━━━━\n§6⚔ ЗАЛ ДРЕВНЕГО СТРАЖА\n"
                +"§7Три этапа. Орды врагов. Два босса.\n§8Питомцы помогут в бою, если ты атакуешь цель.\n\n"
                +"§aСЮЖЕТНЫЙ §8· §fДля знакомства\n§eГЕРОИЧЕСКИЙ §8· §fСерьёзное испытание\n"
                +"§cКОШМАР §8· §fМаксимальная опасность\n§8━━━━━━━━━━━━━━━━━━\n§7Выбери сложность:");
        form.addButton(new ElementButton("§l§a✦ СЮЖЕТНЫЙ\n§8Награда: 2 алмаза"));
        form.addButton(new ElementButton("§l§e✦ ГЕРОИЧЕСКИЙ\n§8Награда: 4 алмаза"));
        form.addButton(new ElementButton("§l§c☠ КОШМАР\n§8Награда: 8 алмазов"));
        form.addButton(new ElementButton("§7Закрыть"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if(form.wasClosed()) return;
            switch(form.getResponse().getClickedButtonId()) {
                case 0 -> start(p,STORY); case 1 -> start(p,HERO); case 2 -> start(p,NIGHTMARE);
                default -> { }
            }
        }));
        p.showFormWindow(form);
    }

    private void start(Player p, Difficulty d) {
        if(runs.containsKey(p.getUniqueId())) { p.sendMessage("§cТы уже участвуешь в рейде. /raid leave"); return; }
        Position spawn=readPosition("spawn");
        if(spawn==null) { p.sendMessage("§cРейд не настроен. Администратор должен выполнить /raid build и /raid setexit."); return; }
        RaidRun run=new RaidRun(p,d,spawn); runs.put(p.getUniqueId(),run);
        p.teleport(spawn);
        p.sendMessage("§6✦ NEXORA §8» §fРейд начался: "+d.title);
        p.sendMessage("§7Этапы: зачистка → хранитель → древний страж.");
        spawnStage(run);
    }

    private void spawnMob(RaidRun run, String type, String name, int hp, double angle, double radius) {
        Position pos=new Position(run.center.x+Math.cos(angle)*radius,run.center.y,
                run.center.z+Math.sin(angle)*radius,run.center.getLevel());
        Entity mob=Entity.createEntity(type,pos);
        if(mob==null) { run.player.sendMessage("§cНе удалось создать врага: "+type); return; }
        mob.setNameTag(name); mob.setNameTagVisible(true); mob.setNameTagAlwaysVisible(true);
        if(hp>0) { mob.setMaxHealth(hp); mob.setHealth(hp); }
        mob.spawnToAll();
        run.alive.add(mob.getId()); run.mobs.put(mob.getId(),mob); owners.put(mob.getId(),run);
    }

    private void spawnStage(RaidRun run) {
        if(!run.player.isOnline()) { cleanup(run); return; }
        run.stage++;
        run.alive.clear();
        run.player.sendMessage("§8━━━━━━━━━━━━━━━━━━━━");
        if(run.stage==1) {
            run.player.sendMessage("§b⚔ ЭТАП I — ЗАЧИСТКА §7· Враги приближаются!");
            for(int i=0;i<run.difficulty.adds+2;i++)
                spawnMob(run,(i%2==0?"Zombie":"Skeleton"),"§cРазоритель зала",0,Math.PI*2*i/(run.difficulty.adds+2),7);
        } else if(run.stage==2) {
            run.player.sendMessage("§e☠ ЭТАП II — ХРАНИТЕЛЬ РУИН");
            spawnMob(run,"IronGolem","§e✦ ХРАНИТЕЛЬ РУИН",run.difficulty.hp/2,0,0);
            for(int i=0;i<run.difficulty.adds;i++)
                spawnMob(run,"Spider","§5Паук руин",0,Math.PI*2*i/run.difficulty.adds,9);
        } else {
            run.player.sendMessage("§c☠ ЭТАП III — ДРЕВНИЙ СТРАЖ");
            run.player.sendMessage("§7Победи босса, чтобы завершить рейд!");
            spawnMob(run,"IronGolem","§c☠ ДРЕВНИЙ СТРАЖ",run.difficulty.hp,0,0);
        }
        if(run.alive.isEmpty()) { run.player.sendMessage("§cНе удалось создать врагов. Рейд остановлен."); finish(run,false); }
    }

    @EventHandler public void onDeath(EntityDeathEvent event) {
        long id=event.getEntity().getId();
        RaidRun run=owners.remove(id);
        if(run==null || runs.get(run.player.getUniqueId())!=run) return;
        run.alive.remove(id); run.mobs.remove(id);
        if(run.alive.isEmpty()) {
            if(run.stage>=3) finish(run,true);
            else getServer().getScheduler().scheduleDelayedTask(this,()->{
                if(runs.get(run.player.getUniqueId())==run) spawnStage(run);
            },60);
        }
    }

    private void cleanupMobs(RaidRun run) {
        for(Map.Entry<Long,Entity> e:new HashMap<>(run.mobs).entrySet()) {
            Entity mob=e.getValue();
            if(mob!=null&&!mob.isClosed()) mob.close();
            owners.remove(e.getKey());
        }
        run.mobs.clear(); run.alive.clear();
    }
    private void cleanup(RaidRun run) { cleanupMobs(run); runs.remove(run.player.getUniqueId()); }

    private void finish(RaidRun run, boolean success) {
        cleanupMobs(run); runs.remove(run.player.getUniqueId());
        if(!run.player.isOnline()) return;
        if(success) {
            run.player.addExperience(run.difficulty.xp);
            run.player.getInventory().addItem(Item.get(264,0,run.difficulty.diamonds));
            run.player.getInventory().addItem(Item.get(266,0,run.difficulty.diamonds*3));
            run.player.sendMessage("§6✦ NEXORA §8» §aРЕЙД ПРОЙДЕН!");
            run.player.sendMessage("§7Награда: §b+"+run.difficulty.xp+" XP §8· §b"+run.difficulty.diamonds+" алм. §8· §6"+(run.difficulty.diamonds*3)+" золота");
        } else run.player.sendMessage("§cРейд завершён без награды.");
        Position exit=readPosition("exit"); if(exit!=null) run.player.teleport(exit);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        RaidRun run=runs.get(event.getPlayer().getUniqueId()); if(run!=null) cleanup(run);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player p)) { sender.sendMessage("§cКоманда доступна только игроку."); return true; }
        if(args.length>0) {
            if(!p.hasPermission("nexora.raids.admin")&&!p.isOp()) { p.sendMessage("§cНедостаточно прав."); return true; }
            switch(args[0].toLowerCase(Locale.ROOT)) {
                case "build" -> { buildRaid(p); return true; }
                case "setspawn" -> { savePosition("spawn",p); return true; }
                case "setexit" -> { savePosition("exit",p); return true; }
                case "leave" -> {
                    RaidRun run=runs.get(p.getUniqueId());
                    if(run==null) { p.sendMessage("§7Ты не участвуешь в рейде."); return true; }
                    cleanup(run); Position exit=readPosition("exit"); if(exit!=null) p.teleport(exit);
                    p.sendMessage("§eТы покинул рейд без награды."); return true;
                }
                default -> { p.sendMessage("§e/raid §7| §e/raid build §7| §e/raid setspawn §7| §e/raid setexit §7| §e/raid leave"); return true; }
            }
        }
        if(!p.hasPermission("nexora.raids.use")) { p.sendMessage("§cНет прав."); return true; }
        openMenu(p); return true;
    }
}
