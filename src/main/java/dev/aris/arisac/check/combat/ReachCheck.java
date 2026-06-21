package dev.aris.arisac.check.combat;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.check.CheckType;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.data.PositionHistory;
import dev.aris.arisac.manager.PlayerDataManager;
import dev.aris.arisac.manager.PunishmentManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class ReachCheck {

    private final ArisAC plugin;
    private final PunishmentManager punishment;
    private final PlayerDataManager dataManager;

    public ReachCheck(ArisAC plugin, PunishmentManager punishment, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.punishment = punishment;
        this.dataManager = dataManager;
    }

    public void onAttack(Player attacker, Entity target) {
        if (!plugin.getConfig().getBoolean("checks.reach.enabled", true)) return;
        if (attacker.getGameMode() == GameMode.CREATIVE) return;
        if (!(target instanceof LivingEntity)) return;

        PlayerData attackerData = dataManager.get(attacker);
        if (attackerData.teleporting) return;

        double maxDist = plugin.getConfig().getDouble("checks.reach.max-distance", 3.1);
        double pingDivisor = plugin.getConfig().getDouble("checks.reach.ping-buffer-divisor", 20.0);
        double maxPingBuf = plugin.getConfig().getDouble("checks.reach.max-ping-buffer", 0.5);

        // Lag compensation: buffer tăng theo ping
        double pingBuffer = Math.min(attacker.getPing() / pingDivisor / 20.0, maxPingBuf);
        double allowedDist = maxDist + pingBuffer;

        // Lấy position của target tại thời điểm attacker đã gửi packet
        // (bù lag: targetTime = now - attacker ping)
        long attackTime = System.currentTimeMillis();
        long attackerPing = attacker.getPing();
        long targetTime = attackTime - attackerPing;

        double distance;

        if (target instanceof Player targetPlayer) {
            PlayerData targetData = dataManager.get(targetPlayer);
            double[] lagPos = targetData.positionHistory.getPositionAt(targetTime);

            if (lagPos != null) {
                // lagPos[0..2] = x, y (center), z
                double dx = attacker.getEyeLocation().getX() - lagPos[0];
                double dy = attacker.getEyeLocation().getY() - lagPos[1];
                double dz = attacker.getEyeLocation().getZ() - lagPos[2];
                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            } else {
                // Fallback: dùng current position
                distance = attacker.getEyeLocation().distance(
                        target.getLocation().add(0, target.getHeight() / 2.0, 0));
            }
        } else {
            distance = attacker.getEyeLocation().distance(
                    target.getLocation().add(0, target.getHeight() / 2.0, 0));
        }

        if (distance > allowedDist) {
            double excess = distance - allowedDist;
            double vlAdd = Math.min(excess * 8.0, 6.0); // scale theo mức vượt, cap 6
            attackerData.addVL(CheckType.REACH.getDisplayName(), vlAdd);
            double vl = attackerData.getVL(CheckType.REACH.getDisplayName());

            plugin.getLogger().info("[Reach] " + attacker.getName()
                    + " dist=" + String.format("%.3f", distance)
                    + " allowed=" + String.format("%.3f", allowedDist)
                    + " excess=" + String.format("%.3f", excess)
                    + " ping=" + attackerPing + "ms");

            punishment.handleViolation(attacker, attackerData, CheckType.REACH, vl);
        } else if (distance <= allowedDist - 0.3) {
            // Giảm VL khi distance hoàn toàn ok
            attackerData.decayVL(CheckType.REACH.getDisplayName(), 0.5);
        }
    }

    // Gọi từ PacketListener mỗi khi nhận position packet của player
    public void recordPosition(Player player) {
        if (!plugin.getConfig().getBoolean("checks.reach.enabled", true)) return;
        PlayerData data = dataManager.get(player);
        if (!data.teleporting) {
            data.positionHistory.record(
                    player.getLocation(),
                    (float) player.getBoundingBox().getWidthX(),
                    (float) player.getBoundingBox().getHeight());
        }
    }

    public void tickDecay(PlayerData data) {
        double decay = plugin.getConfig().getDouble("checks.reach.vl-decay-per-tick", 0.8);
        data.decayVL(CheckType.REACH.getDisplayName(), decay);
    }
}
