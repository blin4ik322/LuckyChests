package me.blin4ik322.luckychests.modules.clans;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.UUID;

/**
 * Обработчик команды /top — показывает топ кланов, отсортированных
 * по очкам (счётчик ClanScores), красиво оформленный цветом.
 *
 * Формат вывода:
 *  [⭐] Топ кланов:
 *
 *  1. Название (очки очков):
 *  🥇 Лидер
 *   ▪ Участник1
 *   ▪ Участник2
 *  ...
 */
public class ClanTopCommand implements CommandExecutor {

    private static final int TOP_SIZE = 10;

    private final ClanManager manager;

    public ClanTopCommand(ClanManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Clan> top = manager.getTopClans(TOP_SIZE);

        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Пока что не создано ни одного клана.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[⭐] " + ChatColor.YELLOW
                + ChatColor.BOLD + "Топ кланов:");
        sender.sendMessage("");

        int place = 1;
        for (Clan clan : top) {
            printClan(sender, clan, place);
            place++;
        }

        return true;
    }

    private void printClan(CommandSender sender, Clan clan, int place) {
        String leaderName = resolveName(clan.getLeader());

        sender.sendMessage(ChatColor.GRAY + "" + place + ". "
                + ChatColor.AQUA + "" + ChatColor.BOLD + clan.getName()
                + ChatColor.RESET + ChatColor.YELLOW + " (" + clan.getScore() + " очков):");

        sender.sendMessage(ChatColor.GOLD + " \uD83E\uDD47 " + ChatColor.GOLD + ChatColor.BOLD + leaderName);

        for (UUID memberId : clan.getMembers()) {
            if (memberId.equals(clan.getLeader())) {
                continue;
            }
            String memberName = resolveName(memberId);
            sender.sendMessage(ChatColor.GRAY + "  ▪ " + ChatColor.WHITE + memberName);
        }

        sender.sendMessage("");
    }

    private String resolveName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : "???";
    }
}