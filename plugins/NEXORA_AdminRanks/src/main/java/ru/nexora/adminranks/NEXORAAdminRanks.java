package ru.nexora.adminranks;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

public final class NEXORAAdminRanks extends PluginBase {
    private static final int GLYPH_BASE = 0xF750;
    private static final int STEPS = 3;
    private Config ranks;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        saveDefaultConfig();
        ranks = new Config(new File(getDataFolder(), "ranks.yml"), Config.YAML);
        getLogger().info("Отдельная система административных рангов загружена.");
    }

    @Override
    public void onDisable() { if (ranks != null) ranks.save(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("adminrank")) return false;
        if (!sender.isOp() && !sender.hasPermission("nexora.adminranks.manage")) {
            sender.sendMessage("§cНедостаточно прав."); return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/adminrank <игрок> <ранг|remove>");
            sender.sendMessage("§7Ранги: helper, junior_moder, moder, senior_moder, admin, staff");
            return true;
        }
        Player target = getServer().getPlayer(args[0]);
        if (target == null) { sender.sendMessage("§cИгрок должен быть онлайн."); return true; }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals("remove") || id.equals("none")) {
            removeRank(target.getUniqueId());
            sender.sendMessage("§aАдминистративный ранг снят с " + target.getName() + ".");
            target.sendMessage("§7Твой административный ранг снят.");
            return true;
        }
        if (!getConfig().exists("ranks." + id + ".label")) {
            sender.sendMessage("§cНеизвестный ранг. Используй /adminrank для списка.");
            return true;
        }
        setRank(target.getUniqueId(), id);
        sender.sendMessage("§aВыдан ранг §f" + id + " §aигроку " + target.getName() + ".");
        target.sendMessage("§aТебе выдан административный ранг §f" + getConfig().getString("ranks." + id + ".label") + "§a.");
        return true;
    }

    public String getAdminRankId(UUID id) {
        String rank = ranks.getString("players." + id, "");
        if (rank.equalsIgnoreCase("chief_admin")) rank = "staff";
        return getConfig().exists("ranks." + rank + ".label") ? rank : "";
    }

    public void setRank(UUID id, String rank) {
        ranks.set("players." + id, rank.toLowerCase(Locale.ROOT));
        ranks.save();
        refreshOnlinePlayer(id);
    }

    public void removeRank(UUID id) { ranks.remove("players." + id); ranks.save(); refreshOnlinePlayer(id); }

    private void refreshOnlinePlayer(UUID id) {
        Player player = getServer().getPlayer(id).orElse(null);
        Plugin survival = getServer().getPluginManager().getPlugin("NEXORA_Survival");
        if (player == null || survival == null || !survival.isEnabled()) return;
        try { survival.getClass().getMethod("refreshRankDisplay", Player.class).invoke(survival, player); }
        catch (ReflectiveOperationException ex) { getLogger().warning("Не удалось обновить отображение ранга: " + ex.getMessage()); }
    }

    public String getPrefix(UUID id) {
        String rank = getAdminRankId(id);
        if (rank.isEmpty()) return "";
        String label = getConfig().getString("ranks." + rank + ".label", "");
        int offset = switch (rank) {
            case "helper" -> 153;
            case "junior_moder" -> 171;
            case "moder" -> 219;
            case "senior_moder" -> 246;
            case "admin" -> 294;
            case "staff" -> 585;
            default -> -1;
        };
        return offset < 0 ? label : gradient(label, offset);
    }

    private String gradient(String value, int offset) {
        StringBuilder out = new StringBuilder();
        int count = value.replace(" ", "").length(), pos = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ' ') { out.append(' '); continue; }
            int step = count <= 1 ? 0 : (int)Math.round((double)pos * (STEPS - 1) / (count - 1));
            out.appendCodePoint(GLYPH_BASE + offset + pos * STEPS + step);
            pos++;
        }
        return out.toString();
    }

    public int getHomesLimit(UUID id) {
        String rank = getAdminRankId(id);
        return rank.isEmpty() ? -1 : getConfig().getInt("ranks." + rank + ".homes", 3);
    }

    public boolean hasKit(UUID id, String kit) {
        String rank = getAdminRankId(id);
        return !rank.isEmpty() && getConfig().getStringList("ranks." + rank + ".kits").contains(kit);
    }
}
