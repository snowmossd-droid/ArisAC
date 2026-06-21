package dev.aris.arisac.check.combat;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.check.CheckType;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.manager.PunishmentManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class AutoTotemCheck {

    private final ArisAC plugin;
    private final PunishmentManager punishment;

    public AutoTotemCheck(ArisAC plugin, PunishmentManager punishment) {
        this.plugin = plugin;
        this.punishment = punishment;
    }

    // Gọi khi nhận ClickContainer packet
    public void onClickContainer(Player player, PlayerData data, int slot, String actionType) {
        if (!plugin.getConfig().getBoolean("checks.autototem.enabled", true)) return;

        long windowMs = plugin.getConfig().getLong("checks.autototem.sequence-window-ms", 60);
        data.recentInventoryPackets.addLast(
            new PlayerData.TimedPacket(System.currentTimeMillis(), "CLICK:" + actionType, slot));
        data.pruneInventoryPackets(windowMs);
        evaluate(player, data, windowMs);
    }

    // Gọi khi nhận SwapOffhand / PlayerAction SWAP
    public void onSwapOffhand(Player player, PlayerData data) {
        if (!plugin.getConfig().getBoolean("checks.autototem.enabled", true)) return;

        long windowMs = plugin.getConfig().getLong("checks.autototem.sequence-window-ms", 60);
        data.recentInventoryPackets.addLast(
            new PlayerData.TimedPacket(System.currentTimeMillis(), "SWAP_OFFHAND", -1));
        data.pruneInventoryPackets(windowMs);
        evaluate(player, data, windowMs);
    }

    // Gọi khi nhận HeldItemChange packet
    public void onHeldItemChange(Player player, PlayerData data, int slot) {
        if (!plugin.getConfig().getBoolean("checks.autototem.enabled", true)) return;

        long windowMs = plugin.getConfig().getLong("checks.autototem.sequence-window-ms", 60);
        data.recentInventoryPackets.addLast(
            new PlayerData.TimedPacket(System.currentTimeMillis(), "HELD_CHANGE", slot));
        data.pruneInventoryPackets(windowMs);
    }

    private void evaluate(Player player, PlayerData data, long windowMs) {
        // Chỉ check khi offhand hiện tại là totem (kết quả của swap)
        if (player.getInventory().getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING) return;

        long clickCount = data.recentInventoryPackets.stream()
            .filter(p -> p.type().startsWith("CLICK")).count();
        long swapCount = data.recentInventoryPackets.stream()
            .filter(p -> p.type().equals("SWAP_OFFHAND")).count();
        long heldChanges = data.recentInventoryPackets.stream()
            .filter(p -> p.type().equals("HELD_CHANGE")).count();

        int minPackets = plugin.getConfig().getInt("checks.autototem.min-packets-in-window", 3);

        // Matrix mode: ≥2 click + 1 swap + 1 held change trong window
        boolean matrixPattern = clickCount >= 2 && swapCount >= 1 && heldChanges >= 1
                             && (clickCount + swapCount + heldChanges) >= minPackets;

        // NewVersion mode: click slot 40 với SWAP action (F-key swap)
        boolean newVersionPattern = data.recentInventoryPackets.stream()
            .anyMatch(p -> p.slot() == 40 && p.type().equals("CLICK:SWAP"));

        if (matrixPattern || newVersionPattern) {
            String type = matrixPattern ? "matrix" : "newversion";
            data.addVL(CheckType.AUTO_TOTEM.getDisplayName(), 20.0);
            double vl = data.getVL(CheckType.AUTO_TOTEM.getDisplayName());

            plugin.getLogger().info("[AutoTotem] " + player.getName()
                + " type=" + type
                + " clicks=" + clickCount
                + " swaps=" + swapCount
                + " held=" + heldChanges
                + " window=" + windowMs + "ms");

            punishment.handleViolation(player, data, CheckType.AUTO_TOTEM, vl);
            data.recentInventoryPackets.clear();
        }
    }
}
