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

    private static final String[] COLOR_CODES = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
    private static final int[] COLOR_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    String tag(Player player) {
        String id = clanId(player.getUniqueId());
        return id.isEmpty() ? "" : tag(id);
    }

    private String tag(String id) {
        String clanName = store.cfg.getString("clans." + id + ".name", id);
        String from = store.cfg.getString("clans." + id + ".color.from", "b");
        String to = store.cfg.getString("clans." + id + ".color.to", from);
        return gradientText("[" + clanName + "]", from, to) + "§r";
    }

    private String gradientText(String value, String from, String to) {
        int a = colorIndex(from), b = colorIndex(to);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            double t = value.length() <= 1 ? 0 : (double) i / (value.length() - 1);
            int rgb = mix(COLOR_RGB[a], COLOR_RGB[b], t);
            out.append('§').append(COLOR_CODES[nearestColor(rgb)]).append(value.charAt(i));
        }
        return out.toString();
    }

    private int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private int nearestColor(int rgb) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < COLOR_RGB.length; i++) {
            int c = COLOR_RGB[i];
            long dr = ((rgb >> 16) & 255) - ((c >> 16) & 255);
            long dg = ((rgb >> 8) & 255) - ((c >> 8) & 255);
            long db = (rgb & 255) - (c & 255);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }

    private int colorIndex(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String[] names = {"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray",
                "dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
        String[] ru = {"чёрный","черный","тёмно-синий","темно-синий","тёмно-зелёный","темно-зелёный",
                "бирюзовый","тёмно-красный","темно-красный","фиолетовый","золотой","серый",
                "тёмно-серый","темно-серый","синий","зелёный","зеленый","голубой","красный",
                "розовый","жёлтый","желтый","белый"};
        String[] codes = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
        for (int i = 0; i < codes.length; i++) if (v.equals(codes[i]) || v.equals(names[i])) return i;
        String[] aliases = {"black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray",
                "dark_gray","blue","green","aqua","red","light_purple","yellow","white"};
        for (int i = 0; i < aliases.length; i++) if (v.equals(aliases[i])) return i;
        for (int i = 0; i < ru.length; i++) if (v.equals(ru[i])) {
            int[] map = {0,0,1,1,2,2,3,4,4,5,6,7,8,8,9,10,10,11,12,13,14,14,15};
            return map[i];
        }
        return -1;
    }

    private boolean setColors(Player player, String id, String from, String to) {
        int a = colorIndex(from), b = colorIndex(to);
        if (a < 0 || b < 0) {
            player.sendMessage(PREFIX + "§cНеизвестный цвет. Используй код 0–9 или a–f, либо название цвета.");
            return false;
        }
        store.cfg.set("clans." + id + ".color.from", COLOR_CODES[a]);
        store.cfg.set("clans." + id + ".color.to", COLOR_CODES[b]);
        changed();
        player.sendMessage(PREFIX + "§aЦвет клана изменён: " + gradientText("ПРИМЕР", COLOR_CODES[a], COLOR_CODES[b]));
        return true;
    }

    private void colorMenu(Player player) {
        String id = clanId(player.getUniqueId());
        if (id.isEmpty()) { player.sendMessage(PREFIX + "§cТы не состоишь в клане."); return; }
        if (!role(id, player.getUniqueId()).equals("лидер")) {
            player.sendMessage(PREFIX + "§cМенять оформление клана может только лидер.");
            return;
        }
        FormWindowSimple form = new FormWindowSimple("§l§0ЦВЕТ КЛАНА", "§7Выбери однотонный цвет или градиент.\n§8Настройка будет видна всем игрокам.");
        String[] labels = {"§0Чёрный","§1Тёмно-синий","§2Тёмно-зелёный","§3Бирюзовый","§4Тёмно-красный","§5Фиолетовый",
                "§6Золотой","§7Серый","§8Тёмно-серый","§9Синий","§aЗелёный","§bГолубой","§cКрасный","§dРозовый","§eЖёлтый","§fБелый",
                "§c§lГрадиент: Красный → Золотой","§b§lГрадиент: Голубой → Фиолетовый","§a§lГрадиент: Зелёный → Жёлтый","§d§lГрадиент: Розовый → Голубой",
                "§7Сбросить цвет"};
        for (String label : labels) form.addButton(new ElementButton(label));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int choice = form.getResponse().getClickedButtonId();
            if (choice < 0 || choice >= labels.length || !player.isOnline()) return;
            if (choice < 16) setColors(player, id, COLOR_CODES[choice], COLOR_CODES[choice]);
            else if (choice == 16) setColors(player, id, "c", "6");
            else if (choice == 17) setColors(player, id, "b", "5");
            else if (choice == 18) setColors(player, id, "a", "e");
            else if (choice == 19) setColors(player, id, "d", "b");
            else setColors(player, id, "b", "b");
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
        if (id.isEmpty()) { p.sendMessage(PREFIX + "§cТы не состоишь в клане."); return; }
        if (args.length == 1) { colorMenu(p); return; }
        if (!role(id, p.getUniqueId()).equals("лидер")) { p.sendMessage(PREFIX + "§cМенять цвет может только лидер клана."); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("reset")) { setColors(p, id, "b", "b"); return; }
        if (args.length != 2) { p.sendMessage(PREFIX + "§e/clan color <цвет> §7или §e/clan color reset"); return; }
        setColors(p, id, args[1], args[1]);
    }

    private void clanGradient(CommandSender sender, String[] args) {
        Player p = player(sender); if (p == null) return;
        String id = clanId(p.getUniqueId());
        if (id.isEmpty()) { p.sendMessage(PREFIX + "§cТы не состоишь в клане."); return; }
        if (!role(id, p.getUniqueId()).equals("лидер")) { p.sendMessage(PREFIX + "§cМенять оформление может только лидер клана."); return; }
        if (args.length != 3) {
            p.sendMessage(PREFIX + "§e/clan gradient <цвет1> <цвет2>");
            p.sendMessage(PREFIX + "§7Пример: §e/clan gradient red blue");
            return;
        }
        setColors(p, id, args[1], args[2]);
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
        form.addButton(new ElementButton("§cЗАКРЫТЬ"));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int choice = form.getResponse().getClickedButtonId();
            if (choice == 0) info(p);
            else if (choice == 1) list(p);
            else if (choice == 2) help(p);
            else if (choice == 4) colorMenu(p);
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
        sender.sendMessage("§e/clan gradient <цвет1> <цвет2> §7— градиент (лидер)");
        sender.sendMessage("§e/clan kick <игрок> §7— исключить");
        sender.sendMessage("§e/clan promote/demote <игрок> §7— заместитель");
        sender.sendMessage("§e/clan transfer <игрок> §7— передать лидерство");
        sender.sendMessage("§e/clan leave §7/ §e/clan disband §7— выйти/распустить");
        sender.sendMessage("§e/cc <сообщение> §7— чат клана");
        sender.sendMessage("§8§m------------------------");
    }
}
