package me.blin4ik322.luckychests.modules.playersbattle;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /playerbattle <минимум очков> <максимум очков> — включить функцию наград
 *   за убийство игроков и задать границы динамической награды.
 * /playerbattle off — выключить функцию.
 *
 * Только для операторов (luckychests.playerbattle).
 */
public class PlayerBattleCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "luckychests.playerbattle";

    private final PlayerBattleManager manager;

    public PlayerBattleCommand(PlayerBattleManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Недостаточно прав.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            manager.disable();
            sender.sendMessage(ChatColor.GREEN + "Награды за убийство игроков "
                    + ChatColor.RED + "выключены" + ChatColor.GREEN + ".");
            return true;
        }

        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }

        int min;
        int max;
        try {
            min = Integer.parseInt(args[0]);
            max = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Минимум и максимум должны быть целыми числами.");
            return true;
        }

        if (min <= 0 || max <= min) {
            sender.sendMessage(ChatColor.RED
                    + "Некорректные границы: минимум должен быть больше нуля, а максимум — больше минимума.");
            return true;
        }

        manager.configure(min, max);
        sender.sendMessage(ChatColor.GREEN + "Награды за убийство игроков "
                + ChatColor.YELLOW + "включены" + ChatColor.GREEN + ": от "
                + ChatColor.YELLOW + min + ChatColor.GREEN + " до "
                + ChatColor.YELLOW + max + ChatColor.GREEN + " очков за игрока.");
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Награды за игроков] " + ChatColor.YELLOW + "Команды:");
        sender.sendMessage(ChatColor.WHITE + "/playerbattle <минимум> <максимум>" + ChatColor.GRAY
                + " — включить и задать границы награды");
        sender.sendMessage(ChatColor.WHITE + "/playerbattle off" + ChatColor.GRAY + " — выключить");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> options = Arrays.asList("off", "15", "10");
            String lower = args[0].toLowerCase();
            return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("off")) {
            return Arrays.asList("30", "45", "60").stream()
                    .filter(o -> o.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
