package dev.aris.arisac.check.combat;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.check.CheckType;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.data.TickData;
import dev.aris.arisac.manager.PunishmentManager;
import org.bukkit.entity.Player;

public class AimAssistCheck {

    private final ArisAC plugin;
    private final PunishmentManager punishment;
    private static final int MAX_SAMPLES = 80;

    public AimAssistCheck(ArisAC plugin, PunishmentManager punishment) {
        this.plugin = plugin;
        this.punishment = punishment;
    }

    public void onRotation(Player player, PlayerData data, float yaw, float pitch) {
        if (!plugin.getConfig().getBoolean("checks.aimassist.enabled", true)) return;
        if (data.teleporting) return;

        TickData tick = data.aimProcessor.process(yaw, pitch);
        data.addAimSample(tick, MAX_SAMPLES);

        int minSamples = plugin.getConfig().getInt("checks.aimassist.min-samples", 60);
        if (data.jerkYawSamples.size() < minSamples) return;

        // ── Check A: Jerk variance quá thấp ─────────────────
        // Track mode DVD logo: jerk ổn định đều đặn vì aim point
        // di chuyển với velocity nhỏ và cố định trong hitbox
        double jerkVarYaw = PlayerData.variance(data.jerkYawSamples);
        double jerkVarPitch = PlayerData.variance(data.jerkPitchSamples);
        double jerkVar = (jerkVarYaw + jerkVarPitch) / 2.0;

        double jerkThresh = plugin.getConfig().getDouble(
                "checks.aimassist.jerk-variance-threshold", 0.0008);

        if (jerkVar < jerkThresh && jerkVar > 0) {
            data.addVL(CheckType.AIM_ASSIST_JERK.getDisplayName(), 2.5);
        } else {
            data.decayVL(CheckType.AIM_ASSIST_JERK.getDisplayName(), 1.5);
        }

        // ── Check B: GCD error quá đều ───────────────────────
        // Cheat fake GCD = GCD error luôn gần 0 và ổn định
        // Người thật: GCD error dao động vì tay chuột không đều tuyệt đối
        double gcdErrMean = PlayerData.mean(data.gcdErrYawSamples);
        double gcdErrVar = PlayerData.variance(data.gcdErrYawSamples);

        double gcdErrThresh = plugin.getConfig().getDouble(
                "checks.aimassist.gcd-error-mean-threshold", 0.0004);

        // GCD đã được compute nhưng error quá nhỏ và ổn định
        if (data.aimProcessor.getSamplesX() >= 40
                && gcdErrMean < gcdErrThresh
                && gcdErrVar < gcdErrThresh * 0.5) {
            data.addVL(CheckType.AIM_ASSIST_GCD.getDisplayName(), 2.0);
        } else {
            data.decayVL(CheckType.AIM_ASSIST_GCD.getDisplayName(), 1.2);
        }

        // ── Sensitivity mismatch giữa 2 trục ─────────────────
        // Minecraft dùng 1 slider cho cả X và Y
        // Nếu sensitivity từ GCD của 2 trục lệch nhiều = cheat
        double modeX = data.aimProcessor.getModeX();
        double modeY = data.aimProcessor.getModeY();
        if (modeX > 0 && modeY > 0) {
            double sensX = Math.cbrt(modeX / 1.2);
            double sensY = Math.cbrt(modeY / 1.2);
            double mismatch = Math.abs(sensX - sensY);
            double mismatchThresh = plugin.getConfig().getDouble(
                    "checks.aimassist.sensitivity-mismatch-threshold", 0.18);

            if (mismatch > mismatchThresh) {
                data.addVL(CheckType.AIM_ASSIST_GCD.getDisplayName(), 1.5);
            }
        }

        // ── Evaluate punishment ───────────────────────────────
        // Yêu cầu CẢ HAI check A và B đều cao để tránh FP
        double vlJerk = data.getVL(CheckType.AIM_ASSIST_JERK.getDisplayName());
        double vlGcd  = data.getVL(CheckType.AIM_ASSIST_GCD.getDisplayName());
        double jerkThreshold = plugin.getConfig().getDouble("checks.aimassist.vl-threshold", 12.0);
        double gcdThreshold  = jerkThreshold;

        // Cần cả 2 check trigger để flag (giảm false positive)
        if (vlJerk >= jerkThreshold && vlGcd >= gcdThreshold * 0.6) {
            punishment.handleViolation(player, data, CheckType.AIM_ASSIST_JERK, vlJerk);
        } else if (vlGcd >= gcdThreshold && vlJerk >= jerkThreshold * 0.6) {
            punishment.handleViolation(player, data, CheckType.AIM_ASSIST_GCD, vlGcd);
        }
    }

    public void tickDecay(PlayerData data) {
        // AimAssist decay chậm hơn vì cần accumulate nhiều sample
        double decay = plugin.getConfig().getDouble("checks.aimassist.vl-decay-per-tick", 1.0);
        data.decayVL(CheckType.AIM_ASSIST_JERK.getDisplayName(), decay);
        data.decayVL(CheckType.AIM_ASSIST_GCD.getDisplayName(), decay);
    }

    public void onTeleport(PlayerData data) {
        // Reset khi teleport để tránh FP do position jump
        data.aimProcessor.reset();
        data.jerkYawSamples.clear();
        data.jerkPitchSamples.clear();
        data.gcdErrYawSamples.clear();
        data.resetVL(CheckType.AIM_ASSIST_JERK.getDisplayName());
        data.resetVL(CheckType.AIM_ASSIST_GCD.getDisplayName());
    }
}
