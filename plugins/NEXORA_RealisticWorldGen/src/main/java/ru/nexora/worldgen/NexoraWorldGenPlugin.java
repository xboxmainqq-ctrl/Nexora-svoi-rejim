package ru.nexora.worldgen;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.plugin.PluginBase;

import java.util.HashMap;

public final class NexoraWorldGenPlugin extends PluginBase {

    @Override
    public void onEnable() {
        if (!Generator.addGenerator(NexoraGenerator.class, "nexora", Generator.TYPE_INFINITE)) {
            getLogger().warning("Generator name 'nexora' is already registered; keeping the existing registration.");
        }
        getServer().getCommandMap().register("nexora", new Command("worldgen", "Create a NEXORA world", "/worldgen create <name> [seed]") {
            {
                setPermission("nexora.worldgen.create");
            }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!sender.hasPermission("nexora.worldgen.create")) {
                    sender.sendMessage("§cYou do not have permission.");
                    return true;
                }
                if (args.length < 2 || !args[0].equalsIgnoreCase("create")) {
                    sender.sendMessage("§eUsage: /worldgen create <name> [seed]");
                    return true;
                }

                String worldName = args[1].trim();
                if (!worldName.matches("[A-Za-z0-9_\\-]{1,32}")) {
                    sender.sendMessage("§cWorld name may contain only English letters, numbers, _ and - (max 32).");
                    return true;
                }
                if (getServer().isLevelGenerated(worldName)) {
                    sender.sendMessage("§cA world with this name already exists.");
                    return true;
                }

                long seed = System.currentTimeMillis();
                if (args.length >= 3) {
                    try {
                        seed = Long.parseLong(args[2]);
                    } catch (NumberFormatException exception) {
                        sender.sendMessage("§cSeed must be a whole number.");
                        return true;
                    }
                }

                HashMap<String, Object> options = new HashMap<>();
                boolean created = getServer().generateLevel(worldName, seed, Generator.getGenerator("nexora"), options);
                if (created) {
                    sender.sendMessage("§aNEXORA world created: §f" + worldName + " §7(seed " + seed + ")");
                    getLogger().info("Created NEXORA world '" + worldName + "' with seed " + seed);
                } else {
                    sender.sendMessage("§cCould not create world. Check the server console.");
                }
                return true;
            }
        });
        getLogger().info("NEXORA Realistic WorldGen enabled. Generator: nexora");
    }
}