package ru.nexora.donate;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.plugin.PluginBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NEXORADonate extends PluginBase {
    private static final String PREFIX = "§8[§dNEXORA§8] §r";
    private static final String GLYPH_CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
            + "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
            + "0123456789_-";
    private static final int GLYPH_BASE = 0xE300;
    private static final int GRADIENT_STEPS = 10;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Каталог привилегий NEXORA_Donate загружен. Покупка: "
                + (getConfig().getBoolean("purchase-enabled", false) ? "включена" : "пока отключена"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("donate")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только игроку.");
            return true;
        }
        openCatalog(player);
        return true;
    }

    private List<String> privilegeIds() {
        List<String> result = new ArrayList<>();
        for (String id : getConfig().getStringList("display-order")) {
            if (getConfig().exists("privileges." + id + ".name")) result.add(id);
        }
        return result;
    }

    private void openCatalog(Player player) {
        List<String> ids = privilegeIds();
        if (ids.isEmpty()) {
            player.sendMessage(PREFIX + "§cКаталог привилегий пока пуст.");
            return;
        }
        FormWindowSimple form = new FormWindowSimple(
                getConfig().getString("title", "§l§0NEXORA DONATE"),
                getConfig().getString("subtitle", "§7Выбери привилегию:"));
        for (String id : ids) {
            String base = "privileges." + id;
            String label = getConfig().getString(base + ".label", id.toUpperCase(Locale.ROOT));
            String gradient = getConfig().getString(base + ".gradient", "ocean");
            String preview = gradientText(label, gradient);
            String subtitle = getConfig().getString(base + ".short", "");
            form.addButton(new ElementButton(preview + (subtitle.isEmpty() ? "" : "\n" + subtitle)));
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
        if (!getConfig().exists(base + ".name")) {
            openCatalog(player);
            return;
        }
        StringBuilder body = new StringBuilder();
        body.append(getConfig().getString(base + ".description", "§7Описание скоро появится.")).append("\n\n");
        body.append("§l§fПРЕИМУЩЕСТВА\n");
        for (String benefit : getConfig().getStringList(base + ".benefits")) {
            body.append(benefit).append("\n");
        }
        body.append("\n§l§fСТОИМОСТЬ\n").append(getConfig().getString(base + ".price", "§eСкоро на сайте"));
        if (!getConfig().getBoolean("purchase-enabled", false)) {
            body.append("\n\n§8Покупка появится после запуска сайта NEXORA.");
        }

        String label = getConfig().getString(base + ".label", id.toUpperCase(Locale.ROOT));
        String gradient = getConfig().getString(base + ".gradient", "ocean");
        FormWindowSimple form = new FormWindowSimple(
                gradientText(label, gradient), body.toString());
        form.addButton(new ElementButton("§a↩ НАЗАД К ПРИВИЛЕГИЯМ"));
        form.addButton(new ElementButton("§c✕ ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed() || !player.isOnline()) return;
            if (form.getResponse().getClickedButtonId() == 0) openCatalog(player);
        }));
        player.showFormWindow(form);
    }
    private String gradientText(String value, String gradientId) {
        int gradient = switch (gradientId.toLowerCase(Locale.ROOT)) {
            case "sunset" -> 0;
            case "ocean" -> 1;
            case "neon" -> 2;
            case "royal" -> 3;
            default -> 1;
        };
        StringBuilder out = new StringBuilder();
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char ch = value.charAt(i);
            if (ch == ' ') {
                out.append(' ');
                continue;
            }
            int charIndex = GLYPH_CHARSET.indexOf(ch);
            if (charIndex < 0) {
                out.append(ch);
                continue;
            }
            int step = length <= 1 ? 0 : (int) Math.round((double) i * (GRADIENT_STEPS - 1) / (length - 1));
            int codePoint = GLYPH_BASE + ((gradient * GRADIENT_STEPS + step) * GLYPH_CHARSET.length()) + charIndex;
            out.appendCodePoint(codePoint);
        }
        return out.toString();
    }

}
