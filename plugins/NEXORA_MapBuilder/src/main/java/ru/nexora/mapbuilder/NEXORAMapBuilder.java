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
    private static final int CLEAR_RADIUS = 60, CLEAR_TOP = 127, CLEAR_BUDGET = 1200, PLACE_BUDGET = 400;
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
        ox = p.getFloorX(); oy = Math.max(5, p.getFloorY()); oz = p.getFloorZ();
        String marker = "builds.arden." + activeLevel.getFolderName();
        if (locations.getBoolean(marker + ".complete", false)) {
            p.sendMessage("§eАрден уже отмечен как построенный. Повторный запуск отключён, чтобы не затереть город.");
            activeLevel = null; activePlayer = null; return;
        }
        queue.clear(); placed = 0; removed = 0; clearing = true;
        cx = -CLEAR_RADIUS; cz = -CLEAR_RADIUS; cy = oy;
        locations.set(marker + ".active", true); locations.set(marker + ".originX", ox);
        locations.set(marker + ".originY", oy); locations.set(marker + ".originZ", oz); locations.save();
        p.sendMessage("§6[Арден] §eЭтап 1/2: постепенно очищаю круглую площадку радиусом 60 блоков.");
        p.sendMessage("§cВнимание: территория внутри круга будет очищена от деревьев, гор, воды и построек!");
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
                    queue.add(new BlockChange(wx, oy - 1, wz, STONE));
                }
                cy = oy; cz++;
                if (cz > CLEAR_RADIUS) { cz = -CLEAR_RADIUS; cx++; }
                if (cx > CLEAR_RADIUS) {
                    clearing = false; queueArden(marker);
                    if (activePlayer != null) activePlayer.sendMessage("§a[Арден] Площадка очищена. §eНачинаю постепенное строительство города.");
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
            locations.set(marker + ".complete", true); locations.set(marker + ".active", false);
            putPoint("spawn", activeLevel, ox, oy + 1, oz, 0, 0);
            putPoint("castle", activeLevel, ox - 30, oy + 1, oz - 29, 0, 0);
            putPoint("market", activeLevel, ox + 23, oy + 1, oz - 29, 0, 0);
            putPoint("arena", activeLevel, ox, oy + 1, oz + 43, 0, 0);
            putPoint("sanctuary", activeLevel, ox - 34, oy + 1, oz + 25, 0, 0);
            locations.save();
            if (activePlayer != null) activePlayer.sendMessage("§a[Арден] Город построен! Установлено блоков: §f" + placed);
            stopGeneration();
        } else if (activePlayer != null && placed > 0 && placed % 10000 < PLACE_BUDGET) {
            activePlayer.sendMessage("§6[Арден] §eПоставлено: §f" + placed + " §7| в очереди: §f" + queue.size());
        }
    }

    private void queueArden(String marker) {
        Level w = activeLevel;
        for (int x=-58;x<=58;x++) for (int z=-58;z<=58;z++) {
            if (x*x+z*z > 58*58) continue;
            b(w,ox+x,oy-1,oz+z,STONE);
            if (Math.abs(x)<=3 || Math.abs(z)<=3) b(w,ox+x,oy,oz+z,COBBLE);
            else if (Math.abs(x)<54 && Math.abs(z)<54) b(w,ox+x,oy,oz+z,GRASS);
        }
        plaza(w,ox,oy,oz); castle(w,ox-30,oy,oz-29);
        marketHouse(w,ox+23,oy,oz-29,15,13); marketHouse(w,ox+39,oy,oz-13,13,15);
        marketHouse(w,ox+23,oy,oz+5,15,13); sanctuary(w,ox-34,oy,oz+25);
        guild(w,ox+31,oy,oz+28); arena(w,ox,oy,oz+43); perimeter(w,ox,oy,oz); avenueTrees(w,ox,oy,oz);
        locations.set(marker + ".queuedBlocks", queue.size()); locations.save();
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
        for(int xx=x+2;xx<x+sx-2;xx+=4){b(w,xx,y+2,z,GLASS);b(w,xx,y+2,z+sz-1,GLASS);}
        for(int zz=z+2;zz<z+sz-2;zz+=4){b(w,x,y+2,zz,GLASS);b(w,x+sx-1,y+2,zz,GLASS);}
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
    private void castle(Level w,int x,int y,int z){
        shell(w,x,y,z,27,23,STONE_BRICK,STONE_BRICK);
        // Four tall corner towers with battlements.
        for(int[] c:new int[][]{{0,0},{23,0},{0,19},{23,19}}){
            fill(w,x+c[0],y+1,z+c[1],x+c[0]+3,y+17,z+c[1]+3,STONE_BRICK);
            fill(w,x+c[0]+1,y+3,z+c[1]+1,x+c[0]+2,y+15,z+c[1]+2,AIR);
            fill(w,x+c[0]-1,y+18,z+c[1]-1,x+c[0]+4,y+19,z+c[1]+4,QUARTZ);
            b(w,x+c[0]+1,y+20,z+c[1]+1,GLOWSTONE);
        }
        fill(w,x+10,y+1,z+9,x+16,y+4,z+13,QUARTZ);
        fill(w,x+11,y+2,z+10,x+15,y+3,z+12,AIR);
        for(int xx=x+4;xx<x+23;xx+=3)b(w,xx,y+8,z+11,TORCH);
        // Courtyard garden and approach stairs.
        fill(w,x+5,y+1,z+5,x+9,y+1,z+8,GRASS);
        fill(w,x+17,y+1,z+5,x+21,y+1,z+8,GRASS);
        for(int i=1;i<=8;i++)fill(w,x+12-i,y+i,z-2-i,x+14+i,y+i,z-1-i,STONE_BRICK);
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
        s.sendMessage("§e/nexora build arden §7— очистить место и постепенно построить Арден");
    }
}
