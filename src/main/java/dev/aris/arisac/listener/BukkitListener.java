package dev.aris.arisac.listener;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.check.combat.AimAssistCheck;
import dev.aris.arisac.check.combat.KillAuraCheck;
import dev.aris.arisac.check.combat.ReachCheck;
import dev.aris.arisac.check.spoof.AntiSpoofCheck;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.manager.PlayerDataManager;
import dev.aris.arisac.manager.PunishmentManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class BukkitListener implements Listener {

    private final ArisAC plugin;
    private final PlayerDataManager dataManager;
    private final PunishmentManager punishment;
    private final KillAuraCheck killAura;
    private final AimAssistCheck aimAssist;
    private final ReachCheck reach;
    private final AntiSpoofCheck antiSpoof;

    public BukkitListener(ArisAC plugin, PlayerDataManager dataManager,
                          PunishmentManager punishment, KillAuraCheck killAura,
                          AimAssistCheck aimAssist, ReachCheck reach,
                          AntiSpoofCheck antiSpoof) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
        this.punishment  = punishment;
        this.killAura    = killAura;
        this.aimAssist   = aimAssist;
        this.reach       = reach;
        this.antiSpoof   = antiSpoof;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        PlayerData data = dataManager.get(e.getPlayer());
        data.resetSpoofData();
        data.spoofData.joinTime = System.currentTimeMillis();
        punishment.checkFlagExpiry(e.getPlayer(), data);

        long delayTicks = plugin.getModDetectionManager().getCheckDelaySeconds() * 20L + 40L;
        e.getPlayer().getScheduler().runDelayed(plugin, task -> {
            if (e.getPlayer().isOnline()) {
                antiSpoof.onJoinDelayCheck(e.getPlayer(), data.spoofData);
            }
        }, null, delayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        dataManager.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.isCancelled()) return;
        PlayerData data = dataManager.get(e.getPlayer());
        data.teleporting = true;
        data.positionHistory.clear();
        data.clearKillAuraState();
        aimAssist.onTeleport(data);
        e.getPlayer().getScheduler().runDelayed(plugin,
                task -> data.teleporting = false, null, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        PlayerData data = dataManager.get(e.getPlayer());
        data.teleporting = true;
        data.positionHistory.clear();
        data.clearKillAuraState();
        aimAssist.onTeleport(data);
        e.getPlayer().getScheduler().runDelayed(plugin,
                task -> data.teleporting = false, null, 5L);
    }
}
