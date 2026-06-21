package dev.aris.arisac.manager;

import dev.aris.arisac.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final ConcurrentHashMap<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    public PlayerData get(Player player) { return get(player.getUniqueId()); }

    public PlayerData get(UUID uuid) {
        return dataMap.computeIfAbsent(uuid, PlayerData::new);
    }

    public void remove(UUID uuid) {
        PlayerData d = dataMap.remove(uuid);
        if (d != null) {
            d.positionHistory.clear();
            d.aimProcessor.reset();
        }
    }

    public void removeAll() { dataMap.clear(); }
}
