package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** Player reports for moderators. Reports are stored persistently in reports.yml. */
final class ReportModule {
    private static final long COOLDOWN_MS = 60_000L;
    private static final int MAX_REASON_LENGTH = 120;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final NEXORASurvival plugin;
    private final Store store;

    ReportModule(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.store = new Store(new File(plugin.getDataFolder(), "reports.yml"));
    }

    void flush() { store.flush(); }

    boolean handle(CommandSender sender, String command, String[] args) {
        if (command.equals("reports")) {
            list(sender);
            return true;
        }
        if (!command.equals("report")) return false;
        if (args.length > 0 && args[0].equalsIgnoreCase("close")) {
            close(sender, args);
        } else {
            create(sender, args);
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cЖалобу можно отправить только из игры.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(PREFIX + "§e/report <игрок> <причина>");
            player.sendMessage("§7Пример: §f/report Steve грифит мой дом");
            return;
        }
        String target = NEXORASurvival.clean(args[0]);
        String reason = NEXORASurvival.clean(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        if (!target.matches("[A-Za-z0-9_]{3,16}")) {
            player.sendMessage(PREFIX + "§cУкажи корректный ник игрока (3–16 символов).");
            return;
        }
        if (target.equalsIgnoreCase(player.getName())) {
            player.sendMessage(PREFIX + "§cНельзя отправить жалобу на самого себя.");
            return;
        }
        if (reason.length() < 4 || reason.length() > MAX_REASON_LENGTH) {
            player.sendMessage(PREFIX + "§cПричина должна содержать от 4 до " + MAX_REASON_LENGTH + " символов.");
            return;
        }
        long now = System.currentTimeMillis();
        String cooldownPath = "cooldowns." + player.getUniqueId();
        long last = store.cfg.getLong(cooldownPath, 0L);
        if (now - last < COOLDOWN_MS) {
            long seconds = Math.max(1L, (COOLDOWN_MS - (now - last) + 999L) / 1000L);
            player.sendMessage(PREFIX + "§cПодожди ещё " + seconds + " сек. перед следующей жалобой.");
            return;
        }

        int id = Math.max(1, store.cfg.getInt("next-id", 1));
        String base = "reports." + id;
        store.cfg.set(base + ".reporter", player.getName());
        store.cfg.set(base + ".reporterUuid", player.getUniqueId().toString());
        store.cfg.set(base + ".target", target);
        store.cfg.set(base + ".reason", reason);
        store.cfg.set(base + ".created", now);
        store.cfg.set(base + ".status", "open");
        store.cfg.set("next-id", id + 1);
        store.cfg.set(cooldownPath, now);
        store.touch();
        store.flush();

        player.sendMessage(PREFIX + "§aЖалоба №§f" + id + " §aотправлена администрации.");
        String alert = PREFIX + "§cНовая жалоба №" + id + " §8| §f" + target
                + " §8| §7от §f" + player.getName() + " §8| §7" + reason
                + " §8— §e/reports";
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            if (NEXORASurvival.isAdmin(online)) online.sendMessage(alert);
        }
    }

    private void list(CommandSender sender) {
        if (!NEXORASurvival.isAdmin(sender)) {
            sender.sendMessage(PREFIX + "§cПросматривать жалобы может только администрация.");
            return;
        }
        ConfigSection all = store.cfg.getSection("reports");
        List<Integer> ids = new ArrayList<>();
        for (String key : all.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                if (store.cfg.getString("reports." + key + ".status", "open").equals("open")) ids.add(id);
            } catch (NumberFormatException ignored) { }
        }
        ids.sort(Comparator.reverseOrder());
        sender.sendMessage("§8§m------------------------");
        sender.sendMessage("§c§lЖАЛОБЫ §8| §7открыто: §f" + ids.size());
        int shown = 0;
        for (int id : ids) {
            if (shown++ >= 10) break;
            String base = "reports." + id;
            long created = store.cfg.getLong(base + ".created", 0L);
            sender.sendMessage("§8#" + id + " §f" + store.cfg.getString(base + ".target", "?")
                    + " §8| §7от §f" + store.cfg.getString(base + ".reporter", "?"));
            sender.sendMessage(" §7" + store.cfg.getString(base + ".reason", "")
                    + " §8(" + DATE.format(Instant.ofEpochMilli(created)) + ")");
            sender.sendMessage(" §8Закрыть: §e/report close " + id);
        }
        if (ids.isEmpty()) sender.sendMessage("§7Открытых жалоб нет.");
        sender.sendMessage("§8§m------------------------");
    }

    private void close(CommandSender sender, String[] args) {
        if (!NEXORASurvival.isAdmin(sender)) {
            sender.sendMessage(PREFIX + "§cЗакрывать жалобы может только администрация.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§e/report close <номер>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(PREFIX + "§cНомер жалобы должен быть числом.");
            return;
        }
        String base = "reports." + id;
        if (id < 1 || !store.cfg.exists(base + ".reporter")) {
            sender.sendMessage(PREFIX + "§cЖалоба №" + id + " не найдена.");
            return;
        }
        if (store.cfg.getString(base + ".status", "open").equals("closed")) {
            sender.sendMessage(PREFIX + "§eЖалоба №" + id + " уже закрыта.");
            return;
        }
        store.cfg.set(base + ".status", "closed");
        store.cfg.set(base + ".closedBy", sender.getName());
        store.cfg.set(base + ".closedAt", System.currentTimeMillis());
        store.touch();
        store.flush();
        sender.sendMessage(PREFIX + "§aЖалоба №" + id + " закрыта.");
    }
}
