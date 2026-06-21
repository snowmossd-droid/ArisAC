package dev.aris.arisac.check.spoof;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.manager.ModDetectionManager;
import dev.aris.arisac.util.DiscordWebhook;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class AntiSpoofCheck {

    private static final Set<String> FABRIC_CORE = Set.of(
            "fabric-networking-api-v1:channel/v1",
            "fabric:registry/sync/id",
            "fabric:sync/tags",
            "fabric-registry-sync-v0:id_remap",
            "fabric-screen-handler-api-v1:open_screen"
    );

    private final ArisAC plugin;
    private final ModDetectionManager modDetection;

    public AntiSpoofCheck(ArisAC plugin, ModDetectionManager modDetection) {
        this.plugin = plugin;
        this.modDetection = modDetection;
    }

    public void onBrandReceived(Player player, SpoofData data, String brand) {
        if (!isEnabled()) return;
        data.brand = brand.trim();
        data.brandReceived = true;
        if (data.joinTime == 0) data.joinTime = System.currentTimeMillis();

        ModDetectionManager.ModEntry entry = modDetection.findByBrand(brand);
        if (entry != null) {
            addScore(data, 100, "BRAND_MATCH:" + entry.displayName());
        }
        evaluateScore(player, data);
    }

    public void onChannelRegister(Player player, SpoofData data, Set<String> channels) {
        if (!isEnabled()) return;
        data.registeredChannels.addAll(channels);
        data.channelsReceived = true;

        for (String ch : channels) {
            ModDetectionManager.ModEntry entry = modDetection.findByChannel(ch);
            if (entry != null) {
                addScore(data, 100, "CHANNEL_MATCH:" + entry.displayName() + "(" + ch + ")");
            }
        }
        evaluateScore(player, data);
    }

    public void onSwingPacket(SpoofData data) {
        data.lastSwingTime = System.currentTimeMillis();
    }

    public void onAttackPacket(Player player, SpoofData data) {
        long diff = System.currentTimeMillis() - data.lastSwingTime;
        if (diff > 200) {
            data.noSwingCount++;
            if (data.noSwingCount >= 4) {
                addScore(data, 20, "NO_SWING_ON_ATTACK x" + data.noSwingCount);
                evaluateScore(player, data);
            }
        } else {
            data.noSwingCount = Math.max(0, data.noSwingCount - 1);
        }
    }

    public void onKeepAliveResponse(Player player, SpoofData data, long responseMs) {
        data.keepAliveTimings.add(responseMs);
        if (data.keepAliveTimings.size() > 10) data.keepAliveTimings.poll();

        if (data.keepAliveTimings.size() >= 5) {
            double avg = data.keepAliveTimings.stream().mapToLong(l -> l).average().orElse(0);
            long var = (long) data.keepAliveTimings.stream()
                    .mapToLong(l -> (l - (long) avg) * (l - (long) avg))
                    .average().orElse(0);
            if (avg < 5 && var < 4) {
                addScore(data, 25, "INSTANT_KEEPALIVE avg=" + String.format("%.1f", avg) + "ms");
                evaluateScore(player, data);
            }
        }
    }

    public void onBrandAfterMove(Player player, SpoofData data) {
        if (data.brandReceived && data.firstMoveTime > 0
                && data.brandReceivedTime > data.firstMoveTime + 500) {
            addScore(data, 20, "BRAND_AFTER_MOVE delay="
                    + (data.brandReceivedTime - data.firstMoveTime) + "ms");
            evaluateScore(player, data);
        }
    }

    public void onFirstMove(SpoofData data) {
        if (data.firstMoveTime == 0) data.firstMoveTime = System.currentTimeMillis();
    }

    public void onJoinDelayCheck(Player player, SpoofData data) {
        if (!isEnabled() || data.punished) return;

        String brand = data.brand.toLowerCase();
        boolean isFabric = brand.equals("fabric") || brand.equals("quilt");
        boolean isVanilla = brand.equals("vanilla");

        if (isFabric && data.channelsReceived) {
            boolean hasFabricCh = data.registeredChannels.stream()
                    .anyMatch(c -> c.startsWith("fabric") || c.startsWith("fabric-"));
            if (!hasFabricCh) {
                addScore(data, 60, "FABRIC_BRAND_ZERO_FABRIC_CHANNELS");
            }
        }

        if (isFabric && !data.channelsReceived) {
            addScore(data, 50, "FABRIC_BRAND_NO_CHANNELS_AT_ALL");
        }

        if (isVanilla && data.channelsReceived) {
            boolean hasNonMc = data.registeredChannels.stream()
                    .anyMatch(c -> !c.startsWith("minecraft:"));
            if (hasNonMc) {
                addScore(data, 70, "VANILLA_BRAND_WITH_MOD_CHANNELS");
            }
        }

        sendDiscordReport(player, data);
        evaluateScore(player, data);
    }

    private void addScore(SpoofData data, int score, String reason) {
        data.suspicionScore += score;
        data.reasons.add(reason);
    }

    private void evaluateScore(Player player, SpoofData data) {
        if (data.punished) return;
        FileConfiguration cfg = plugin.getConfig();
        int kickThreshold = cfg.getInt("checks.antispoof.kick-score", 60);
        int alertThreshold = cfg.getInt("checks.antispoof.alert-score", 30);

        if (data.suspicionScore >= kickThreshold) {
            punish(player, data);
        } else if (data.suspicionScore >= alertThreshold) {
            alertStaff(player, data, false);
        }
    }

    private void punish(Player player, SpoofData data) {
        if (data.punished) return;
        data.punished = true;

        alertStaff(player, data, true);

        FileConfiguration cfg = plugin.getConfig();
        String kickMsg = cfg.getString("messages.spoof-kick",
                "&cBan bi kick: Phat hien mod khong cho phep.\n&7Lien he admin.")
                .replace("{mod}", String.join(", ", data.reasons))
                .replace("{reason}", String.join(", ", data.reasons));

        String action = cfg.getString("checks.antispoof.action", "KICK");

        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) return;
            player.kick(LegacyComponentSerializer.legacyAmpersand().deserialize(kickMsg));
            if ("BAN".equalsIgnoreCase(action)) {
                int days = cfg.getInt("checks.antispoof.ban-days", 7);
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                        player.getName(), "ArisAC AntiSpoof: " + String.join(", ", data.reasons),
                        java.util.Date.from(java.time.Instant.now().plusSeconds(days * 86400L)),
                        "ArisAC");
            }
            if (cfg.getBoolean("punishment.broadcast-on-kick", true)) {
                String bc = cfg.getString("messages.kick-broadcast",
                        "&8[ArisAC] &f{player} &abi kick vi &c{check}&a.")
                        .replace("{player}", player.getName())
                        .replace("{check}", "AntiSpoof");
                Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(bc));
            }
        }, null);
    }

    private void alertStaff(Player player, SpoofData data, boolean isPunished) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("alerts.enabled", true)) return;

        String status = isPunished ? "&c[KICKED]" : "&e[SUSPECT]";
        String msg = "&8[&cArisAC&8] " + status + " &f" + player.getName()
                + " &7| score=&c" + data.suspicionScore
                + " &7| brand=&f" + data.brand
                + " &7| channels=&f" + data.registeredChannels.size()
                + " &7| flags=&c" + String.join(", ", data.reasons);

        String perm = cfg.getString("alerts.permission", "arisac.alerts");
        var comp = LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(perm))
                .forEach(p -> p.sendMessage(comp));

        plugin.getLogger().warning("[AntiSpoof] " + player.getName()
                + " score=" + data.suspicionScore
                + " brand=" + data.brand
                + " channels=" + data.registeredChannels.size()
                + " flags=" + data.reasons);
    }

    public void sendDiscordReport(Player player, SpoofData data) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("discord.enabled", false)) return;

        String webhookUrl = cfg.getString("discord.webhook-url", "");
        if (webhookUrl.isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) return;

        DiscordWebhook webhook = new DiscordWebhook(webhookUrl);

        boolean isSuspect = data.suspicionScore >= cfg.getInt("checks.antispoof.alert-score", 30);
        boolean isKicked  = data.suspicionScore >= cfg.getInt("checks.antispoof.kick-score", 60);

        int color = isKicked ? 0xFF0000 : (isSuspect ? 0xFF8800 : 0x00FF88);
        String title = isKicked ? "🚨 Mod Detected - KICKED"
                     : (isSuspect ? "⚠️ Suspicious Player" : "📋 Player Join Report");

        String channelList = data.registeredChannels.isEmpty()
                ? "*Khong co channel nao*"
                : String.join("\n", data.registeredChannels.stream()
                        .limit(20).toList());

        String flagList = data.reasons.isEmpty()
                ? "*Khong co flag nao*"
                : String.join("\n", data.reasons);

        String avatar = "https://mc-heads.net/avatar/" + player.getName() + "/64";

        DiscordWebhook.Embed embed = new DiscordWebhook.Embed();
        embed.title = title;
        embed.description = "Player **" + player.getName() + "** vua tham gia server.";
        embed.color = color;
        embed.thumbnailUrl = avatar;
        embed.footer = "ArisAC | " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        embed.addField("👤 Player", player.getName(), true);
        embed.addField("🏷️ Brand", "`" + data.brand + "`", true);
        embed.addField("🔌 Channels (" + data.registeredChannels.size() + ")",
                "```\n" + (channelList.length() > 900
                        ? channelList.substring(0, 900) + "\n..." : channelList) + "\n```", false);
        if (!data.reasons.isEmpty()) {
            embed.addField("🚩 Flags (score=" + data.suspicionScore + ")",
                    "```\n" + flagList + "\n```", false);
        }
        embed.addField("📡 Ping", player.getPing() + "ms", true);
        embed.addField("🌍 IP", player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "unknown", true);

        webhook.sendEmbed(embed);
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("checks.antispoof.enabled", true);
    }

    public static class SpoofData {
        public String brand = "unknown";
        public boolean brandReceived = false;
        public boolean channelsReceived = false;
        public boolean punished = false;
        public String detectedMod = null;
        public long joinTime = 0;
        public long firstMoveTime = 0;
        public long brandReceivedTime = 0;
        public long lastSwingTime = 0;
        public int noSwingCount = 0;
        public int suspicionScore = 0;
        public final Set<String> registeredChannels = new LinkedHashSet<>();
        public final List<String> reasons = new ArrayList<>();
        public final Deque<Long> keepAliveTimings = new ArrayDeque<>();
    }
}
