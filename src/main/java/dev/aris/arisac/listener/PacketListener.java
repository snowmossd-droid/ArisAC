package dev.aris.arisac.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import dev.aris.arisac.ArisAC;
import dev.aris.arisac.check.combat.*;
import dev.aris.arisac.check.spoof.AntiSpoofCheck;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.manager.PlayerDataManager;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class PacketListener extends PacketListenerAbstract {

    private final ArisAC plugin;
    private final PlayerDataManager dataManager;
    private final KillAuraCheck killAuraCheck;
    private final AutoTotemCheck autoTotemCheck;
    private final ReachCheck reachCheck;
    private final AimAssistCheck aimAssistCheck;
    private final AntiSpoofCheck antiSpoofCheck;

    public PacketListener(ArisAC plugin, PlayerDataManager dataManager,
                          KillAuraCheck killAuraCheck, AutoTotemCheck autoTotemCheck,
                          ReachCheck reachCheck, AimAssistCheck aimAssistCheck,
                          AntiSpoofCheck antiSpoofCheck) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.killAuraCheck = killAuraCheck;
        this.autoTotemCheck = autoTotemCheck;
        this.reachCheck = reachCheck;
        this.aimAssistCheck = aimAssistCheck;
        this.antiSpoofCheck = antiSpoofCheck;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.hasPermission("arisac.bypass")) return;

        PlayerData data = dataManager.get(player);

        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            var pk = new WrapperPlayClientPluginMessage(event);
            String channel = pk.getChannelName();
            byte[] payload = pk.getData();

            if ("minecraft:brand".equals(channel) && payload.length > 0) {
                String brand = readBrandString(payload);
                if (brand != null) {
                    data.spoofData.brandReceivedTime = System.currentTimeMillis();
                    antiSpoofCheck.onBrandReceived(player, data.spoofData, brand);
                    antiSpoofCheck.onBrandAfterMove(player, data.spoofData);
                }
            } else if ("minecraft:register".equals(channel)) {
                Set<String> channels = parseRegisterPayload(payload);
                antiSpoofCheck.onChannelRegister(player, data.spoofData, channels);
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            var pk = new WrapperPlayClientPlayerPositionAndRotation(event);
            float yaw = pk.getYaw();
            float pitch = pk.getPitch();

            antiSpoofCheck.onFirstMove(data.spoofData);
            killAuraCheck.onMovePacket(player, data, yaw, pitch);
            aimAssistCheck.onRotation(player, data, yaw, pitch);
            reachCheck.recordPosition(player);
            data.lastYaw = yaw;
            data.lastPitch = pitch;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
            var pk = new WrapperPlayClientPlayerRotation(event);
            aimAssistCheck.onRotation(player, data, pk.getYaw(), pk.getPitch());
            data.lastYaw = pk.getYaw();
            data.lastPitch = pk.getPitch();
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION) {
            antiSpoofCheck.onFirstMove(data.spoofData);
            reachCheck.recordPosition(player);
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            var pk = new WrapperPlayClientInteractEntity(event);
            if (pk.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                org.bukkit.entity.Entity target = player.getWorld().getEntities().stream()
                        .filter(e -> e.getEntityId() == pk.getEntityId())
                        .findFirst().orElse(null);
                if (target != null) {
                    antiSpoofCheck.onAttackPacket(player, data.spoofData);
                    killAuraCheck.onAttack(player, data);
                    killAuraCheck.onAttackEntity(player, data, target);
                    reachCheck.onAttack(player, target);
                }
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            antiSpoofCheck.onSwingPacket(data.spoofData);
        }

        if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            var pk = new WrapperPlayClientKeepAlive(event);
            long responseMs = System.currentTimeMillis() - data.keepAliveSentTime;
            if (data.keepAliveId == pk.getId() && data.keepAliveSentTime > 0) {
                antiSpoofCheck.onKeepAliveResponse(player, data.spoofData, responseMs);
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            var pk = new WrapperPlayClientEntityAction(event);
            switch (pk.getAction()) {
                case START_SPRINTING -> killAuraCheck.onStartSprint(player, data);
                case STOP_SPRINTING  -> killAuraCheck.onStopSprint(player, data);
                default -> {}
            }
        }

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            var pk = new WrapperPlayClientClickWindow(event);
            String actionType = pk.getWindowClickType() != null
                    ? pk.getWindowClickType().name() : "UNKNOWN";
            autoTotemCheck.onClickContainer(player, data, pk.getSlot(), actionType);
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            var pk = new WrapperPlayClientHeldItemChange(event);
            autoTotemCheck.onHeldItemChange(player, data, pk.getSlot());
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            var pk = new WrapperPlayClientPlayerDigging(event);
            if (pk.getAction() == com.github.retrooper.packetevents.protocol.player.DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
                autoTotemCheck.onSwapOffhand(player, data);
            }
        }
    }

    private String readBrandString(byte[] raw) {
        try {
            int i = 0;
            while (i < raw.length && (raw[i] & 0x80) != 0) i++;
            i++;
            return new String(raw, i, raw.length - i, StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    private Set<String> parseRegisterPayload(byte[] raw) {
        Set<String> out = new HashSet<>();
        int start = 0;
        for (int i = 0; i <= raw.length; i++) {
            if (i == raw.length || raw[i] == 0) {
                if (i > start)
                    out.add(new String(raw, start, i - start, StandardCharsets.UTF_8));
                start = i + 1;
            }
        }
        return out;
    }
    }
