package ru.nexora.seller;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.Listener;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NEXORASeller extends PluginBase implements Listener {
    private Config economy;
    private Plugin survival;
    private File balanceFile;
    private Config balances;

    private record Category(String id, String title, String description) { }
    private static final List<Category> CATEGORIES = List.of(
            new Category("food", "§l§6🍖 ЕДА", "Мясо, хлеб, рыба и готовая еда"),
            new Category("ores", "§l§b💎 РУДА", "Слитки, драгоценности и минералы"),
            new Category("blocks", "§l§7▣ БЛОКИ", "Камень, песок, дерево и стройматериалы"),
            new Category("farm", "§l§a❀ ФЕРМА", "Урожай, семена и растения"),
            new Category("drops", "§l§c✦ ДОБЫЧА", "Трофеи с мобов и полезные материалы")
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        survival = getServer().getPluginManager().getPlugin("NEXORA_Survival");
        File data = survival != null ? survival.getDataFolder() : getDataFolder();
        if (!data.exists()) data.mkdirs();
        balanceFile = new File(data, "economy.yml");
        balances = new Config(balanceFile, Config.YAML);
        economy = survival != null ? survival.getConfig() : getConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NEXORA_Seller включён. Скупщик вынесен в отдельный плагин.");
    }

    @Override
    public void onDisable() {
        if (balances != null) balances.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "sell" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cКоманда доступна только игроку."); return true; }
                if (args.length == 0) openRoot(player);
                else if (args[0].equalsIgnoreCase("all")) { sellAll(player); openRoot(player); }
                else if (args[0].equalsIgnoreCase("hand")) sellHand(player, args);
                else player.sendMessage("§e/sell hand [количество] §7или §e/sell all");
            }
            case "seller" -> {
                if (sender instanceof Player player) openRoot(player);
                else sender.sendMessage("§cСкупщик доступен только игроку.");
            }
            case "prices" -> {
                if (sender instanceof Player player) openPrices(player);
                else sender.sendMessage(priceText());
            }
            default -> { return false; }
        }
        return true;
    }

    private String currency() { return economy.getString("economy.currency", "монет"); }
    private String money(long amount) { return "§e" + amount + " §6" + currency(); }
    private ConfigSection prices() { return economy.getSection("economy.sell-prices"); }

    private int price(Item item) {
        if (item == null || item.isNull() || item.hasCustomName() || item.hasEnchantments()) return 0;
        return economy.getInt("economy.sell-prices." + item.getId() + ".price", 0);
    }
    private long balance(UUID id) {
        return balances.getLong("balances." + id, economy.getLong("economy.start-balance", 100));
    }
    private void deposit(Player player, long amount) {
        UUID id = player.getUniqueId();
        balances.set("balances." + id, balance(id) + amount);
        balances.set("names." + id, player.getName());
        balances.save();
    }

    private String categoryOf(String id) {
        return switch (id) {
            case "260","297","320","363","364","349","350","393","400" -> "food";
            case "263","265","266","264","388","331","351","406" -> "ores";
            case "1","4","5","12","13","17","45","49","82","87","121" -> "blocks";
            case "295","296","391","392","338","360","361","362","86","103" -> "farm";
            case "287","288","289","334","341","352","367","375" -> "drops";
            default -> "other";
        };
    }
    private int count(Player p, String category) {
        int n=0;
        for (Item i:p.getInventory().getContents().values())
            if (price(i)>0 && (category==null || category.equals(categoryOf(String.valueOf(i.getId()))))) n+=i.getCount();
        return n;
    }
    private long value(Player p, String category) {
        long n=0;
        for (Item i:p.getInventory().getContents().values())
            if (price(i)>0 && (category==null || category.equals(categoryOf(String.valueOf(i.getId()))))) n+=(long)price(i)*i.getCount();
        return n;
    }
    private void openRoot(Player p) {
        FormWindowSimple f=new FormWindowSimple("§l§0✦ NEXORA · СКУПЩИК",
                "§8━━━━━━━━━━━━━━━━━━━━\n§7Выбирай отдел или продавай всё сразу.\n§8━━━━━━━━━━━━━━━━━━━━\n"
                +"§eБаланс: §f"+balance(p.getUniqueId())+" "+currency()+"\n§aМожно продать: §f"+count(p,null)+" шт.\n"
                +"§6Оценка инвентаря: §f"+value(p,null)+" "+currency());
        for(Category c:CATEGORIES) f.addButton(new ElementButton(c.title()+"\n§8"+c.description()+"\n§eВ наличии: §f"+count(p,c.id())+" шт."));
        f.addButton(new ElementButton("§l§a✦ ПРОДАТЬ ВСЁ\n§8"+count(p,null)+" шт. · "+value(p,null)+" "+currency()));
        f.addButton(new ElementButton("§7Прайс-лист"));
        f.addButton(new ElementButton("§8Закрыть"));
        f.addHandler(FormResponseHandler.withoutPlayer(ignored->{if(f.wasClosed())return;int i=f.getResponse().getClickedButtonId();
            if(i>=0&&i<CATEGORIES.size())openCategory(p,CATEGORIES.get(i));
            else if(i==CATEGORIES.size()){sellAll(p);openRoot(p);}
            else if(i==CATEGORIES.size()+1)openPrices(p);
        }));
        p.showFormWindow(f);
    }
    private void openCategory(Player p, Category c) {
        ConfigSection section=prices();
        List<String> ids=new ArrayList<>();
        FormWindowSimple f=new FormWindowSimple("§l§0СКУПЩИК · "+c.title(),
                "§8"+c.description()+"\n§7В отделе: §f"+count(p,c.id())+" шт.\n§7Стоимость: §e"+value(p,c.id())+" "+currency());
        for(String id:section.getKeys(false)) if(c.id().equals(categoryOf(id))) {
            ids.add(id);
            f.addButton(new ElementButton("§f"+economy.getString("economy.sell-prices."+id+".name",id)
                    +"\n§7У тебя: §f"+countId(p,Integer.parseInt(id))+" шт. §8| §e"
                    +economy.getInt("economy.sell-prices."+id+".price",0)+" мон./шт."));
        }
        f.addButton(new ElementButton("§l§a✦ ПРОДАТЬ ВЕСЬ ОТДЕЛ"));
        f.addButton(new ElementButton("§c🟥 СТЕКЛО · НАЗАД К ОТДЕЛАМ"));
        f.addButton(new ElementButton("§8⬛ ЗАКРЫТЬ МЕНЮ"));
        f.addHandler(FormResponseHandler.withoutPlayer(ignored->{if(f.wasClosed())return;int i=f.getResponse().getClickedButtonId();
            if(i>=0&&i<ids.size())sellId(p,Integer.parseInt(ids.get(i)),c);
            else if(i==ids.size()){sellCategory(p,c);openCategory(p,c);}
            else if(i==ids.size()+1)openRoot(p);
        }));
        p.showFormWindow(f);
    }
    private int countId(Player p,int id){int n=0;for(Item i:p.getInventory().getContents().values())if(i.getId()==id&&price(i)>0)n+=i.getCount();return n;}
    private void sellId(Player p,int id,Category c){
        int unit=economy.getInt("economy.sell-prices."+id+".price",0),n=0;long total=0;
        Map<Integer,Item> copy=new HashMap<>(p.getInventory().getContents());
        for(var e:copy.entrySet()){Item it=e.getValue();if(it.getId()!=id||price(it)<=0)continue;n+=it.getCount();total+=(long)unit*it.getCount();p.getInventory().clear(e.getKey());}
        if(total>0){deposit(p,total);p.sendMessage("§6✦ NEXORA §8» §aПродано §f"+n+" шт. §aза "+money(total));}
        else p.sendMessage("§6✦ NEXORA §8» §cУ тебя нет этого товара.");
        openCategory(p,c);
    }
    private void sellCategory(Player p,Category c){
        long total=0;int n=0;Map<Integer,Item> copy=new HashMap<>(p.getInventory().getContents());
        for(var e:copy.entrySet()){Item it=e.getValue();if(price(it)<=0||!c.id().equals(categoryOf(String.valueOf(it.getId()))))continue;
            n+=it.getCount();total+=(long)price(it)*it.getCount();p.getInventory().clear(e.getKey());}
        if(total>0){deposit(p,total);p.sendMessage("§6✦ NEXORA §8» §aОтдел принял §f"+n+" шт. §aза "+money(total));}
        else p.sendMessage("§6✦ NEXORA §8» §cВ этом отделе нечего продавать.");
    }
    private void sellAll(Player p){
        long total=0;int n=0;Map<Integer,Item> copy=new HashMap<>(p.getInventory().getContents());
        for(var e:copy.entrySet()){Item it=e.getValue();int v=price(it);if(v<=0)continue;n+=it.getCount();total+=(long)v*it.getCount();p.getInventory().clear(e.getKey());}
        if(total>0){deposit(p,total);p.sendMessage("§6✦ NEXORA §8» §aПродано предметов: §f"+n+" §aза "+money(total));}
        else p.sendMessage("§6✦ NEXORA §8» §cВ инвентаре нет товаров для продажи.");
    }
    private void sellHand(Player p,String[] args){
        Item it=p.getInventory().getItemInHand();int unit=price(it);
        if(unit<=0){p.sendMessage("§6✦ NEXORA §8» §cЭтот предмет нельзя продать. §e/prices");return;}
        int n=it.getCount();
        if(args.length>1){try{n=Math.min(n,Math.max(0,Integer.parseInt(args[1])));}catch(NumberFormatException ex){p.sendMessage("§cУкажи количество числом.");return;}}
        if(n<=0)return;
        if(n>=it.getCount())p.getInventory().setItemInHand(Item.get(0,0,0));
        else{it.setCount(it.getCount()-n);p.getInventory().setItemInHand(it);}
        long total=(long)unit*n;deposit(p,total);p.sendMessage("§6✦ NEXORA §8» §aПродано §f"+n+" шт. §aза "+money(total));
    }
    private String priceText(){
        StringBuilder b=new StringBuilder("§7Цена за 1 штуку:\n");
        for(String id:prices().getKeys(false))b.append("\n§f").append(economy.getString("economy.sell-prices."+id+".name",id))
                .append(" §8— ").append(money(economy.getInt("economy.sell-prices."+id+".price",0)));
        return b.toString();
    }
    private void openPrices(Player p){
        FormWindowSimple f=new FormWindowSimple("§l§0ЦЕНЫ СКУПКИ",priceText());
        f.addButton(new ElementButton("§c🟥 СТЕКЛО · НАЗАД К СКУПЩИКУ"));
        f.addButton(new ElementButton("§8⬛ ЗАКРЫТЬ МЕНЮ"));
        f.addHandler(FormResponseHandler.withoutPlayer(ignored->{if(!f.wasClosed()&&f.getResponse().getClickedButtonId()==0)openRoot(p);}));
        p.showFormWindow(f);
    }
}
