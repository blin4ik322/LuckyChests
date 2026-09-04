package me.blin4ik322.luckychests.modules.announce;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Обработчик команд /bcast и /broadcast (часть модуля announce).
 * Отправляет всем игрокам оформленное объявление в чат с явным
 * выделением текста оператора.
 */
public class BroadcastCommand implements CommandExecutor {

    // Право на использование объявлений (можно поменять/вынести в конфиг)
    private static final String PERMISSION = "server.announce";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Использование: /" + label + " <сообщение>");
            return true;
        }

        String message = translateColors(String.join(" ", args));
        String senderName = sender instanceof Player ? sender.getName() : "Консоль";

        String bar = ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + repeat("=", 7)
                + ChatColor.RESET + ChatColor.BOLD + ChatColor.RED + " ОБЪЯВЛЕНИЕ " + ChatColor.RESET
                + ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + repeat("=", 7);
        String endBar = ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + repeat("=", 27) + ChatColor.RESET;

        Bukkit.broadcastMessage(" ");
        Bukkit.broadcastMessage(bar);
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "||");
        Bukkit.broadcastMessage(ChatColor.WHITE + "" + ChatColor.BOLD + message);
        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "||");
        Bukkit.broadcastMessage(endBar);

        return true;
    }

    private boolean hasPermission(CommandSender sender) {
        if (sender.isOp() || sender.hasPermission(PERMISSION)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "У вас нет прав на использование этой команды.");
        return false;
    }

    private String translateColors(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(s);
        return sb.toString();
    }
}
