package dev.aris.arisac.manager;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.check.CheckType;
import dev.aris.arisac.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class PunishmentManager {

    private final ArisAC plugin;

    public PunishmentManager(ArisAC plugin) { this.plugin = plugin; }

    public void handleViolation(Player player, PlayerData data, CheckType check, double vl) {
        FileConfiguration cfg = plugin.getConfig();
        String configKey = check.getConfigKey();

        if (!cfg.getBoolean("checks." + configKey + ".enabled", true)) return;

        double threshold = cfg.getDouble("checks." + configKey + ".vl-threshold", 10.0);
        if (vl < threshold) return;

        // Reset VL sau mỗi lần flag để tránh spam punishment
        data.resetVL(check.getDisplayName());
        data.recordCheckFlag(check.getDisplayName());

        int flags = data.incrementFlagCount();
        alertStaff(player, check, vl, flags);

        String action1 = cfg.getString("punishment.flag1.action", "KICK");
        String action2 = cfg.getString("punishment.flag2.action", "BAN");

        if (flags == 1) {
            executePunishment(player, data, check, action1, cfg);
        } else if (flags >= 2) {
            executePunishment(player, data, check, action2, cfg);
        }
    }

    private void executePunishment(Player player, PlayerData data, CheckType check,
                                   String action, FileConfiguration cfg) {
        switch (action.toUpperCase()) {
            case "KICK" -> scheduleKick(player, check, cfg);
            case "BAN"  -> scheduleBan(player, data, check, cfg);
            default     -> {}
        }
    }

    private void scheduleKick(Player player, CheckType check, FileConfiguration cfg) {
        player.getScheduler().run(plugin, task -> {
            String msg = format(cfg.getString("messages.kick-screen",
                    "&cBi kick vi gian lan!\n&7Check: {check}"), check, 0, null);
            player.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(msg));

            if (cfg.getBoolean("punishment.broadcast-on-kick", true)) {
                String broadcast = format(cfg.getString("messages.kick-broadcast",
                        "&c{player} bi kick vi {check}."), check, 0, player.getName());
                Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(broadcast));
            }
        }, null);
    }

    private void scheduleBan(Player player, PlayerData data, CheckType check, FileConfiguration cfg) {
        int days = cfg.getInt("punishment.flag2.duration-days", 30);
        String expiry = LocalDateTime.now().plusDays(days)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        player.getScheduler().run(plugin, task -> {
            String msg = format(cfg.getString("messages.ban-screen",
                    "&cBi ban {duration} ngay!\n&7Check: {check}\n&7Het han: {expiry}"),
                    check, days, expiry);
            player.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(msg));

            // Dùng ban-list của Paper
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                    .addBan(player.getName(),
                            "ArisAC: " + check.getDisplayName(),
                            java.util.Date.from(java.time.Instant.now()
                                    .plusSeconds(TimeUnit.DAYS.toSeconds(days))),
                            "ArisAC");

            if (cfg.getBoolean("punishment.broadcast-on-ban", true)) {
                String broadcast = format(cfg.getString("messages.ban-broadcast",
                        "&c{player} bi ban {duration} ngay vi {check}."),
                        check, days, player.getName());
                Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(broadcast));
            }

            data.resetFlags();
        }, null);
    }

    public void alertStaff(Player player, CheckType check, double vl, int flags) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("alerts.enabled", true)) return;

        String template = cfg.getString("messages.alert",
                "&8[ArisAC] &f{player} &7flagged &c{check} &8| VL: &f{vl} &8| Ping: &f{ping}ms");

        String msg = template
                .replace("{player}", player.getName())
                .replace("{check}", check.getDisplayName())
                .replace("{vl}", String.format("%.1f", vl))
                .replace("{ping}", String.valueOf(player.getPing()))
                .replace("{flags}", String.valueOf(flags));

        String perm = cfg.getString("alerts.permission", "arisac.alerts");
        net.kyori.adventure.text.Component component = net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacyAmpersand().deserialize(msg);

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(perm))
                .forEach(p -> p.sendMessage(component));

        if (cfg.getBoolean("alerts.console-log", true)) {
            plugin.getLogger().info("[FLAG] " + player.getName() + " | " + check.getDisplayName()
                    + " | VL=" + String.format("%.1f", vl) + " | flags=" + flags
                    + " | ping=" + player.getPing());
        }
    }

    private String format(String s, CheckType check, int days, String extra) {
        return s.replace("{check}", check.getDisplayName())
                .replace("{duration}", String.valueOf(days))
                .replace("{expiry}", extra != null ? extra : "")
                .replace("{player}", extra != null ? extra : "");
    }

    public void checkFlagExpiry(Player player, PlayerData data) {
        FileConfiguration cfg = plugin.getConfig();
        int resetDays = cfg.getInt("punishment.flag-reset-days", 7);
        long elapsed = System.currentTimeMillis() - data.getLastFlagReset();
        if (elapsed > TimeUnit.DAYS.toMillis(resetDays)) {
            data.resetFlags();
        }
    }
}
