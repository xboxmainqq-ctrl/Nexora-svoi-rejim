package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerLoginEvent;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** Persistent moderation actions and styled player-facing notices. */
final class ModerationModule implements Listener {
    private final NEXORASurvival plugin;
    private final Store store;

    ModerationModule(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.store = new Store(new File(plugin.getDataFolder(), "moderation.yml"));
    }

    void flush() { store.flush(); }

    boolean handle(CommandSender sender, String command, String[] args) {
        switch (command) {
            case "kick" -> kick(sender, args);
            case "ban" -> punish(sender, args, true);
            case "mute" -> punish(sender, args, false);
            case "unban" -> pardon(sender, args, true);
            case "unmute" -> pardon(sender, args, false);
            default -> { return false; }
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (NEXORASurvival.isAdmin(sender)) return true;
        sender.sendMessage(PREFIX + "§cУ тебя нет прав на это действие.");
        return false;
    }

    private String reason(String[] args, int from) {
        if (args.length <= from) return "Нарушение правил сервера";
        String value = NEXORASurvival.clean(String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)));
        if (value.isEmpty()) return "Нарушение правил сервера";
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private void kick(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§e/kick <игрок> <причина>");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(PREFIX + "§cИгрок должен быть в сети.");
            return;
        }
        if (target.isOp() && !sender.isOp()) {
            sender.sendMessage(PREFIX + "§cНельзя кикнуть оператора.");
            return;
        }
        String why = reason(args, 1);
        target.kick(kickScreen(why), false);
        sender.sendMessage(PREFIX + "§aИгрок §f" + target.getName() + " §aисключён. §7Причина: §f" + why);
    }

    private void punish(CommandSender sender, String[] args, boolean ban) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(ban ? PREFIX + "§e/ban <игрок> [время] <причина>" : PREFIX + "§e/mute <игрок> [время] <причина>");
            sender.sendMessage("§7Время: §f30s, 5m, 2h, 7d, 2w, 1d12h §8| §fperm §7— навсегда");
            return;
        }
        String name = NEXORASurvival.clean(args[0]);
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            sender.sendMessage(PREFIX + "§cНекорректный ник игрока.");
            return;
        }
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null && online.isOp() && !sender.isOp()) {
            sender.sendMessage(PREFIX + "§cНельзя наказать оператора.");
            return;
        }

        int reasonAt = 1;
        long expires = 0L;
        String duration = "навсегда";
        if (args.length >= 3) {
            long parsed = parseDuration(args[1]);
            if (parsed > 0L) {
                expires = System.currentTimeMillis() + parsed;
                duration = prettyDuration(parsed);
                reasonAt = 2;
            } else if (args[1].equalsIgnoreCase("perm") || args[1].equalsIgnoreCase("permanent")) {
                reasonAt = 2;
            }
        }
        String why = reason(args, reasonAt);
        String key = name.toLowerCase(Locale.ROOT);
        String base = (ban ? "bans." : "mutes.") + key;
        store.cfg.set(base + ".name", name);
        store.cfg.set(base + ".reason", why);
        store.cfg.set(base + ".by", sender.getName());
        store.cfg.set(base + ".created", System.currentTimeMillis());
        store.cfg.set(base + ".expires", expires);
        store.touch();
        store.flush();

        if (online != null) {
            if (ban) online.kick(banScreen(why, duration), false);
            else online.sendMessage(muteScreen(why, duration));
        }
        sender.sendMessage(PREFIX + (ban ? "§cБан выдан игроку §f" : "§eМут выдан игроку §f")
                + name + " §8| §7Срок: §f" + duration + " §8| §7Причина: §f" + why);
    }

    private void pardon(CommandSender sender, String[] args, boolean ban) {
        if (!requireAdmin(sender)) return;
        if (args.length < 1) {
            sender.sendMessage(ban ? PREFIX + "§e/unban <игрок>" : PREFIX + "§e/unmute <игрок>");
            return;
        }
        String key = NEXORASurvival.clean(args[0]).toLowerCase(Locale.ROOT);
        String base = (ban ? "bans." : "mutes.") + key;
        if (!store.cfg.exists(base + ".name")) {
            sender.sendMessage(PREFIX + "§cДля этого игрока нет активного " + (ban ? "бана." : "мута."));
            return;
        }
        store.cfg.remove(base);
        store.touch();
        store.flush();
        sender.sendMessage(PREFIX + "§a" + (ban ? "Бан" : "Мут") + " игрока §f" + args[0] + " §aснят.");
        Player online = plugin.getServer().getPlayerExact(args[0]);
        if (online != null && !ban) online.sendMessage("§a§lNEXORA §8» §r§aТвой мут снят администрацией.");
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String key = player.getName().toLowerCase(Locale.ROOT);
        String base = "bans." + key;
        if (!store.cfg.exists(base + ".name")) return;
        long expires = store.cfg.getLong(base + ".expires", 0L);
        if (expires > 0L && System.currentTimeMillis() >= expires) {
            store.cfg.remove(base);
            store.touch();
            store.flush();
            return;
        }
        String why = store.cfg.getString(base + ".reason", "Нарушение правил сервера");
        long remaining = expires == 0L ? 0L : expires - System.currentTimeMillis();
        String duration = remaining == 0L ? "навсегда" : prettyDuration(remaining);
        event.setCancelled(true);
        event.setKickMessage(banScreen(why, duration));
    }

    @EventHandler
    public void onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String base = "mutes." + player.getName().toLowerCase(Locale.ROOT);
        if (!store.cfg.exists(base + ".name")) return;
        long expires = store.cfg.getLong(base + ".expires", 0L);
        if (expires > 0L && System.currentTimeMillis() >= expires) {
            store.cfg.remove(base);
            store.touch();
            store.flush();
            return;
        }
        event.setCancelled(true);
        String why = store.cfg.getString(base + ".reason", "Нарушение правил сервера");
        long remaining = expires == 0L ? 0L : expires - System.currentTimeMillis();
        player.sendMessage(muteScreen(why, remaining == 0L ? "навсегда" : prettyDuration(remaining)));
    }

    private String kickScreen(String why) {
        return "§c§lNEXORA §8| §fВЫ ИСКЛЮЧЕНЫ\n\n§7Администрация отключила вас от сервера.\n§7Причина: §f" + why
                + "\n\n§8Если считаете наказание ошибочным — обратитесь к администрации.";
    }

    private String banScreen(String why, String duration) {
        return "§c§lNEXORA §8| §4БЛОКИРОВКА ДОСТУПА\n\n§7Доступ к серверу временно ограничен.\n"
                + "§7Причина: §f" + why + "\n§7Срок: §f" + duration
                + "\n\n§8По вопросам обращайтесь к администрации.";
    }

    private String muteScreen(String why, String duration) {
        return "§6§lNEXORA §8| §eЧАТ ОГРАНИЧЕН\n§7Ты не можешь отправлять сообщения.\n"
                + "§7Причина: §f" + why + "\n§7Срок: §f" + duration;
    }

    /**
     * Accepts compact durations such as 30m, 2h, 7d, 2w and combinations
     * such as 1d12h30m. The total duration is capped at ten years.
     */
    private long parseDuration(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        if (v.equals("perm") || v.equals("permanent")) return 0L;
        if (v.isEmpty()) return -1L;

        Pattern part = Pattern.compile("(\\\\d+)([smhdw])");
        Matcher matcher = part.matcher(v);
        long total = 0L;
        int end = 0;
        try {
            while (matcher.find()) {
                if (matcher.start() != end) return -1L;
                long amount = Long.parseLong(matcher.group(1));
                if (amount <= 0L) return -1L;
                long unit = switch (matcher.group(2).charAt(0)) {
                    case 's' -> 1_000L;
                    case 'm' -> 60_000L;
                    case 'h' -> 3_600_000L;
                    case 'd' -> 86_400_000L;
                    case 'w' -> 604_800_000L;
                    default -> -1L;
                };
                total = Math.addExact(total, Math.multiplyExact(amount, unit));
                if (total > 3650L * 86_400_000L) return -1L;
                end = matcher.end();
            }
        } catch (ArithmeticException | NumberFormatException ex) {
            return -1L;
        }
        return end == v.length() && end > 0 ? total : -1L;
    }

    private String prettyDuration(long millis) {
        long minutes = Math.max(1L, millis / 60_000L);
        if (minutes % 10080 == 0) return (minutes / 10080) + " нед.";
        if (minutes % 1440 == 0) return (minutes / 1440) + " дн.";
        if (minutes % 60 == 0) return (minutes / 60) + " ч.";
        return minutes + " мин.";
    }
}
