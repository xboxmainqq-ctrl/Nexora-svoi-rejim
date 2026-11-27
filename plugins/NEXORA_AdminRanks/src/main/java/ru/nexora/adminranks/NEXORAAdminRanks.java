package ru.nexora.adminranks;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NEXORAAdminRanks extends PluginBase {
    private static final int GLYPH_BASE = 0xF750;
    /** Letters already used by the 8 donate privileges (see NEXORADonate). */
    private static final int STAFF_GLYPH_START = 51;

    /**
     * Rank id and the label baked into the resource-pack font. Order and text must match
     * RANKS in resource-pack/tools/make_rank_gradient_font.py (verify_rank_glyph_map.py checks it).
     * The labels live in code on purpose: an old config.yml left on the server can never
     * shift the glyph positions or hide a rank.
     */
    private static final String[][] RANK_TABLE = {
            {"helper", "ХЕЛПЕР"},
            {"junior_moder", "МЛАДШИЙ МОДЕРАТОР"},
            {"moder", "МОДЕРАТОР"},
            {"senior_moder", "СТАРШИЙ МОДЕРАТОР"},
            {"admin", "АДМИНИСТРАТОР"},
            {"staff", "ГЛАВНЫЙ АДМИНИСТРАТОР"},
    };
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    private static final Map<String, Integer> OFFSETS = new LinkedHashMap<>();
    private static final Map<String, Integer> DEFAULT_HOMES = Map.of(
            "helper", 3, "junior_moder", 3, "moder", 10, "senior_moder", 10, "admin", 20, "staff", 20);
    private static final List<String> BASIC_KITS = List.of("start", "daily");
    private static final List<String> FULL_KITS = List.of("start", "daily", "vip");
    /** Other names for the top rank ("staff" stays the stored id). */
    private static final Map<String, String> ALIASES = Map.of(
            "chief_admin", "staff", "chiefadmin", "staff", "main_admin", "staff",
            "glav_admin", "staff", "glavadmin", "staff", "главадмин", "staff",
            "главный_администратор", "staff");

    static {
        int offset = STAFF_GLYPH_START;
        for (String[] row : RANK_TABLE) {
            LABELS.put(row[0], row[1]);
            OFFSETS.put(row[0], offset);
            offset += row[1].replace(" ", "").length();
        }
    }

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

    private static String normalize(String raw) {
        String id = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim().replace('-', '_');
        return ALIASES.getOrDefault(id, id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("adminrank")) return false;
        if (!sender.isOp() && !sender.hasPermission("nexora.adminranks.manage")) {
            sender.sendMessage("§cНедостаточно прав."); return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/adminrank <игрок> <ранг|remove>");
            sender.sendMessage("§7Ранги: " + String.join(", ", LABELS.keySet())
                    + " §8(staff = ГЛАВНЫЙ АДМИНИСТРАТОР, также chief_admin)");
            return true;
        }
        Player target = getServer().getPlayer(args[0]);
        if (target == null) { sender.sendMessage("§cИгрок должен быть онлайн."); return true; }
        String id = normalize(args[1]);
        if (id.equals("remove") || id.equals("none")) {
            removeRank(target.getUniqueId());
            sender.sendMessage("§aАдминистративный ранг снят с " + target.getName() + ".");
            target.sendMessage("§7Твой административный ранг снят.");
            return true;
        }
        if (!LABELS.containsKey(id)) {
            sender.sendMessage("§cНеизвестный ранг. Используй /adminrank для списка.");
            return true;
        }
        setRank(target.getUniqueId(), id);
        sender.sendMessage("§aВыдан ранг §f" + LABELS.get(id) + " §aигроку " + target.getName() + ".");
        target.sendMessage("§aТебе выдан административный ранг §f" + LABELS.get(id) + "§a.");
        return true;
    }

    public String getAdminRankId(UUID id) {
        String rank = normalize(ranks.getString("players." + id, ""));
        return LABELS.containsKey(rank) ? rank : "";
    }

    public void setRank(UUID id, String rank) {
        ranks.set("players." + id, normalize(rank));
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
        return rank.isEmpty() ? "" : gradient(LABELS.get(rank), OFFSETS.get(rank));
    }

    /** One glyph per letter: U+F750 + rank offset + letter position; the colour run is baked into the glyphs. */
    private String gradient(String value, int offset) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ' ') { out.append(' '); continue; }
            out.appendCodePoint(GLYPH_BASE + offset + pos);
            pos++;
        }
        return out.toString();
    }

    public int getHomesLimit(UUID id) {
        String rank = getAdminRankId(id);
        return rank.isEmpty() ? -1 : getConfig().getInt("ranks." + rank + ".homes", DEFAULT_HOMES.get(rank));
    }

    public boolean hasKit(UUID id, String kit) {
        String rank = getAdminRankId(id);
        if (rank.isEmpty()) return false;
        List<String> kits = getConfig().exists("ranks." + rank + ".kits")
                ? getConfig().getStringList("ranks." + rank + ".kits")
                : (rank.equals("helper") || rank.equals("junior_moder") ? BASIC_KITS : FULL_KITS);
        return kits.contains(kit);
    }
}
