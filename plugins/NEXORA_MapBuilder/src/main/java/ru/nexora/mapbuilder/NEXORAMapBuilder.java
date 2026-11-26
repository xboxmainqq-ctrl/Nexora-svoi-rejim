package ru.nexora.mapbuilder;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * NEXORA MapBuilder — persistent locations and an in-world procedural fantasy capital.
 * Blocks are placed directly into the Nukkit level; no schematic importer is required.
 */
public final class NEXORAMapBuilder extends PluginBase {
    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private static final int AIR=0, STONE=1, GRASS=2, DIRT=3, COBBLE=4, PLANKS=5,
            BEDROCK=7, WATER=9, SAND=12, LOG=17, LEAVES=18, GLASS=20,
            SANDSTONE=24, WOOL=35, BRICK=45, BOOKSHELF=47, MOSSY=48,
            OBSIDIAN=49, TORCH=50, STAIRS=53, FENCE=85, NETHERRACK=87,
            GLOWSTONE=89, STONE_BRICK=98, IRON_BARS=101, GLASS_PANE=102,
            WOOD_SLAB=126, QUARTZ=155, SEA_LANTERN=169, PRISMARINE=168;
    private Config locations;
    private final java.util.Queue<BlockChange> queue = new java.util.ArrayDeque<>();
    private int taskId = -1, ox, oy, oz, cx, cz, cy, placed, removed;
    private Level activeLevel;
    private Player activePlayer;
    private boolean clearing;
    private static final int CLEAR_RADIUS = 128, CLEAR_TOP = 255, CLEAR_BUDGET = 5000, PLACE_BUDGET = 1200;
    private int buildStage = 1;
    private static final class BlockChange {
        final int x, y, z, id;
        BlockChange(int x, int y, int z, int id) { this.x=x; this.y=y; this.z=z; this.id=id; }
    }

    @Override public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку данных MapBuilder.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        locations = new Config(new File(getDataFolder(), "locations.yml"), Config.YAML);
        getLogger().info("NEXORA MapBuilder загружен. Точек: " + locations.getSection("locations").size());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexora.admin") && !sender.isOp()) {
            sender.sendMessage("§cНедостаточно прав."); return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> saveLocation(sender, "spawn");
            case "setloc" -> { if (args.length < 2) sender.sendMessage("§c/nexora setloc <имя>"); else saveLocation(sender,args[1]); }
            case "locations" -> listLocations(sender);
            case "tp" -> { if (args.length < 2) sender.sendMessage("§c/nexora tp <имя>"); else teleportTo(sender,args[1]); }
            case "build" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cСтроительство запускается из игры."); break; }
                String district = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "arden";
                if (!district.equals("arden")) { sender.sendMessage("§cДоступно: /nexora build arden"); break; }
                buildArden(p);
            }
            default -> help(sender);
        }
        return true;
    }

    private void saveLocation(CommandSender sender, String name) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cТолько в игре."); return; }
        if (!NAME.matcher(name).matches()) { sender.sendMessage("§cИмя: латиница, цифры, _ или -, 1–32 символа."); return; }
        String path="locations."+name.toLowerCase(Locale.ROOT);
        Location l=p.getLocation();
        locations.set(path+".world",p.getLevel().getFolderName());
        locations.set(path+".x",l.getX()); locations.set(path+".y",l.getY()); locations.set(path+".z",l.getZ());
        locations.set(path+".yaw",l.getYaw()); locations.set(path+".pitch",l.getPitch());
        locations.save(); sender.sendMessage("§aТочка §f"+name+" §aсохранена.");
    }

    private void listLocations(CommandSender sender) {
        Map<String,Object> saved=locations.getSection("locations");
        if(saved.isEmpty()){sender.sendMessage("§eТочек пока нет.");return;}
        sender.sendMessage("§6Сохранённые точки NEXORA:");
        for(String key:saved.keySet()) sender.sendMessage("§e- §f"+key);
    }

    private void teleportTo(CommandSender sender,String name) {
        if(!(sender instanceof Player p)){sender.sendMessage("§cТолько в игре.");return;}
        if(!NAME.matcher(name).matches()){sender.sendMessage("§cНекорректное имя.");return;}
        String path="locations."+name.toLowerCase(Locale.ROOT);
        if(!locations.exists(path)){sender.sendMessage("§cТочка не найдена.");return;}
        Level level=Server.getInstance().getLevelByName(locations.getString(path+".world",""));
        if(level==null){sender.sendMessage("§cМир точки не загружен.");return;}
        Location dest=new Location(locations.getDouble(path+".x"),locations.getDouble(path+".y"),
                locations.getDouble(path+".z"),(float)locations.getDouble(path+".yaw"),
                (float)locations.getDouble(path+".pitch"),level);
        p.teleport(dest); sender.sendMessage("§aТелепортация к §f"+name+"§a.");
    }

    private void buildArden(Player p) {
        if (taskId != -1) { p.sendMessage("§cГенератор уже работает."); return; }
        activeLevel = p.getLevel(); activePlayer = p;
        ox = p.getFloorX(); oy = 64; oz = p.getFloorZ();
        String marker = "builds.arden." + activeLevel.getFolderName();
        if (locations.getBoolean(marker + ".complete", false)) {
            p.sendMessage("§eАрден уже отмечен как построенный. Повторный запуск отключён, чтобы не затереть город.");
            activeLevel = null; activePlayer = null; return;
        }
        queue.clear(); placed = 0; removed = 0; clearing = true; buildStage = 1;
        cx = -CLEAR_RADIUS; cz = -CLEAR_RADIUS; cy = oy;
        locations.set(marker + ".active", true); locations.set(marker + ".originX", ox);
        locations.set(marker + ".originY", oy); locations.set(marker + ".originZ", oz); locations.save();
        p.sendMessage("§6[Арден] §eЭтап 1/8: очищаю круглую территорию диаметром 256 блоков.");
        p.sendMessage("§cВНИМАНИЕ: будут удалены блоки внутри круга радиусом 128 блоков от Y=64 до Y=255, включая постройки!");
        taskId = getServer().getScheduler().scheduleRepeatingTask(this, () -> generationTick(marker), 1).getTaskId();
    }

    private void generationTick(String marker) {
        if (activeLevel == null) { stopGeneration(); return; }
        if (clearing) {
            int budget = CLEAR_BUDGET;
            while (budget-- > 0) {
                int dx = cx, dz = cz;
                if (dx * dx + dz * dz <= CLEAR_RADIUS * CLEAR_RADIUS) {
                    int wx = ox + dx, wz = oz + dz;
                    if (cy <= CLEAR_TOP) {
                        if (activeLevel.getBlockIdAt(wx, cy, wz) != AIR) { activeLevel.setBlockAt(wx, cy, wz, AIR); removed++; }
                        cy++; continue;
                    }

                }
                cy = oy; cz++;
                if (cz > CLEAR_RADIUS) { cz = -CLEAR_RADIUS; cx++; }
                if (cx > CLEAR_RADIUS) {
                    clearing = false; buildStage = 2; locations.set(marker + ".stage", buildStage); locations.save();
                    if (activePlayer != null) activePlayer.sendMessage("§a[Арден] Этап 1/8 завершён. Начинаю выравнивание (2/8).");
                    queueArden(marker);
                    break;
                }
            }
            if (activePlayer != null && removed > 0 && removed % 50000 < CLEAR_BUDGET) activePlayer.sendMessage("§e[Арден] Удалено блоков: §f" + removed);
            return;
        }
        int budget = PLACE_BUDGET;
        while (budget-- > 0 && !queue.isEmpty()) {
            BlockChange c = queue.poll(); activeLevel.setBlockAt(c.x, c.y, c.z, c.id); placed++;
        }
        if (queue.isEmpty()) {
            if (buildStage < 8) {
                buildStage++; locations.set(marker + ".stage", buildStage); locations.save();
                if (activePlayer != null) activePlayer.sendMessage("§6[Арден] Этап " + buildStage + "/8: " + stageName(buildStage));
                queueArden(marker); return;
            }
            putPoint("spawn", activeLevel, ox, oy + 2, oz, 0, 0);
            putPoint("castle", activeLevel, ox, oy + 2, oz - 78, 0, 0);
            putPoint("market", activeLevel, ox + 65, oy + 2, oz - 10, 0, 0);
            putPoint("arena", activeLevel, ox, oy + 2, oz + 75, 0, 0);
            putPoint("sanctuary", activeLevel, ox - 65, oy + 2, oz + 10, 0, 0);
            locations.set(marker + ".stage", 8); locations.set(marker + ".complete", true); locations.set(marker + ".active", false);
            locations.save();
            if (activePlayer != null) activePlayer.sendMessage("§a[Арден] Этап 8/8 завершён! Арден готов. Блоков: §f" + placed);
            stopGeneration();
        } else if (activePlayer != null && placed > 0 && placed % 50000 < PLACE_BUDGET) {
            activePlayer.sendMessage("§6[Арден] Этап " + buildStage + "/8 | блоков: §f" + placed + " §7| очередь: §f" + queue.size());
        }
    }

    private String stageName(int n) {
        return switch(n) {
            case 2 -> "выравнивание круглой площадки";
            case 3 -> "центральная площадь и кольцевые дороги";
            case 4 -> "круговая стена, башни и ворота";
            case 5 -> "главные здания столицы";
            case 6 -> "жилые кварталы";
            case 7 -> "сады, фонари и декор";
            case 8 -> "интерьеры, мостовые, балконы и финальные детали";
            default -> "финальная отделка";
        };
    }

    private void queueArden(String marker) {
        Level w=activeLevel;
        if(buildStage==2) {
            for(int x=-CLEAR_RADIUS;x<=CLEAR_RADIUS;x++) for(int z=-CLEAR_RADIUS;z<=CLEAR_RADIUS;z++)
                if(x*x+z*z<=CLEAR_RADIUS*CLEAR_RADIUS) b(w,ox+x,oy-1,oz+z,STONE);
        } else if(buildStage==3) {
            for(int r=24;r<=116;r+=24) for(int a=0;a<360;a+=2) {
                double t=Math.toRadians(a); int x=ox+(int)Math.round(Math.cos(t)*r), z=oz+(int)Math.round(Math.sin(t)*r);
                fill(w,x-2,oy,z-2,x+2,oy,z+2,STONE_BRICK);
            }
            for(int a=0;a<360;a+=45) { double t=Math.toRadians(a); for(int r=10;r<=124;r++) {
                int x=ox+(int)Math.round(Math.cos(t)*r), z=oz+(int)Math.round(Math.sin(t)*r);
                fill(w,x-2,oy,z-2,x+2,oy,z+2,COBBLE);
            }}
            plaza(w,ox,oy,oz);
        } else if(buildStage==4) {
            for(int a=0;a<360;a++) { double t=Math.toRadians(a); int x=ox+(int)Math.round(Math.cos(t)*124), z=oz+(int)Math.round(Math.sin(t)*124);
                int nx=(int)Math.round(-Math.sin(t)), nz=(int)Math.round(Math.cos(t));
                for(int thick=-1;thick<=1;thick++) fill(w,x+nx*thick,oy+1,z+nz*thick,x+nx*thick,oy+8,z+nz*thick,STONE_BRICK);
                if(a%8==0) { fill(w,x-2,oy+9,z-2,x+2,oy+10,z+2,QUARTZ); b(w,x,oy+11,z,GLOWSTONE); }
                if(a%45==0) {
                    fill(w,x-4,oy+1,z-4,x+4,oy+17,z+4,STONE_BRICK);
                    fill(w,x-3,oy+3,z-3,x+3,oy+14,z+3,AIR);
                    fill(w,x-5,oy+18,z-5,x+5,oy+19,z+5,QUARTZ);
                    for(int h=5;h<=13;h+=4) { b(w,x-4,oy+h,z,GLASS); b(w,x+4,oy+h,z,GLASS); }
                    b(w,x,oy+20,z,GLOWSTONE);
                }
            }
            for(int a=0;a<360;a+=90) { double t=Math.toRadians(a); int x=ox+(int)Math.round(Math.cos(t)*124),z=oz+(int)Math.round(Math.sin(t)*124);
                for(int side=-1;side<=1;side+=2) { int tx=x+(int)Math.round(-Math.sin(t)*side*8),tz=z+(int)Math.round(Math.cos(t)*side*8);
                    fill(w,tx-3,oy+1,tz-3,tx+3,oy+18,tz+3,STONE_BRICK); fill(w,tx-4,oy+19,tz-4,tx+4,oy+19,tz+4,QUARTZ); b(w,tx,oy+20,tz,GLOWSTONE);
                }
            }
        } else if(buildStage==5) {
            castle(w,ox-18,oy,oz-104); marketHouse(w,ox+54,oy,oz-28,19,17); marketHouse(w,ox+75,oy,oz-8,17,19);
            marketHouse(w,ox+54,oy,oz+14,19,17); sanctuary(w,ox-94,oy,oz+20); guild(w,ox+72,oy,oz+55); arena(w,ox,oy,oz+82);
            shell(w,ox-92,oy,oz-60,23,21,STONE_BRICK,QUARTZ);
            shell(w,ox+38,oy,oz+82,23,21,BRICK,WOOL);
            shell(w,ox-38,oy,oz+84,23,21,STONE_BRICK,PRISMARINE);
        } else if(buildStage==6) {
            int n=0;
            for(int x=-108;x<=108;x+=15) for(int z=-108;z<=108;z+=15) {
                int d=x*x+z*z; if(d<43*43||d>116*116||(Math.abs(x)<24&&Math.abs(z)<24)) continue;
                if((Math.abs(x)<30&&Math.abs(z-82)<28)||(Math.abs(x+94)<18&&Math.abs(z-20)<20)) continue;
                int sx=9+(n%3)*2,sz=9+((n/3)%3)*2,bx=ox+x-sx/2,bz=oz+z-sz/2;
                residentialHouse(w,bx,oy,bz,sx,sz,n); n++;
            }
        } else if(buildStage==7) {
            for(int r=42;r<=108;r+=22) for(int a=0;a<360;a+=12) { double t=Math.toRadians(a); int x=ox+(int)Math.round(Math.cos(t)*r),z=oz+(int)Math.round(Math.sin(t)*r);
                lamp(w,x,oy,z); if(a%24==0) { fill(w,x-2,oy,z-2,x+2,oy,z+2,GRASS); fill(w,x,oy+1,z,x,oy+4,z,LOG); fill(w,x-2,oy+4,z-2,x+2,oy+6,z+2,LEAVES); }
            }
            avenueTrees(w,ox,oy,oz);
            for(int a=0;a<4;a++) {
                double t=Math.toRadians(a*90+45); int gx=ox+(int)Math.round(Math.cos(t)*53),gz=oz+(int)Math.round(Math.sin(t)*53);
                fill(w,gx-10,oy,gz-10,gx+10,oy,gz+10,GRASS);
                fill(w,gx-5,oy+1,gz-5,gx+5,oy+1,gz+5,PRISMARINE);
                fill(w,gx-3,oy+2,gz-3,gx+3,oy+2,gz+3,WATER);
                for(int k=0;k<4;k++){int tx=gx+(k<2?-7:7),tz=gz+(k%2==0?-7:7); fill(w,tx,oy+1,tz,tx,oy+4,tz,LOG); fill(w,tx-2,oy+4,tz-2,tx+2,oy+6,tz+2,LEAVES);}
                for(int k=-8;k<=8;k+=4){lamp(w,gx+k,oy,gz-9);lamp(w,gx+k,oy,gz+9);}
            }
        } else if(buildStage==8) {
            for(int x=-30;x<=30;x+=10){bench(w,ox+x,oy,oz-22);bench(w,ox+x,oy,oz+22);}
            for(int z=-30;z<=30;z+=10){bench(w,ox-22,oy,oz+z);bench(w,ox+22,oy,oz+z);}
            for(int a=0;a<360;a+=90){
                double t=Math.toRadians(a); int gx=ox+(int)Math.round(Math.cos(t)*110),gz=oz+(int)Math.round(Math.sin(t)*110);
                for(int side=-1;side<=1;side+=2){int px=gx+(int)Math.round(-Math.sin(t)*side*4),pz=gz+(int)Math.round(Math.cos(t)*side*4);fill(w,px,oy+1,pz,px,oy+8,pz,QUARTZ);b(w,px,oy+9,pz,GLOWSTONE);}
            }
        }
        locations.set(marker+".stage",buildStage); locations.set(marker+".queuedBlocks",queue.size()); locations.save();
    }

    private void stopGeneration() {
        if (taskId != -1) getServer().getScheduler().cancelTask(taskId);
        taskId = -1; activeLevel = null; activePlayer = null; clearing = false; queue.clear();
    }
    private void putPoint(String name,Level w,double x,double y,double z,float yaw,float pitch){
        String q="locations."+name; locations.set(q+".world",w.getFolderName());
        locations.set(q+".x",x);locations.set(q+".y",y);locations.set(q+".z",z);
        locations.set(q+".yaw",yaw);locations.set(q+".pitch",pitch);
    }
    private void b(Level w,int x,int y,int z,int id){queue.add(new BlockChange(x,y,z,id));}
    private void fill(Level w,int x1,int y1,int z1,int x2,int y2,int z2,int id){
        for(int x=x1;x<=x2;x++)for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)b(w,x,y,z,id);
    }
    private void ring(Level w,int cx,int y,int cz,int r,int id){
        for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)if(Math.abs(x)==r||Math.abs(z)==r)b(w,cx+x,y,cz+z,id);
    }
    private void lamp(Level w,int x,int y,int z){
        fill(w,x,y,z,x,y+3,z,STONE_BRICK);b(w,x,y+4,z,GLOWSTONE);b(w,x,y+5,z,TORCH);
    }
    private void plaza(Level w,int x,int y,int z){
        fill(w,x-13,y,z-13,x+13,y,z+13,STONE_BRICK);
        ring(w,x,y+1,z,12,QUARTZ);ring(w,x,y+1,z,9,PRISMARINE);ring(w,x,y+1,z,6,QUARTZ);
        fill(w,x-4,y+1,z-4,x+4,y+1,z+4,SEA_LANTERN);
        fill(w,x-3,y+2,z-3,x+3,y+2,z+3,WATER);
        fill(w,x-1,y+3,z-1,x+1,y+7,z+1,QUARTZ);
        fill(w,x-2,y+7,z-2,x+2,y+9,z+2,PRISMARINE);
        b(w,x,y+10,z,GLOWSTONE);
        for(int[] d:new int[][]{{-10,-10},{10,-10},{-10,10},{10,10}})lamp(w,x+d[0],y+1,z+d[1]);
        for(int a=-2;a<=2;a++){b(w,x+a,y+2,z-3,GLASS);b(w,x+a,y+2,z+3,GLASS);}
    }
    private void shell(Level w,int x,int y,int z,int sx,int sz,int wall,int roof){
        fill(w,x,y,z,x+sx-1,y+5,z+sz-1,wall);
        fill(w,x+1,y+1,z+1,x+sx-2,y+4,z+sz-2,AIR);
        fill(w,x+1,y,z+1,x+sx-2,y,z+sz-2,PLANKS);
        for(int xx=x+2;xx<x+sx-2;xx+=4){
            b(w,xx,y+2,z,GLASS);b(w,xx,y+3,z,GLASS);b(w,xx,y+2,z+sz-1,GLASS);b(w,xx,y+3,z+sz-1,GLASS);
        }
        for(int zz=z+2;zz<z+sz-2;zz+=4){
            b(w,x,y+2,zz,GLASS);b(w,x,y+3,zz,GLASS);b(w,x+sx-1,y+2,zz,GLASS);b(w,x+sx-1,y+3,zz,GLASS);
        }
        // Stone corner pilasters and decorative lintels make facades less box-like.
        for(int[] c:new int[][]{{0,0},{sx-1,0},{0,sz-1},{sx-1,sz-1}}) {
            fill(w,x+c[0],y+1,z+c[1],x+c[0],y+5,z+c[1],QUARTZ);
        }
        fill(w,x+1,y+5,z+1,x+sx-2,y+5,z+1,QUARTZ);
        fill(w,x+1,y+5,z+sz-2,x+sx-2,y+5,z+sz-2,QUARTZ);
        // Layered, stepped roof gives each building a visible silhouette.
        for(int layer=0;layer<3;layer++){
            int inset=layer;
            fill(w,x+inset,y+6+layer,z+inset,x+sx-1-inset,y+6+layer,z+sz-1-inset,roof);
        }
        fill(w,x+sx/2-1,y+9,z+sz/2-1,x+sx/2+1,y+11,z+sz/2+1,wall);
        b(w,x+sx/2,y+12,z+sz/2,GLOWSTONE);
        // Entrance porch and doorway.
        fill(w,x+sx/2-1,y+1,z-2,x+sx/2+1,y+3,z-1,STONE_BRICK);
        fill(w,x+sx/2,y+1,z,x+sx/2,y+2,z,AIR);
        for(int xx=x+2;xx<x+sx-2;xx+=5){lamp(w,xx,y,z-2);}
    }
    private void residentialHouse(Level w,int x,int y,int z,int sx,int sz,int variant) {
        int wall=variant%4==0?STONE_BRICK:(variant%4==1?BRICK:(variant%4==2?SANDSTONE:PLANKS));
        int roof=variant%3==0?QUARTZ:(variant%3==1?WOOL:BRICK);
        fill(w,x,y,z,x+sx-1,y+6,z+sz-1,wall);
        fill(w,x+1,y+1,z+1,x+sx-2,y+5,z+sz-2,AIR);
        fill(w,x+1,y,z+1,x+sx-2,y,z+sz-2,PLANKS);
        for(int xx=x+1;xx<x+sx-1;xx++){b(w,xx,y+6,z,PLANKS);b(w,xx,y+6,z+sz-1,PLANKS);}
        for(int zz=z+1;zz<z+sz-1;zz++){b(w,x,y+6,zz,PLANKS);b(w,x+sx-1,y+6,zz,PLANKS);}
        for(int xx=x+2;xx<x+sx-2;xx+=4){
            fill(w,xx,y+2,z,xx+1,y+3,z,GLASS);b(w,xx-1,y+2,z,WOOL);b(w,xx+2,y+2,z,WOOL);
            fill(w,xx,y+8,z,xx+1,y+9,z,GLASS);
        }
        int door=x+sx/2;fill(w,door,y+1,z,door,y+3,z,AIR);
        b(w,door-1,y+1,z,QUARTZ);b(w,door+1,y+1,z,QUARTZ);
        fill(w,x+sx/2,y+1,z+1,x+sx/2,y+5,z+sz-2,BRICK);
        fill(w,x+2,y+1,z+2,x+3,y+1,z+3,WOOL);
        fill(w,x+sx-4,y+1,z+2,x+sx-3,y+1,z+3,BOOKSHELF);
        fill(w,x+2,y+1,z+sz-4,x+4,y+1,z+sz-3,PLANKS);
        fill(w,x+sx-4,y+1,z+sz-4,x+sx-3,y+1,z+sz-3,QUARTZ);
        for(int layer=0;layer<4;layer++)fill(w,x-1+layer,y+7+layer,z-1+layer,x+sx-layer,y+7+layer,z+sz-layer,roof);
        fill(w,x+sx/2-1,y+11,z+sz/2-1,x+sx/2+1,y+12,z+sz/2+1,wall);
        fill(w,x+sx-3,y+7,z+2,x+sx-2,y+12,z+3,STONE_BRICK);b(w,x+sx-3,y+13,z+2,GLOWSTONE);
        fill(w,door-2,y+6,z-2,door+2,y+6,z-1,PLANKS);
        for(int xx=door-2;xx<=door+2;xx+=2)b(w,xx,y+7,z-1,FENCE);
        for(int xx=x+2;xx<x+sx-2;xx+=4)lamp(w,xx,y,z-2);
    }
    private void bench(Level w,int x,int y,int z) {
        b(w,x,y+1,z,PLANKS);b(w,x+1,y+1,z,PLANKS);b(w,x,y+2,z-1,FENCE);b(w,x+1,y+2,z-1,FENCE);
    }

    private void castle(Level w,int x,int y,int z){
        // Grand keep: broad two-wing footprint, open central hall and layered roof.
        shell(w,x,y,z,39,33,STONE_BRICK,STONE_BRICK);
        fill(w,x+5,y+1,z+5,x+33,y+1,z+27,QUARTZ);
        fill(w,x+8,y+2,z+8,x+30,y+5,z+24,AIR);
        // Four massive corner towers, hollow inside, with stepped crowns.
        for(int[] c:new int[][]{{0,0},{33,0},{0,27},{33,27}}){
            fill(w,x+c[0],y+1,z+c[1],x+c[0]+5,y+24,z+c[1]+5,STONE_BRICK);
            fill(w,x+c[0]+1,y+3,z+c[1]+1,x+c[0]+4,y+21,z+c[1]+4,AIR);
            fill(w,x+c[0]-1,y+25,z+c[1]-1,x+c[0]+6,y+26,z+c[1]+6,QUARTZ);
            fill(w,x+c[0],y+27,z+c[1],x+c[0]+5,y+29,z+c[1]+5,STONE_BRICK);
            b(w,x+c[0]+2,y+30,z+c[1]+2,GLOWSTONE);
            for(int h=6;h<=18;h+=4) { b(w,x+c[0],y+h,z+c[1]+2,GLASS); b(w,x+c[0]+5,y+h,z+c[1]+2,GLASS); }
        }
        // Main audience hall and high central roof ridge.
        fill(w,x+12,y+2,z+10,x+26,y+11,z+22,QUARTZ);
        fill(w,x+14,y+3,z+11,x+24,y+10,z+21,AIR);
        for(int layer=0;layer<5;layer++)
            fill(w,x+10+layer,y+12+layer,z+8+layer,x+28-layer,y+12+layer,z+24-layer,STONE_BRICK);
        fill(w,x+17,y+17,z+14,x+21,y+21,z+18,PRISMARINE);
        b(w,x+19,y+22,z+16,GLOWSTONE);
        // Tall arched entrance, columns and royal stairs.
        fill(w,x+17,y+1,z-1,x+21,y+7,z+1,QUARTZ);
        fill(w,x+18,y+1,z,x+20,y+6,z+1,AIR);
        for(int i=1;i<=12;i++) fill(w,x+15-i,y+i,z-2-i,x+23+i,y+i,z-1-i,STONE_BRICK);
        for(int xx=x+7;xx<=x+31;xx+=4) {
            fill(w,xx,y+2,z+1,xx+1,y+9,z+1,QUARTZ);
            b(w,xx,y+10,z+1,GLOWSTONE);
        }
        // Courtyard gardens, pools and connecting covered galleries.
        for(int[] d:new int[][]{{8,8},{27,8},{8,22},{27,22}}) {
            fill(w,x+d[0],y+1,z+d[1],x+d[0]+3,y+1,z+d[1]+3,GRASS);
            b(w,x+d[0]+1,y+2,z+d[1]+1,LEAVES);
        }
        fill(w,x+17,y+1,z+25,x+21,y+1,z+29,PRISMARINE);
        fill(w,x+18,y+2,z+26,x+20,y+2,z+28,WATER);
    }
    private void marketHouse(Level w,int x,int y,int z,int sx,int sz){
        shell(w,x,y,z,sx,sz,BRICK,WOOL);
        // Shop counters, shelves, colored awnings and signboards.
        fill(w,x+2,y+1,z+1,x+sx-3,y+1,z+2,PLANKS);
        for(int xx=x+2;xx<x+sx-2;xx+=3){
            b(w,xx,y+3,z-1,WOOL);b(w,xx+1,y+3,z-1,WOOL);
            b(w,xx,y+4,z-1,PLANKS);
        }
        fill(w,x+3,y+2,z+sz-3,x+sx-4,y+3,z+sz-3,BOOKSHELF);
    }
    private void sanctuary(Level w,int x,int y,int z){
        shell(w,x,y,z,25,19,LOG,LEAVES);
        // Open animal habitat pens with fences, grass patches and water troughs.
        for(int n=0;n<2;n++){
            int px=x+3+n*11;
            fill(w,px,y+1,z+3,px+8,y+1,z+14,GRASS);
            fill(w,px,y+2,z+3,px+8,y+3,z+3,FENCE);
            fill(w,px,y+2,z+14,px+8,y+3,z+14,FENCE);
            fill(w,px,y+2,z+3,px,y+3,z+14,FENCE);
            fill(w,px+8,y+2,z+3,px+8,y+3,z+14,FENCE);
            fill(w,px+3,y+2,z+8,px+5,y+2,z+10,WATER);
            for(int i=0;i<3;i++)b(w,px+2+i*2,y+2,z+5,LEAVES);
        }
    }
    private void guild(Level w,int x,int y,int z){
        shell(w,x,y,z,19,19,STONE_BRICK,QUARTZ);
        // Tall knowledge/guild tower attached to the hall.
        fill(w,x+6,y+1,z+6,x+12,y+20,z+12,STONE_BRICK);
        fill(w,x+7,y+2,z+7,x+11,y+18,z+11,AIR);
        for(int h=4;h<18;h+=4){
            fill(w,x+7,y+h,z+7,x+11,y+h,z+11,PLANKS);
            b(w,x+9,y+h+1,z+9,GLOWSTONE);
        }
        for(int h=3;h<18;h+=3){b(w,x+6,y+h,z+9,GLASS);b(w,x+12,y+h,z+9,GLASS);}
        fill(w,x+5,y+21,z+5,x+13,y+21,z+13,QUARTZ);
        b(w,x+9,y+22,z+9,GLOWSTONE);
    }
    private void arena(Level w,int x,int y,int z){
        int cx=x,cz=z;
        for(int r=17;r>=12;r--)ring(w,cx,y+1,cz,r,STONE_BRICK);
        fill(w,cx-11,y,cz-11,cx+11,y,cz+11,SAND);
        for(int r=12;r<=16;r++){ring(w,cx,y+2,cz,r,STONE_BRICK);ring(w,cx,y+3,cz,r,QUARTZ);}
        for(int[] d:new int[][]{{-16,-16},{16,-16},{-16,16},{16,16}}){
            fill(w,cx+d[0]-1,y+1,cz+d[1]-1,cx+d[0]+1,y+9,cz+d[1]+1,STONE_BRICK);
            fill(w,cx+d[0]-2,y+10,cz+d[1]-2,cx+d[0]+2,y+10,cz+d[1]+2,QUARTZ);
            b(w,cx+d[0],y+11,cz+d[1],GLOWSTONE);
        }
        for(int i=-8;i<=8;i+=4){lamp(w,cx+i,y+1,cz-14);lamp(w,cx+i,y+1,cz+14);}
        fill(w,cx-3,y+1,cz-3,cx+3,y+1,cz+3,COBBLE);
        b(w,cx,y+2,cz,SEA_LANTERN);
    }
    private void perimeter(Level w,int x,int y,int z){
        for(int i=-54;i<=54;i++){
            for(int h=1;h<=5;h++){
                b(w,x+i,y+h,z-54,STONE_BRICK);b(w,x+i,y+h,z+54,STONE_BRICK);
                b(w,x-54,y+h,z+i,STONE_BRICK);b(w,x+54,y+h,z+i,STONE_BRICK);
            }
            if(i%4==0){
                b(w,x+i,y+6,z-54,QUARTZ);b(w,x+i,y+6,z+54,QUARTZ);
                b(w,x-54,y+6,z+i,QUARTZ);b(w,x+54,y+6,z+i,QUARTZ);
            }
        }
        for(int[] g:new int[][]{{0,-54},{0,54},{-54,0},{54,0}}){
            int gx=x+g[0],gz=z+g[1];
            fill(w,gx-4,y+1,gz-1,gx+4,y+5,gz+1,AIR);
            for(int side:new int[]{-6,6}){
                fill(w,gx+side-1,y+1,gz-2,gx+side+1,y+12,gz+2,STONE_BRICK);
                fill(w,gx+side-2,y+13,gz-3,gx+side+2,y+13,gz+3,QUARTZ);
                b(w,gx+side,y+14,gz,GLOWSTONE);
            }
        }
    }
    private void avenueTrees(Level w,int x,int y,int z){
        for(int t=-44;t<=44;t+=11){
            for(int[] side:new int[][]{{-7,0},{7,0},{0,-7},{0,7}}){
                int tx=x+side[0]+(side[1]==0?t:0),tz=z+side[1]+(side[0]==0?t:0);
                fill(w,tx,y+1,tz,tx,y+4,tz,LOG);
                fill(w,tx-2,y+4,tz-2,tx+2,y+6,tz+2,LEAVES);
                fill(w,tx-1,y+7,tz-1,tx+1,y+7,tz+1,LEAVES);
            }
        }
    }
    private void help(CommandSender s){
        s.sendMessage("§6NEXORA MapBuilder");
        s.sendMessage("§e/nexora setspawn §7— сохранить спавн");
        s.sendMessage("§e/nexora setloc <имя> §7— сохранить точку");
        s.sendMessage("§e/nexora locations §7— список точек");
        s.sendMessage("§e/nexora tp <имя> §7— телепорт");
        s.sendMessage("§e/nexora build arden §7— круглая столица Арден, 8 этапов");
    }
}
