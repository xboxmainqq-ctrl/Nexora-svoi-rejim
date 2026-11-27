package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.Listener;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.utils.ConfigSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static ru.nexora.survival.NEXORASurvival.PREFIX;

/** Simple social clans: one clan per player, leader/deputies/members and invitations. No wars or territory mechanics. */
final class ClanModule implements Listener {
    private static final int MAX_MEMBERS = 20;
    private static final long INVITE_TTL = 5 * 60 * 1000L;
    private final NEXORASurvival plugin;
    private final Store store;

    ClanModule(NEXORASurvival plugin) {
        this.plugin = plugin;
        this.store = new Store(new File(plugin.getDataFolder(), "clans.yml"));
    }

    void flush() { store.flush(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        store.cfg.set("names." + event.getPlayer().getUniqueId(), event.getPlayer().getName());
        store.touch();
    }

    String clanId(UUID player) {
        ConfigSection all = store.cfg.getSection("clans");
        for (String id : all.getKeys(false)) {
            if (store.cfg.getString("clans." + id + ".leader", "").equals(player.toString())
                    || store.cfg.getStringList("clans." + id + ".members").contains(player.toString())) return id;
        }
        return "";
    }

    // Bedrock 1.19.80+ supports true RGB text colors with §x§R§R§G§G§B§B.
    // No custom font or resource-pack glyphs are needed for clan tags.
    private static final String[] SOLID_NAMES = {
            "Красный", "Коралловый", "Оранжевый", "Золотой", "Жёлтый", "Лаймовый",
            "Зелёный", "Изумрудный", "Бирюзовый", "Голубой", "Синий", "Королевский синий",
            "Фиолетовый", "Лиловый", "Розовый", "Белый"
    };
    private static final int[] SOLID_RGB = {
            0xFF3B30, 0xFF6B6B, 0xFF8C42, 0xFFC247, 0xFFE45E, 0xB7E34B,
            0x35C46A, 0x00A878, 0x00C9B7, 0x38C9FF, 0x2878FF, 0x536DFE,
            0x8B5CF6, 0xB56BFF, 0xFF63B8, 0xF4F7FF
    };
    private static final String[] GRADIENT_NAMES = {
            "Закат", "Океан", "Неон", "Огонь", "Лес", "Королевский",
            "Золото", "Лёд", "Вишня", "Карамель", "Токсик", "Аква"
    };
    private static final String[] GRADIENT_IDS = {
            "sunset", "ocean", "neon", "fire", "forest", "royal",
            "gold", "ice", "cherry", "cotton", "toxic", "aqua"
    };
    private static final int[][] GRADIENT_RGB = {
            {0xFF512F, 0xF09819}, {0x00C6FF, 0x0072FF}, {0xFF00CC, 0x333399},
            {0xFF416C, 0xFF4B2B}, {0x11998E, 0x38EF7D}, {0x4776E6, 0x8E54E9},
            {0xF7971E, 0xFFE259}, {0x74EBD5, 0xACB6E5}, {0xEB3349, 0xF45C43},
            {0xF6D365, 0xFDA085}, {0x76B852, 0x8DC26F}, {0x00F5A0, 0x00D9F5}
    };
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final String LEGACY_CODES = "0123456789abcdef";

    String tag(Player player) {
        String id = clanId(player.getUniqueId());
        return id.isEmpty() ? "" : tag(id);
    }

    private String tag(String id) {
        String clanName = store.cfg.getString("clans." + id + ".name", id);
        String type = store.cfg.getString("clans." + id + ".color.type", "");
        String fallbackFrom = store.cfg.getString("clans." + id + ".color.from", "b");
        String fallbackTo = store.cfg.getString("clans." + id + ".color.to", fallbackFrom);
        if (type.isEmpty()) {
            // Migrate clans created with the previous palette-based color setting.
            int from = legacyRgb(fallbackFrom, 0x38C9FF);
            int to = legacyRgb(fallbackTo, from);
            if (from != to) return gradientText("[" + clanName + "]", from, to) + "§r";
            return solidText("[" + clanName + "]", from) + "§r";
        }
        if (type.equals("gradient")) {
            int from = parseHex(store.cfg.getString("clans." + id + ".color.fromHex", "38C9FF"), 0x38C9FF);
            int to = parseHex(store.cfg.getString("clans." + id + ".color.toHex", "0072FF"), 0x0072FF);
            return gradientText("[" + clanName + "]", from, to) + "§r";
        }
        int rgb = parseHex(store.cfg.getString("clans." + id + ".color.solid", "38C9FF"), 0x38C9FF);
        return solidText("[" + clanName + "]", rgb) + "§r";
    }

    private int legacyRgb(String code, int fallback) {
        if (code == null || code.length() != 1) return fallback;
        int index = LEGACY_CODES.indexOf(code.toLowerCase(Locale.ROOT));
        return index < 0 ? fallback : LEGACY_RGB[index];
    }

    private int parseHex(String value, int fallback) {
        try {
            String hex = value == null ? "" : value.replace("#", "").trim();
            return hex.matches("(?i)[0-9a-f]{6}") ? Integer.parseInt(hex, 16) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String hexCode(int rgb) {
        String hex = String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
        StringBuilder code = new StringBuilder("§x");
        for (int i = 0; i < hex.length(); i++) code.append('§').append(hex.charAt(i));
        return code.toString();
    }

    private String solidText(String value, int rgb) {
        String code = hexCode(rgb);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) out.append(code).append(value.charAt(i));
        return out.toString();
    }

    private String gradientText(String value, int from, int to) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            double t = value.length() <= 1 ? 0 : (double) i / (value.length() - 1);
            int rgb = mix(from, to, t);
            out.append(hexCode(rgb)).append(value.charAt(i));
        }
        return out.toString();
    }

    private int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private boolean setSolid(Player player, String id, int rgb) {
        store.cfg.set("clans." + id + ".color.type", "solid");
        store.cfg.set("clans." + id + ".color.solid", String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF));
        changed();
        player.sendMessage(PREFIX + "§aОднотонный цвет клана изменён: " + solidText("[" + name(id) + "]", rgb));
        return true;
    }

    private boolean setGradient(Player player, String id, int from, int to, String gradientName) {
        store.cfg.set("clans." + id + ".color.type", "gradient");
        store.cfg.set("clans." + id + ".color.fromHex", String.format(Locale.ROOT, "%06X", from & 0xFFFFFF));
        store.cfg.set("clans." + id + ".color.toHex", String.format(Locale.ROOT, "%06X", to & 0xFFFFFF));
        changed();
        player.sendMessage(PREFIX + "§aГрадиент клана изменён: " + gradientText("[" + name(id) + "]", from, to)
                + " §8(" + gradientName + ")");
        return true;
    }

    private boolean canChangeColor(Player player, String id) {
        if (id.isEmpty()) {
            player.sendMessage(PREFIX + "§cТы не состоишь в клане.");
            return false;
        }
        if (!role(id, player.getUniqueId()).equals("лидер")) {
            player.sendMessage(PREFIX + "§cМенять оформление клана может только лидер.");
            return false;
        }
        return true;
    }

    private void colorMenu(Player player) {
        String id = clanId(player.getUniqueId());
        if (!canChangeColor(player, id)) return;
        FormWindowSimple form = new FormWindowSimple("§l§0ОФОРМЛЕНИЕ КЛАНА",
                "§7Сначала выбери однотонный цвет или готовый RGB-градиент.\n§8Изменение сразу появится в чате и над участниками.");
        form.addButton(new ElementButton("§l§fОДНОТОННЫЕ ЦВЕТА\n§8Выбрать один цвет"));
        form.addButton(new ElementButton("§l§bГОТОВЫЕ ГРАДИЕНТЫ\n§8Два плавно переходящих цвета"));
        form.addButton(new ElementButton("§7СБРОСИТЬ ОФОРМЛЕНИЕ\n§8Вернуть голубой цвет"));
        form.addButton(new ElementButton("§cНАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed() || !player.isOnline()) return;
            int choice = form.getResponse().getClickedButtonId();
            if (choice == 0) solidColorMenu(player, id);
            else if (choice == 1) gradientMenu(player, id);
            else if (choice == 2) setSolid(player, id, 0x38C9FF);
            else if (choice == 3) menu(player);
        }));
        player.showFormWindow(form);
    }

    private void solidColorMenu(Player player, String id) {
        FormWindowSimple form = new FormWindowSimple("§l§0ОДНОТОННЫЙ ЦВЕТ", "§7Выбери цвет названия своего клана:");
        for (int i = 0; i < SOLID_NAMES.length; i++) {
            form.addButton(new ElementButton(hexCode(SOLID_RGB[i]) + "§l" + SOLID_NAMES[i]));
        }
        form.addButton(new ElementButton("§cНАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed() || !player.isOnline()) return;
            int choice = form.getResponse().getClickedButtonId();
            if (choice >= 0 && choice < SOLID_RGB.length) setSolid(player, id, SOLID_RGB[choice]);
            else if (choice == SOLID_RGB.length) colorMenu(player);
        }));
        player.showFormWindow(form);
    }

    private void gradientMenu(Player player, String id) {
        FormWindowSimple form = new FormWindowSimple("§l§0ГОТОВЫЕ ГРАДИЕНТЫ", "§7Выбери стиль оформления клана:");
        for (int i = 0; i < GRADIENT_NAMES.length; i++) {
            String preview = gradientText(GRADIENT_NAMES[i].toUpperCase(Locale.ROOT), GRADIENT_RGB[i][0], GRADIENT_RGB[i][1]);
            form.addButton(new ElementButton(preview + "\n§8RGB-градиент"));
        }
        form.addButton(new ElementButton("§cНАЗАД"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed() || !player.isOnline()) return;
            int choice = form.getResponse().getClickedButtonId();
            if (choice >= 0 && choice < GRADIENT_NAMES.length) {
                setGradient(player, id, GRADIENT_RGB[choice][0], GRADIENT_RGB[choice][1], GRADIENT_NAMES[choice]);
            } else if (choice == GRADIENT_NAMES.length) colorMenu(player);
        }));
        player.showFormWindow(form);
    }

    private String role(String id, UUID player) {
        if (store.cfg.getString("clans." + id + ".leader", "").equals(player.toString())) return "лидер";
        if (store.cfg.getStringList("clans." + id + ".officers").contains(player.toString())) return "заместитель";
        return "участник";
    }

    private String name(String id) { return store.cfg.getString("clans." + id + ".name", id); }

    private void changed() {
        store.touch();
        store.flush();
        for (Player online : plugin.getServer().getOnlinePlayers().values()) plugin.social().refreshDisplay(online);
    }

    boolean handle(CommandSender sender, String command, String[] args) {
        if (!command.equals("clan") && !command.equals("cc")) return false;
        if (command.equals("cc")) {
            clanChat(sender, args);
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) menu(player);
            else help(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> create(sender, args);
            case "invite" -> invite(sender, args);
            case "accept" -> accept(sender, args);
            case "deny" -> deny(sender);
            case "leave" -> leave(sender);
            case "disband" -> disband(sender);
            case "kick" -> kick(sender, args);
            case "promote" -> promote(sender, args);
            case "demote" -> demote(sender, args);
            case "transfer" -> transfer(sender, args);
            case "info" -> info(sender);
            case "color" -> clanColor(sender, args);
            case "gradient" -> clanGradient(sender, args);
            case "list" -> list(sender);
            case "help" -> help(sender);
            default -> help(sender);
        }
        return true;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage(PREFIX + "§cКоманда доступна только игроку.");
        return null;
    }

    private void create(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        if (args.length < 2) { p.sendMessage(PREFIX + "§e/clan create <название>"); return; }
        if (!clanId(p.getUniqueId()).isEmpty()) { p.sendMessage(PREFIX + "§cТы уже состоишь в клане."); return; }
        String clanName = NEXORASurvival.clean(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        if (clanName.length() < 3 || clanName.length() > 16 || !clanName.matches("[\\p{L}\\p{N}_ -]+")) {
            p.sendMessage(PREFIX + "§cНазвание: 3–16 символов, буквы, цифры, пробел, _ или -.");
            return;
        }
        String id = clanName.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (store.cfg.exists("clans." + id)) { p.sendMessage(PREFIX + "§cТакое название уже занято."); return; }
        store.cfg.set("clans." + id + ".name", clanName);
        store.cfg.set("clans." + id + ".leader", p.getUniqueId().toString());
        store.cfg.set("clans." + id + ".members", new ArrayList<String>());
        store.cfg.set("clans." + id + ".officers", new ArrayList<String>());
        changed();
        p.sendMessage(PREFIX + "§aКлан §b" + clanName + " §aсоздан! Ты его лидер.");
        colorMenu(p);
    }

    private void invite(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty()) { p.sendMessage(PREFIX + "§cСначала вступи или создай клан."); return; }
        if (role(id, p.getUniqueId()).equals("участник")) { p.sendMessage(PREFIX + "§cПриглашать могут лидер и заместители."); return; }
        if (args.length < 2) { p.sendMessage(PREFIX + "§e/clan invite <игрок>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) { p.sendMessage(PREFIX + "§cИгрок должен быть онлайн."); return; }
        if (!clanId(target.getUniqueId()).isEmpty()) { p.sendMessage(PREFIX + "§cИгрок уже состоит в клане."); return; }
        if (store.cfg.getStringList("clans." + id + ".members").size() + 1 >= MAX_MEMBERS) {
            p.sendMessage(PREFIX + "§cВ клане максимум " + MAX_MEMBERS + " игроков."); return;
        }
        store.cfg.set("invites." + target.getUniqueId() + "." + id, System.currentTimeMillis());
        changed();
        target.sendMessage(PREFIX + "§b" + p.getName() + " §7приглашает тебя в клан §f" + name(id) + "§7. Введи §e/clan accept");
        p.sendMessage(PREFIX + "§aПриглашение отправлено игроку §f" + target.getName() + "§a.");
    }

    private String pendingInvite(UUID player) {
        ConfigSection invites = store.cfg.getSection("invites." + player);
        long now = System.currentTimeMillis();
        for (String id : invites.getKeys(false)) {
            long sent = store.cfg.getLong("invites." + player + "." + id, 0L);
            if (now - sent <= INVITE_TTL && store.cfg.exists("clans." + id + ".leader")) return id;
            store.cfg.remove("invites." + player + "." + id);
            store.touch();
        }
        return "";
    }

    private void accept(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        if (!clanId(p.getUniqueId()).isEmpty()) { p.sendMessage(PREFIX + "§cТы уже состоишь в клане."); return; }
        String id = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : pendingInvite(p.getUniqueId());
        if (id.isEmpty() || !store.cfg.exists("clans." + id + ".leader")
                || System.currentTimeMillis() - store.cfg.getLong("invites." + p.getUniqueId() + "." + id, 0L) > INVITE_TTL) {
            p.sendMessage(PREFIX + "§cНет действующего приглашения. Попроси лидера пригласить тебя снова."); return;
        }
        List<String> members = new ArrayList<>(store.cfg.getStringList("clans." + id + ".members"));
        if (members.size() + 1 >= MAX_MEMBERS) { p.sendMessage(PREFIX + "§cКлан уже заполнен."); return; }
        members.add(p.getUniqueId().toString());
        store.cfg.set("clans." + id + ".members", members);
        store.cfg.remove("invites." + p.getUniqueId());
        changed();
        p.sendMessage(PREFIX + "§aТы вступил в клан §b" + name(id) + "§a.");
        broadcast(id, "§b" + p.getName() + " §7вступил в клан.");
    }

    private void deny(CommandSender sender) {
        Player p = player(sender); if (p == null) return;
        store.cfg.remove("invites." + p.getUniqueId());
        changed();
        p.sendMessage(PREFIX + "§7Приглашения отклонены.");
    }

    private void leave(CommandSender sender) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty()) { p.sendMessage(PREFIX + "§cТы не состоишь в клане."); return; }
        if (role(id, p.getUniqueId()).equals("лидер")) {
            p.sendMessage(PREFIX + "§cЛидер не может выйти. Передай лидерство: §e/clan transfer <игрок> §7или распусти клан: §e/clan disband"); return;
        }
        removeMember(id, p.getUniqueId());
        changed();
        p.sendMessage(PREFIX + "§7Ты покинул клан §f" + name(id) + "§7.");
        broadcast(id, "§f" + p.getName() + " §7покинул клан.");
    }

    private void disband(CommandSender sender) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty() || !role(id, p.getUniqueId()).equals("лидер")) { p.sendMessage(PREFIX + "§cРаспустить клан может только лидер."); return; }
        String clanName = name(id);
        broadcast(id, "§cКлан " + clanName + " распущен лидером.");
        store.cfg.remove("clans." + id);
        changed();
        p.sendMessage(PREFIX + "§cКлан распущен.");
    }

    private void kick(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty() || role(id, p.getUniqueId()).equals("участник")) { p.sendMessage(PREFIX + "§cИсключать могут лидер и заместители."); return; }
        if (args.length < 2) { p.sendMessage(PREFIX + "§e/clan kick <игрок>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null || !clanId(target.getUniqueId()).equals(id)) { p.sendMessage(PREFIX + "§cИгрок не найден в твоём клане или не в сети."); return; }
        if (role(id, target.getUniqueId()).equals("лидер")
                || (role(id, p.getUniqueId()).equals("заместитель") && role(id, target.getUniqueId()).equals("заместитель"))) {
            p.sendMessage(PREFIX + "§cУ тебя недостаточно прав для исключения этого игрока."); return;
        }
        removeMember(id, target.getUniqueId());
        changed();
        target.sendMessage(PREFIX + "§cТебя исключили из клана §f" + name(id) + "§c.");
        p.sendMessage(PREFIX + "§aИгрок исключён из клана.");
        broadcast(id, "§f" + target.getName() + " §7больше не состоит в клане.");
    }

    private void promote(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty() || !role(id, p.getUniqueId()).equals("лидер")) { p.sendMessage(PREFIX + "§cНазначать заместителей может только лидер."); return; }
        if (args.length < 2) { p.sendMessage(PREFIX + "§e/clan promote <игрок>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null || !clanId(target.getUniqueId()).equals(id) || role(id, target.getUniqueId()).equals("лидер")) {
            p.sendMessage(PREFIX + "§cУкажи участника своего клана, который онлайн."); return;
        }
        List<String> officers = new ArrayList<>(store.cfg.getStringList("clans." + id + ".officers"));
        if (!officers.contains(target.getUniqueId().toString())) officers.add(target.getUniqueId().toString());
        store.cfg.set("clans." + id + ".officers", officers);
        changed();
        broadcast(id, "§b" + target.getName() + " §7назначен заместителем.");
    }

    private void demote(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty() || !role(id, p.getUniqueId()).equals("лидер")) { p.sendMessage(PREFIX + "§cСнимать заместителей может только лидер."); return; }
        if (args.length < 2) { p.sendMessage(PREFIX + "§e/clan demote <игрок>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null || !clanId(target.getUniqueId()).equals(id)) { p.sendMessage(PREFIX + "§cУкажи заместителя своего клана, который онлайн."); return; }
        List<String> officers = new ArrayList<>(store.cfg.getStringList("clans." + id + ".officers"));
        if (!officers.remove(target.getUniqueId().toString())) { p.sendMessage(PREFIX + "§cЭтот игрок не заместитель."); return; }
        store.cfg.set("clans." + id + ".officers", officers);
        changed();
        broadcast(id, "§f" + target.getName() + " §7больше не заместитель.");
    }

    private void transfer(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty() || !role(id, p.getUniqueId()).equals("лидер")) { p.sendMessage(PREFIX + "§cПередать лидерство может только лидер."); return; }
        if (args.length < 2) { p.sendMessage(PREFIX + "§e/clan transfer <игрок>"); return; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null || !clanId(target.getUniqueId()).equals(id)) { p.sendMessage(PREFIX + "§cНужен участник твоего клана, который онлайн."); return; }
        List<String> members = new ArrayList<>(store.cfg.getStringList("clans." + id + ".members"));
        members.remove(target.getUniqueId().toString());
        members.add(p.getUniqueId().toString());
        store.cfg.set("clans." + id + ".members", members);
        store.cfg.set("clans." + id + ".leader", target.getUniqueId().toString());
        List<String> officers = new ArrayList<>(store.cfg.getStringList("clans." + id + ".officers"));
        officers.remove(target.getUniqueId().toString());
        officers.remove(p.getUniqueId().toString());
        store.cfg.set("clans." + id + ".officers", officers);
        changed();
        broadcast(id, "§b" + target.getName() + " §7теперь лидер клана.");
    }

    private void removeMember(String id, UUID uuid) {
        List<String> members = new ArrayList<>(store.cfg.getStringList("clans." + id + ".members"));
        members.remove(uuid.toString());
        store.cfg.set("clans." + id + ".members", members);
        List<String> officers = new ArrayList<>(store.cfg.getStringList("clans." + id + ".officers"));
        officers.remove(uuid.toString());
        store.cfg.set("clans." + id + ".officers", officers);
    }

    private void clanColor(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (!canChangeColor(p, id)) return;
        if (args.length == 1) { colorMenu(p); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("reset")) {
            setSolid(p, id, 0x38C9FF);
            return;
        }
        p.sendMessage(PREFIX + "§e/clan color §7— открыть меню оформления");
        p.sendMessage(PREFIX + "§e/clan color reset §7— сбросить оформление");
    }

    private void clanGradient(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (!canChangeColor(p, id)) return;
        if (args.length != 2) {
            p.sendMessage(PREFIX + "§e/clan gradient <название>");
            p.sendMessage(PREFIX + "§7Готовые варианты: sunset, ocean, neon, fire, forest, royal, gold, ice, cherry, cotton, toxic, aqua");
            return;
        }
        for (int i = 0; i < GRADIENT_IDS.length; i++) {
            if (GRADIENT_IDS[i].equalsIgnoreCase(args[1]) || GRADIENT_NAMES[i].equalsIgnoreCase(args[1])) {
                setGradient(p, id, GRADIENT_RGB[i][0], GRADIENT_RGB[i][1], GRADIENT_NAMES[i]);
                return;
            }
        }
        p.sendMessage(PREFIX + "§cТакого градиента нет. Открой /clan color.");
    }

    private void info(CommandSender sender) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty()) { p.sendMessage(PREFIX + "§7Ты не состоишь в клане."); return; }
        String leader = store.cfg.getString("clans." + id + ".leader", "");
        String leaderName = onlineName(leader);
        List<String> members = store.cfg.getStringList("clans." + id + ".members");
        p.sendMessage("§8§m------------------------");
        p.sendMessage("§b§lКЛАН §f" + name(id));
        p.sendMessage("§7Лидер: §f" + leaderName);
        p.sendMessage("§7Участников: §f" + (members.size() + 1) + "§8/§f" + MAX_MEMBERS);
        p.sendMessage("§7Твоя роль: §b" + role(id, p.getUniqueId()));
        p.sendMessage("§8§m------------------------");
        p.sendMessage("§7Участники:");
        p.sendMessage("§b★ §f" + leaderName + " §8— лидер");
        for (String member : members) {
            String r = store.cfg.getStringList("clans." + id + ".officers").contains(member) ? "§b— заместитель" : "§7— участник";
            p.sendMessage("§8• §f" + onlineName(member) + " " + r);
        }
    }

    private String onlineName(String uuid) {
        try {
            Player p = plugin.getServer().getPlayer(UUID.fromString(uuid)).orElse(null);
            if (p != null) return p.getName();
        } catch (IllegalArgumentException ignored) { }
        return store.cfg.getString("names." + uuid, uuid.length() > 8 ? uuid.substring(0, 8) : uuid);
    }

    private void list(CommandSender sender) {
        ConfigSection all = store.cfg.getSection("clans");
        sender.sendMessage(PREFIX + "§bСписок кланов:");
        int count = 0;
        for (String id : all.getKeys(false)) {
            if (!store.cfg.exists("clans." + id + ".leader")) continue;
            int size = store.cfg.getStringList("clans." + id + ".members").size() + 1;
            sender.sendMessage("§8• §b" + name(id) + " §8— §f" + size + "§7/" + MAX_MEMBERS + " §8| лидер: §f" + onlineName(store.cfg.getString("clans." + id + ".leader", "")));
            count++;
        }
        if (count == 0) sender.sendMessage("§7Пока кланов нет.");
    }

    private void clanChat(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty()) { p.sendMessage(PREFIX + "§cТы не состоишь в клане."); return; }
        if (args.length == 0) { p.sendMessage(PREFIX + "§e/cc <сообщение>"); return; }
        String message = NEXORASurvival.clean(String.join(" ", args));
        if (message.isEmpty()) return;
        broadcast(id, tag(id) + " §f" + p.getName() + "§7: §f" + message);
    }

    private void broadcast(String id, String message) {
        String leader = store.cfg.getString("clans." + id + ".leader", "");
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            UUID uuid = online.getUniqueId();
            if (uuid.toString().equals(leader) || store.cfg.getStringList("clans." + id + ".members").contains(uuid.toString())) online.sendMessage(message);
        }
    }

    private void menu(Player p) {
        String id = clanId(p.getUniqueId());
        String body = id.isEmpty()
                ? "§7Создай клан или попроси приглашение от другого игрока.\n\n§8Клан может содержать до " + MAX_MEMBERS + " игроков."
                : "§7Клан: §b" + name(id) + "\n§7Роль: §f" + role(id, p.getUniqueId()) + "\n§7Участников: §f" + (store.cfg.getStringList("clans." + id + ".members").size() + 1) + "/" + MAX_MEMBERS + "\n\n§8Команды управления доступны через чат.";
        FormWindowSimple form = new FormWindowSimple("§l§0КЛАНЫ", body);
        form.addButton(new ElementButton("§bИНФОРМАЦИЯ\n§8Участники и роли"));
        form.addButton(new ElementButton("§aСПИСОК КЛАНОВ\n§8Все кланы"));
        form.addButton(new ElementButton("§eКОМАНДЫ\n§8Как управлять"));
        if (!id.isEmpty() && role(id, p.getUniqueId()).equals("лидер")) form.addButton(new ElementButton("§dОФОРМЛЕНИЕ КЛАНА\n§8Цвет и градиент"));
        form.addButton(new ElementButton("§cЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int choice = form.getResponse().getClickedButtonId();
            if (choice == 0) info(p);
            else if (choice == 1) list(p);
            else if (choice == 2) help(p);
            else if (choice == 3 && !id.isEmpty() && role(id, p.getUniqueId()).equals("лидер")) colorMenu(p);
        }));
        p.showFormWindow(form);
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§8§m------------------------");
        sender.sendMessage("§b§lКЛАНЫ §8— §7команды");
        sender.sendMessage("§e/clan §7— меню");
        sender.sendMessage("§e/clan create <название> §7— создать");
        sender.sendMessage("§e/clan invite <игрок> §7— пригласить");
        sender.sendMessage("§e/clan accept §7/ §e/clan deny §7— принять/отклонить");
        sender.sendMessage("§e/clan info §7/ §e/clan list §7— информация/список");
        sender.sendMessage("§e/clan color §7— меню цвета клана (лидер)");
        sender.sendMessage("§e/clan gradient <название> §7— готовый RGB-градиент (лидер)");
        sender.sendMessage("§e/clan kick <игрок> §7— исключить");
        sender.sendMessage("§e/clan promote/demote <игрок> §7— заместитель");
        sender.sendMessage("§e/clan transfer <игрок> §7— передать лидерство");
        sender.sendMessage("§e/clan leave §7/ §e/clan disband §7— выйти/распустить");
        sender.sendMessage("§e/cc <сообщение> §7— чат клана");
        sender.sendMessage("§8§m------------------------");
    }
}
