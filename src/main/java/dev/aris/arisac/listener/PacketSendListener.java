package dev.aris.arisac.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.manager.PlayerDataManager;
import org.bukkit.entity.Player;

public class PacketSendListener extends PacketListenerAbstract {

    private final PlayerDataManager dataManager;

    public PacketSendListener(PlayerDataManager dataManager) {
        super(PacketListenerPriority.NORMAL);
        this.dataManager = dataManager;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (event.getPacketType() == PacketType.Play.Server.KEEP_ALIVE) {
            var pk = new WrapperPlayServerKeepAlive(event);
            PlayerData data = dataManager.get(player);
            data.keepAliveId = pk.getId();
            data.keepAliveSentTime = System.currentTimeMillis();
        }
    }
}
