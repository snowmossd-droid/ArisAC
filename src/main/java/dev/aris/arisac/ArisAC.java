package dev.aris.arisac;

import com.github.retrooper.packetevents.PacketEvents;
import dev.aris.arisac.check.combat.*;
import dev.aris.arisac.check.spoof.AntiSpoofCheck;
import dev.aris.arisac.command.ArisACCommand;
import dev.aris.arisac.listener.BukkitListener;
import dev.aris.arisac.listener.PacketListener;
import dev.aris.arisac.listener.PacketSendListener;
import dev.aris.arisac.manager.ModDetectionManager;
import dev.aris.arisac.manager.PlayerDataManager;
import dev.aris.arisac.manager.PunishmentManager;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ArisAC extends JavaPlugin {

    private PlayerDataManager dataManager;
    private PunishmentManager punishmentManager;
    private ModDetectionManager modDetectionManager;
    private KillAuraCheck killAuraCheck;
    private AutoTotemCheck autoTotemCheck;
    private ReachCheck reachCheck;
    private AimAssistCheck aimAssistCheck;
    private AntiSpoofCheck antiSpoofCheck;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataManager         = new PlayerDataManager();
        punishmentManager   = new PunishmentManager(this);
        modDetectionManager = new ModDetectionManager(this);
        modDetectionManager.load();

        killAuraCheck  = new KillAuraCheck(this, punishmentManager);
        autoTotemCheck = new AutoTotemCheck(this, punishmentManager);
        reachCheck     = new ReachCheck(this, punishmentManager, dataManager);
        aimAssistCheck = new AimAssistCheck(this, punishmentManager);
        antiSpoofCheck = new AntiSpoofCheck(this, modDetectionManager);

        PacketEvents.getAPI().getEventManager().registerListener(
                new PacketListener(this, dataManager, killAuraCheck,
                        autoTotemCheck, reachCheck, aimAssistCheck, antiSpoofCheck));
        PacketEvents.getAPI().getEventManager().registerListener(
                new PacketSendListener(dataManager));
        PacketEvents.getAPI().init();

        Bukkit.getPluginManager().registerEvents(
                new BukkitListener(this, dataManager, punishmentManager,
                        killAuraCheck, aimAssistCheck, reachCheck, antiSpoofCheck), this);

        var cmd = getCommand("arisac");
        if (cmd != null) cmd.setExecutor(
                new ArisACCommand(this, dataManager, modDetectionManager));

        startTickTask();

        getLogger().info("ArisAC enabled | Checks: KillAura, AutoTotem, Reach, AimAssist, AntiSpoof");
        getLogger().info("Mod entries loaded: " + modDetectionManager.getAllMods().size());
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        dataManager.removeAll();
        getLogger().info("ArisAC disabled.");
    }

    private void startTickTask() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.isOnline() || p.hasPermission("arisac.bypass")) continue;
                var data = dataManager.get(p);
                killAuraCheck.tickDecay(data);
                reachCheck.tickDecay(data);
                aimAssistCheck.tickDecay(data);
            }
        }, 1L, 1L);
    }

    public PlayerDataManager getDataManager()           { return dataManager; }
    public PunishmentManager getPunishmentManager()     { return punishmentManager; }
    public ModDetectionManager getModDetectionManager() { return modDetectionManager; }
}
