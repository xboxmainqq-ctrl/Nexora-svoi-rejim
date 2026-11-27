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
            form.addButton(new ElementButton(getConfig().getString("privileges." + id + ".short",
                    getConfig().getString("privileges." + id + ".name", id))));
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

        FormWindowSimple form = new FormWindowSimple(
                getConfig().getString(base + ".name", id), body.toString());
        form.addButton(new ElementButton("§a↩ НАЗАД К ПРИВИЛЕГИЯМ"));
        form.addButton(new ElementButton("§c✕ ЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed() || !player.isOnline()) return;
            if (form.getResponse().getClickedButtonId() == 0) openCatalog(player);
        }));
        player.showFormWindow(form);
    }
}