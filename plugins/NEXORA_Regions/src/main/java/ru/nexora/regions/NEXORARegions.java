package ru.nexora.regions;
import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.level.Level;
import cn.nukkit.plugin.PluginBase;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
public final class NEXORARegions extends PluginBase {
 static final String PREFIX="§6✦ NEXORA Регионы §8» ";
 private RegionsModule regions;
 @Override public void onEnable(){saveDefaultConfig();regions=new RegionsModule(this);getServer().getPluginManager().registerEvents(regions,this);getLogger().info("NEXORA_Regions включён.");}
 @Override public void onDisable(){if(regions!=null)regions.flush();}
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){return regions.handle(sender,command.getName().toLowerCase(Locale.ROOT),args);}
 boolean inSpawnProtection(Level level,int x,int z){int radius=Math.max(0,getConfig().getInt("spawn-protection.radius",16));if(radius==0||level==null)return false;var spawn=level.getSpawnLocation();return Math.abs(x-spawn.getFloorX())<=radius&&Math.abs(z-spawn.getFloorZ())<=radius;}
 int claimLimit(Player player){return isAdmin(player)?100:Math.max(1,getConfig().getInt("regions.claim-limit",3));}
 static boolean isAdmin(CommandSender sender){return sender.isOp()||sender.hasPermission("nexora.regions.admin");}
 static String clean(String value){return value==null?"":value.replace("§","").replaceAll("\\p{Cntrl}","").trim();}
 static String money(long amount){return String.format(Locale.US,"%,d",amount);}
 static void choose(Player player,String title,String body,List<String> labels,IntConsumer onPick){FormWindowSimple form=new FormWindowSimple(title,body);for(String label:labels)form.addButton(new ElementButton(label));form.addHandler(FormResponseHandler.withoutPlayer(ignored->{if(form.wasClosed())return;int index=form.getResponse().getClickedButtonId();if(index>=0&&index<labels.size())onPick.accept(index);}));player.showFormWindow(form);}
}
