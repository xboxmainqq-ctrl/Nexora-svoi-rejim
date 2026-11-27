package ru.nexora.survival;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.Listener;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.plugin.PluginBase;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class NEXORASurvival extends PluginBase {
    static final String PREFIX = "§6✦ NEXORA §8» ";
    private static final long MAX_AMOUNT = 1_000_000_000L;

    private EconomyModule economy;
    private TeleportModule teleports;
    private SocialModule social;
    private GearModule gear;
    private ClanModule clans;
    private ReportModule reports;
    private ModerationModule moderation;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().error("Не удалось создать папку NEXORA_Survival.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        economy = new EconomyModule(this);
        teleports = new TeleportModule(this);
        social = new SocialModule(this);
        gear = new GearModule(this);
        clans = new ClanModule(this);
        reports = new ReportModule(this);
        moderation = new ModerationModule(this);
        for (Listener listener : new Listener[]{economy, teleports, social, gear, clans, moderation}) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        // Data files are written once a minute and on shutdown, not after every change.
        getServer().getScheduler().scheduleRepeatingTask(this, this::flushAll, 20 * 60);
        getLogger().info("NEXORA_Survival включён: экономика, телепорты, кланы, ранги, киты и арсенал.");
    }

    @Override
    public void onDisable() {
        flushAll();
    }

    private void flushAll() {
        if (economy != null) economy.flush();
        if (teleports != null) teleports.flush();
        if (social != null) social.flush();
        if (clans != null) clans.flush();
        if (reports != null) reports.flush();
        if (moderation != null) moderation.flush();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return economy.handle(sender, name, args)
                || teleports.handle(sender, name, args)
                || social.handle(sender, name, args)
                || clans.handle(sender, name, args)
                || reports.handle(sender, name, args)
                || moderation.handle(sender, name, args)
                || gear.handle(sender, name, args);
    }

    /** Public economy bridge used by optional companion plugins such as NEXORA_Seller. */
    public long getBalance(java.util.UUID id) {
        return economy.balance(id);
    }

    /** Credits a player's shared Survival balance. */
    public void deposit(java.util.UUID id, long amount) {
        economy.deposit(id, amount);
    }

    EconomyModule economy() { return economy; }
    TeleportModule teleports() { return teleports; }
    SocialModule social() { return social; }
    ClanModule clans() { return clans; }

    static boolean isAdmin(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("nexora.survival.admin");
    }

    /** Removes colour codes and control characters from text typed by a player. */
    static String clean(String text) {
        if (text == null) return "";
        return text.replace("§", "").replaceAll("\\p{Cntrl}", "").trim();
    }

    /** Parses a whole number of coins; returns -1 if it is not a valid amount. */
    static long parseAmount(String text, boolean allowZero) {
        try {
            long value = Long.parseLong(text.trim());
            if (value < 0 || value > MAX_AMOUNT || (value == 0 && !allowZero)) return -1;
            return value;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Shows a touch-friendly list; onPick gets the index of the pressed button. */
    static void choose(Player player, String title, String body, List<String> labels, IntConsumer onPick) {
        FormWindowSimple form = new FormWindowSimple(title, body);
        for (String label : labels) form.addButton(new ElementButton(label));
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) return;
            int index = form.getResponse().getClickedButtonId();
            if (index >= 0 && index < labels.size()) onPick.accept(index);
        }));
        player.showFormWindow(form);
    }
}
