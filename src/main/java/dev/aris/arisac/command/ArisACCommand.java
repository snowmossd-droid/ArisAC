package dev.aris.arisac.command;

import dev.aris.arisac.ArisAC;
import dev.aris.arisac.data.PlayerData;
import dev.aris.arisac.manager.ModDetectionManager;
import dev.aris.arisac.manager.PlayerDataManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArisACCommand implements CommandExecutor {

    private final ArisAC plugin;
    private final PlayerDataManager dataManager;
    private final ModDetectionManager modDetection;

    public ArisACCommand(ArisAC plugin, PlayerDataManager dataManager,
                         ModDetectionManager modDetection) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.modDetection = modDetection;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arisac.admin")) {
            send(sender, plugin.getConfig().getString("messages.no-permission", "&cKhong co quyen."));
            return true;
        }

        if (args.length == 0) {
            send(sender, "&8[&cArisAC&8] &7v" + plugin.getDescription().getVersion());
            send(sender, "&7/arisac reload          &8- &fTai lai config + mods.yml");
            send(sender, "&7/arisac flags <player>  &8- &fXem flags cua player");
            send(sender, "&7/arisac clearflags <p>  &8- &fXoa flags");
            send(sender, "&7/arisac modlist         &8- &fXem danh sach mod dang detect");
            send(sender, "&7/arisac inspect <p>     &8- &fXem brand + channels cua player");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.reloadConfig();
                modDetection.load();
                send(sender, "&aTai lai config va mods.yml thanh cong. Mod entries: &f"
                        + modDetection.getAllMods().size());
            }

            case "flags" -> {
                if (args.length < 2) { send(sender, "&cDung: /arisac flags <player>"); return true; }
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) { send(sender, "&cKhong tim thay player."); return true; }
                PlayerData data = dataManager.get(t);
                send(sender, "&f" + t.getName() + " &7| Flags: &c" + data.getFlagCount()
                        + " &7| Brand: &f" + data.spoofData.brand
                        + " &7| Channels: &f" + data.spoofData.registeredChannels.size()
                        + " &7| Mod detected: &c" + (data.spoofData.detectedMod != null
                            ? data.spoofData.detectedMod : "none"));
            }

            case "clearflags" -> {
                if (args.length < 2) { send(sender, "&cDung: /arisac clearflags <player>"); return true; }
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) { send(sender, "&cKhong tim thay player."); return true; }
                dataManager.get(t).resetFlags();
                send(sender, "&aXoa flags cua &f" + t.getName() + " &athank cong.");
            }

            case "modlist" -> {
                List<ModDetectionManager.ModEntry> mods = modDetection.getAllMods();
                send(sender, "&8[&cArisAC&8] &7Dang detect &f" + mods.size() + " &7mod:");
                for (ModDetectionManager.ModEntry m : mods) {
                    String channels = m.channels().isEmpty() ? "&8(brand only)" :
                            "&7channels: &f" + m.channels().size();
                    String brands = m.brands().isEmpty() ? "" :
                            " &7brands: &f" + m.brands().size();
                    send(sender, "  &c" + m.displayName() + " &8[" + m.action() + "] "
                            + channels + brands);
                }
            }

            case "inspect" -> {
                if (args.length < 2) { send(sender, "&cDung: /arisac inspect <player>"); return true; }
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) { send(sender, "&cKhong tim thay player."); return true; }
                PlayerData data = dataManager.get(t);
                send(sender, "&8--- &fInspect: " + t.getName() + " &8---");
                send(sender, "&7Brand: &f" + data.spoofData.brand
                        + " &7| Ping: &f" + t.getPing() + "ms");
                send(sender, "&7Channels (&f" + data.spoofData.registeredChannels.size() + "&7):");
                for (String ch : data.spoofData.registeredChannels) {
                    send(sender, "  &8- &f" + ch);
                }
                if (data.spoofData.detectedMod != null)
                    send(sender, "&cDetected: " + data.spoofData.detectedMod);
            }

            default -> send(sender, "&cLenh khong hop le. Dung /arisac de xem huong dan.");
        }
        return true;
    }

    private void send(CommandSender s, String msg) {
        s.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }
}
