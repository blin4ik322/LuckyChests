package me.blin4ik322.luckychests.modules.playersbattle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /bounties, /награды — список игроков, за убийство которых сейчас дают
 * больше минимума, отсортированный по убыванию награды. Доступна всем.
 */
public class BountiesCommand implements CommandExecutor {

    private final PlayerBattleManager manager;

    public BountiesCommand(PlayerBattleManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!manager.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Награды за убийство игроков сейчас выключены.");
            return true;
        }

        List<Map.Entry<UUID, Integer>> bounties = manager.getBounties();

        if (bounties.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Пока ни у кого нет повышенной награды за голову.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[\uD83D\uDCB0] " + ChatColor.YELLOW
                + ChatColor.BOLD + "Награды за игроков:");
        sender.sendMessage("");

        int place = 1;
        for (Map.Entry<UUID, Integer> entry : bounties) {
            String name = resolveName(entry.getKey());
            int streak = manager.getStreak(entry.getKey());
            sender.sendMessage(ChatColor.GRAY + "" + place + ". "
                    + ChatColor.AQUA + name + ChatColor.RESET
                    + ChatColor.YELLOW + " — " + entry.getValue() + " очков"
                    + ChatColor.GRAY + " (" + streak + " убийств подряд)");
            place++;
        }

        return true;
    }

    private String resolveName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : "???";
    }
}
