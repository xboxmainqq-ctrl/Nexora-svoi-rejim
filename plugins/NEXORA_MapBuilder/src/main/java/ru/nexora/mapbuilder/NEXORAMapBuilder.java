package ru.nexora.mapbuilder;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.PluginBase;

/**
 * Initial MapBuilder entry point.
 * Persistent location storage and safe staged builders are added in following commits.
 */
public final class NEXORAMapBuilder extends PluginBase {
    @Override
    public void onEnable() {
        getLogger().info("NEXORA MapBuilder loaded. Use /nexora help");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexora.admin") && !sender.isOp()) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6NEXORA MapBuilder");
            sender.sendMessage("§e/nexora setspawn §7— сохранить основной спавн");
            sender.sendMessage("§e/nexora setloc <имя> §7— сохранить именованную точку");
            sender.sendMessage("§e/nexora locations §7— список точек");
            sender.sendMessage("§e/nexora tp <имя> §7— телепорт к точке");
            sender.sendMessage("§e/nexora build <имя> §7— строительство (будет включено после проверки API)");
            return true;
        }
        sender.sendMessage("§eЭта функция появится в следующем этапе разработки.");
        return true;
    }
}
