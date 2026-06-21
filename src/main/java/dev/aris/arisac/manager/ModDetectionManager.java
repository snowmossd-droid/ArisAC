package dev.aris.arisac.manager;

import dev.aris.arisac.ArisAC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ModDetectionManager {

    public record ModEntry(
            String id,
            String displayName,
            String action,
            Set<String> channels,
            Set<String> brands
    ) {}

    // channel → ModEntry
    private final Map<String, ModEntry> channelIndex = new ConcurrentHashMap<>();
    // brand (lowercase) → ModEntry
    private final Map<String, ModEntry> brandIndex = new ConcurrentHashMap<>();

    private final List<ModEntry> allMods = new ArrayList<>();
    private final ArisAC plugin;

    private boolean instantKick = true;
    private int checkDelaySeconds = 3;
    private boolean zeroChannelEnabled = true;
    private long zeroChannelWaitMs = 4000;
    private boolean zeroChannelOnlyFabric = true;

    public ModDetectionManager(ArisAC plugin) {
        this.plugin = plugin;
    }

    public void load() {
        channelIndex.clear();
        brandIndex.clear();
        allMods.clear();

        File file = new File(plugin.getDataFolder(), "mods.yml");
        if (!file.exists()) {
            plugin.saveResource("mods.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        Logger log = plugin.getLogger();

        instantKick = cfg.getBoolean("instant-kick", true);
        checkDelaySeconds = cfg.getInt("check-delay-seconds", 3);

        ConfigurationSection zc = cfg.getConfigurationSection("zero-channel-detection");
        if (zc != null) {
            zeroChannelEnabled = zc.getBoolean("enabled", true);
            zeroChannelWaitMs = zc.getLong("wait-ms", 4000);
            zeroChannelOnlyFabric = zc.getBoolean("only-flag-fabric-brand", true);
        }

        ConfigurationSection mods = cfg.getConfigurationSection("mods");
        if (mods == null) {
            log.warning("[ModDetection] mods.yml khong co section 'mods'.");
            return;
        }

        int loaded = 0;
        for (String modId : mods.getKeys(false)) {
            ConfigurationSection sec = mods.getConfigurationSection(modId);
            if (sec == null) continue;

            String displayName = sec.getString("display-name", modId);
            String action = sec.getString("action", "KICK").toUpperCase();
            List<String> channels = sec.getStringList("channels");
            List<String> brands = sec.getStringList("brands");

            Set<String> channelSet = new HashSet<>(channels);
            Set<String> brandSet = new HashSet<>();
            for (String b : brands) brandSet.add(b.toLowerCase().trim());

            ModEntry entry = new ModEntry(modId, displayName, action, channelSet, brandSet);
            allMods.add(entry);

            for (String ch : channelSet) channelIndex.put(ch.toLowerCase(), entry);
            for (String br : brandSet)  brandIndex.put(br, entry);

            loaded++;
        }

        log.info("[ModDetection] Da tai " + loaded + " mod entries tu mods.yml");
    }

    /** Kiem tra mot channel co thuoc mod nao khong */
    public ModEntry findByChannel(String channel) {
        return channelIndex.get(channel.toLowerCase());
    }

    /** Kiem tra brand co thuoc mod nao khong */
    public ModEntry findByBrand(String brand) {
        return brandIndex.get(brand.toLowerCase().trim());
    }

    public boolean isInstantKick() { return instantKick; }
    public int getCheckDelaySeconds() { return checkDelaySeconds; }
    public boolean isZeroChannelEnabled() { return zeroChannelEnabled; }
    public long getZeroChannelWaitMs() { return zeroChannelWaitMs; }
    public boolean isZeroChannelOnlyFabric() { return zeroChannelOnlyFabric; }

    public List<ModEntry> getAllMods() { return Collections.unmodifiableList(allMods); }
}
