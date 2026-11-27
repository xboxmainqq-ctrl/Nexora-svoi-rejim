package ru.nexora.donate;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class NEXORADonate extends PluginBase {
    private static final String PREFIX = "§8[§dNEXORA§8] §r";
    private static final int GLYPH_BASE = 0xF750;

    /**
     * Privilege id and the label baked into the resource-pack font, in glyph order. Must match
     * RANKS in resource-pack/tools/make_rank_gradient_font.py (verify_rank_glyph_map.py checks it).
     * Offsets are cumulative letter counts, so these 8 ranks use glyphs 0..50 (NEXORA_AdminRanks continues at 51).
     */
    private static final String[][] GLYPH_TABLE = {
            {"aurvex", "AURVEX"}, {"zyreth", "ZYRETH"}, {"veloria", "VELORIA"}, {"kryon", "KRYON"},
            {"nymeris", "NYMERIS"}, {"solvex", "SOLVEX"}, {"eclyra", "ECLYRA"}, {"nexorion", "NEXORION"},
    };
    private static final Map<String, String> GLYPH_LABELS = new LinkedHashMap<>();
    private static final Map<String, Integer> GLYPH_OFFSETS = new LinkedHashMap<>();

    static {
        int offset = 0;
        for (String[] row : GLYPH_TABLE) {
            GLYPH_LABELS.put(row[0], row[1]);
            GLYPH_OFFSETS.put(row[0], offset);
            offset += row[1].replace(" ", "").length();
        }
    }

    private Config ranks;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        saveDefaultConfig();
        ranks = new Config(new File(getDataFolder(), "ranks.yml"), Config.YAML);
        getLogger().info("Каталог и отдельная система донат-привилегий NEXORA загружены.");
    }

    @Override
    public void onDisable() { if (ranks != null) ranks.save(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("donate")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Команда доступна только игроку."); return true;
            }
            openCatalog(player); return true;
        }
        if (!command.getName().equalsIgnoreCase("donaterank")) return false;
        if (!sender.isOp() && !sender.hasPermission("nexora.donate.manage")) {
            sender.sendMessage("§cНедостаточно прав."); return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§e/donaterank <игрок> <ранг|remove>");
            sender.sendMessage("§7Ранги: aurvex, zyreth, veloria, kryon, nymeris, solvex, eclyra, nexorion");
            return true;
        }
        Player target = getServer().getPlayer(args[0]);
        if (target == null) { sender.sendMessage("§cИгрок должен быть онлайн."); return true; }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals("remove") || id.equals("none")) {
            removeRank(target.getUniqueId());
            sender.sendMessage("§aДонат-привилегия снята с " + target.getName() + ".");
            target.sendMessage("§7Твоя донат-привилегия снята.");
            return true;
        }
        if (!getConfig().exists("privileges." + id + ".label")) {
            sender.sendMessage("§cНеизвестная привилегия. Используй /donaterank для списка.");
            return true;
        }
        setRank(target.getUniqueId(), id);
        sender.sendMessage("§aВыдана привилегия §f" + id.toUpperCase(Locale.ROOT) + " §aигроку " + target.getName() + ".");
        target.sendMessage("§aТебе выдана привилегия §f" + id.toUpperCase(Locale.ROOT) + "§a.");
        return true;
    }

    private List<String> privilegeIds() {
        List<String> result = new ArrayList<>();
        for (String id : getConfig().getStringList("display-order")) {
            if (getConfig().exists("privileges." + id + ".label")) result.add(id);
        }
        return result;
    }

    private void openCatalog(Player player) {
        List<String> ids = privilegeIds();
        if (ids.isEmpty()) {
            player.sendMessage(PREFIX + "§cКаталог привилегий пока пуст."); return;
        }
        FormWindowSimple form = new FormWindowSimple(
                getConfig().getString("title", "§l§0NEXORA"),
                getConfig().getString("subtitle", "§7Выбери привилегию NEXORA:"));
        for (String id : ids) {
            String base = "privileges." + id;
            String label = getConfig().getString(base + ".label", id.toUpperCase(Locale.ROOT));
            String gradient = getConfig().getString(base + ".gradient", id);
            String subtitle = getConfig().getString(base + ".short", "");
            form.addButton(new ElementButton(gradientText(label, gradient)
                    + (subtitle.isEmpty() ? "" : "\n§8" + subtitle)));
        }
        form.addButton(new ElementButton("§c✕ ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed() || !player.isOnline()) return;
            int choice = form.getResponse().getClickedButtonId();
            if (choice >= 0 && choice < ids.size()) openDetails(player, ids.get(choice));
        }));
        player.showFormWindow(form);
    }

    private void openDetails(Player player, String id) {
        String base = "privileges." + id;
        if (!getConfig().exists(base + ".label")) { openCatalog(player); return; }
        StringBuilder body = new StringBuilder();
        body.append(getConfig().getString(base + ".description", "§7Описание скоро появится.")).append("\n\n");
        body.append("§l§fОСОБЕННОСТИ ПРИВИЛЕГИИ\n");
        for (String benefit : getConfig().getStringList(base + ".benefits")) body.append(benefit).append("\n");
        body.append("\n§l§fСТОИМОСТЬ\n").append(getConfig().getString(base + ".price", "§eСкоро на сайте"));
        if (!getConfig().getBoolean("purchase-enabled", false))
            body.append("\n\n§8Покупка появится после запуска сайта NEXORA.");
        String label = getConfig().getString(base + ".label", id.toUpperCase(Locale.ROOT));
        FormWindowSimple form = new FormWindowSimple(gradientText(label, id), body.toString());
        form.addButton(new ElementButton("§a↩ К СПИСКУ ПРИВИЛЕГИЙ"));
        form.addButton(new ElementButton("§c✕ ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed() || !player.isOnline()) return;
            if (form.getResponse().getClickedButtonId() == 0) openCatalog(player);
        }));
        player.showFormWindow(form);
    }

    /** One glyph per letter (U+F750 + offset + position); the gradient is baked into the font. */
    private String gradientText(String value, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        Integer offset = GLYPH_OFFSETS.get(key);
        if (offset == null) return value;
        // The built-in label is what the font was drawn for; config text cannot shift the glyphs.
        String label = GLYPH_LABELS.get(key);
        StringBuilder out = new StringBuilder();
        int position = 0;
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (ch == ' ') { out.append(' '); continue; }
            out.appendCodePoint(GLYPH_BASE + offset + position);
            position++;
        }
        return out.toString();
    }

    public String getRankId(UUID id) {
        String rank = ranks.getString("players." + id, "");
        rank = switch (rank.toLowerCase(Locale.ROOT)) {
            case "vip" -> "aurvex"; case "premium" -> "veloria";
            case "elite" -> "solvex"; case "legend" -> "nexorion";
            default -> rank;
        };
        return getConfig().exists("privileges." + rank + ".label") ? rank : "";
    }

    public void setRank(UUID id, String rank) {
        ranks.set("players." + id, rank.toLowerCase(Locale.ROOT)); ranks.save();
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
        String rank = getRankId(id);
        if (rank.isEmpty()) return "";
        return gradientText(getConfig().getString("privileges." + rank + ".label", ""), rank);
    }

    public int getHomesLimit(UUID id) {
        String rank = getRankId(id);
        if (rank.isEmpty()) return -1;
        return getConfig().getInt("privileges." + rank + ".homes", 3);
    }

    public boolean hasKit(UUID id, String kit) {
        String rank = getRankId(id);
        return !rank.isEmpty() && getConfig().getStringList("privileges." + rank + ".kits").contains(kit);
    }
}
