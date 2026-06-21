package dev.aris.arisac.check.combat;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.check.CheckType;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.manager.PunishmentManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class KillAuraCheck {

    private final ArisAC plugin;
    private final PunishmentManager punishment;

    public KillAuraCheck(ArisAC plugin, PunishmentManager punishment) {
        this.plugin = plugin;
        this.punishment = punishment;
    }

    // ── A: Drop Sprint ───────────────────────────────────────
    // STOP_SPRINTING → attack → START_SPRINTING cùng tick
    // ThunderHack gửi sequence này mỗi lần attack khi sprint

    public void onStopSprint(Player player, PlayerData data) {
        data.stopSprintTime = System.currentTimeMillis();
        data.hadStopSprint = true;
        data.startSprintTime = -1;
    }

    public void onStartSprint(Player player, PlayerData data) {
        if (data.hadStopSprint && data.attackTime > 0) {
            data.startSprintTime = System.currentTimeMillis();
            evaluateDropSprint(player, data);
        } else {
            data.clearKillAuraState();
        }
    }

    public void onAttack(Player player, PlayerData data) {
        if (data.hadStopSprint) {
            data.attackTime = System.currentTimeMillis();
        }
    }

    private void evaluateDropSprint(Player player, PlayerData data) {
        if (!plugin.getConfig().getBoolean("checks.killaura.enabled", true)) return;

        long window = plugin.getConfig().getLong("checks.killaura.drop-sprint-window-ms", 80);
        long stopToAttack = data.attackTime - data.stopSprintTime;
        long attackToStart = data.startSprintTime - data.attackTime;

        if (stopToAttack >= 0 && stopToAttack <= window
                && attackToStart >= 0 && attackToStart <= window) {

            data.addVL(CheckType.KILLAURA_DROP_SPRINT.getDisplayName(), 6.0);
            double vl = data.getVL(CheckType.KILLAURA_DROP_SPRINT.getDisplayName());
            punishment.handleViolation(player, data, CheckType.KILLAURA_DROP_SPRINT, vl);
        }

        data.clearKillAuraState();
    }

    // ── B: Grim double-move ───────────────────────────────────
    // 2 PlayerPosition packet với rotation khác nhau trong cùng 1 tick
    // ThunderHack Grim mode: gửi fake rotation packet trước khi attack

    public void onMovePacket(Player player, PlayerData data, float yaw, float pitch) {
        if (!plugin.getConfig().getBoolean("checks.killaura.enabled", true)) return;

        long now = System.currentTimeMillis();
        long grimWindow = plugin.getConfig().getLong("checks.killaura.grim-mode-window-ms", 15);

        if (data.lastMovePacketTime > 0
                && (now - data.lastMovePacketTime) <= grimWindow
                && !Float.isNaN(data.lastMoveYaw)) {

            float yawDiff = Math.abs(normalize(yaw - data.lastMoveYaw));
            float pitchDiff = Math.abs(pitch - data.lastMovePitch);

            // Rotation thay đổi > 5° giữa 2 move packet trong <15ms = Grim mode
            if (yawDiff > 5f || pitchDiff > 5f) {
                data.addVL(CheckType.KILLAURA_GRIM_MOVE.getDisplayName(), 8.0);
                double vl = data.getVL(CheckType.KILLAURA_GRIM_MOVE.getDisplayName());
                punishment.handleViolation(player, data, CheckType.KILLAURA_GRIM_MOVE, vl);
            }
        }

        data.lastMovePacketTime = now;
        data.lastMoveYaw = yaw;
        data.lastMovePitch = pitch;
    }

    // ── C: Attack angle ───────────────────────────────────────
    // Ray từ mắt player không trúng hitbox của entity bị attack
    // Detect hitbox expansion và attack khi không nhìn target

    public void onAttackEntity(Player attacker, PlayerData data, org.bukkit.entity.Entity target) {
        if (!plugin.getConfig().getBoolean("checks.killaura.enabled", true)) return;
        if (data.teleporting) return;

        double maxAngle = plugin.getConfig().getDouble("checks.killaura.attack-angle-max-degrees", 90.0);

        org.bukkit.Location eye = attacker.getEyeLocation();
        Vector lookDir = eye.getDirection().normalize();
        Vector toTarget = target.getLocation().add(0, target.getHeight() / 2.0, 0)
                .toVector().subtract(eye.toVector()).normalize();

        double cosAngle = Math.max(-1.0, Math.min(1.0, lookDir.dot(toTarget)));
        double angleDeg = Math.toDegrees(Math.acos(cosAngle));

        if (angleDeg > maxAngle) {
            data.addVL(CheckType.KILLAURA_ANGLE.getDisplayName(), 4.0);
            double vl = data.getVL(CheckType.KILLAURA_ANGLE.getDisplayName());
            punishment.handleViolation(attacker, data, CheckType.KILLAURA_ANGLE, vl);
        }
    }

    // ── Decay (gọi mỗi tick) ─────────────────────────────────

    public void tickDecay(PlayerData data) {
        FileConfiguration cfg = plugin.getConfig();
        double decay = cfg.getDouble("checks.killaura.vl-decay-per-tick", 0.5);
        data.decayVL(CheckType.KILLAURA_DROP_SPRINT.getDisplayName(), decay);
        data.decayVL(CheckType.KILLAURA_GRIM_MOVE.getDisplayName(), decay);
        data.decayVL(CheckType.KILLAURA_ANGLE.getDisplayName(), decay);
    }

    private float normalize(float a) {
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }
}
