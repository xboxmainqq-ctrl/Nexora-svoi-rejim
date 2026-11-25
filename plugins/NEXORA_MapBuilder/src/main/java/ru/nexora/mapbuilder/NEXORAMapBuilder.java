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
 * NEXORA MapBuilder: persistent named world locations.
 */
public final class NEXORAMapBuilder extends PluginBase {
    private static final Pattern LOCATION_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,32}");
    private Config locations;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку данных MapBuilder.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        locations = new Config(new File(getDataFolder(), "locations.yml"), Config.YAML);
        getLogger().info("NEXORA MapBuilder загружен. Сохранённых точек: " + locations.getSection("locations").size());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexora.admin") && !sender.isOp()) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> saveLocation(sender, "spawn");
            case "setloc" -> {
                if (args.length < 2) sender.sendMessage("§cИспользование: /nexora setloc <имя>");
                else saveLocation(sender, args[1]);
            }
            case "locations" -> listLocations(sender);
            case "tp" -> {
                if (args.length < 2) sender.sendMessage("§cИспользование: /nexora tp <имя>");
                else teleportTo(sender, args[1]);
            }
            case "help" -> sendHelp(sender);
            case "build" -> sender.sendMessage("§eСтроитель Ардена ещё разрабатывается. Сохранение точек уже доступно.");
            default -> sendHelp(sender);
        }
        return true;
    }

    private void saveLocation(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду можно выполнить только в игре.");
            return;
        }
        if (!LOCATION_NAME.matcher(name).matches()) {
            sender.sendMessage("§cИмя: 1–32 символа, только латиница, цифры, _ и -.");
            return;
        }

        String path = "locations." + name.toLowerCase(Locale.ROOT);
        Location location = player.getLocation();
        locations.set(path + ".world", player.getLevel().getFolderName());
        locations.set(path + ".x", location.getX());
        locations.set(path + ".y", location.getY());
        locations.set(path + ".z", location.getZ());
        locations.set(path + ".yaw", location.getYaw());
        locations.set(path + ".pitch", location.getPitch());
        locations.save();
        sender.sendMessage("§aТочка §f" + name + " §aсохранена в locations.yml.");
    }

    private void listLocations(CommandSender sender) {
        Map<String, Object> saved = locations.getSection("locations");
        if (saved.isEmpty()) {
            sender.sendMessage("§eСохранённых точек пока нет. Используй /nexora setloc <имя>.");
            return;
        }
        sender.sendMessage("§6Сохранённые точки NEXORA:");
        for (String name : saved.keySet()) {
            sender.sendMessage("§e- §f" + name);
        }
    }

    private void teleportTo(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду можно выполнить только в игре.");
            return;
        }
        if (!LOCATION_NAME.matcher(name).matches()) {
            sender.sendMessage("§cНекорректное имя точки.");
            return;
        }

        String path = "locations." + name.toLowerCase(Locale.ROOT);
        if (!locations.exists(path)) {
            sender.sendMessage("§cТочка §f" + name + " §cне найдена.");
            return;
        }

        String worldName = locations.getString(path + ".world", "");
        Level level = Server.getInstance().getLevelByName(worldName);
        if (level == null) {
            sender.sendMessage("§cМир §f" + worldName + " §cне загружен.");
            return;
        }

        Location destination = new Location(
                locations.getDouble(path + ".x"),
                locations.getDouble(path + ".y"),
                locations.getDouble(path + ".z"),
                (float) locations.getDouble(path + ".yaw"),
                (float) locations.getDouble(path + ".pitch"),
                level
        );
        player.teleport(destination);
        sender.sendMessage("§aТелепортация к точке §f" + name + "§a.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6NEXORA MapBuilder");
        sender.sendMessage("§e/nexora setspawn §7— сохранить основной спавн");
        sender.sendMessage("§e/nexora setloc <имя> §7— сохранить именованную точку");
        sender.sendMessage("§e/nexora locations §7— список сохранённых точек");
        sender.sendMessage("§e/nexora tp <имя> §7— телепорт к точке");
        sender.sendMessage("§e/nexora build <имя> §7— строительство (в разработке)");
    }
}
