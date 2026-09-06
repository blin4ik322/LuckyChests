package me.blin4ik322.luckychests.modules.automelter;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Обработчик команд /melt и /плавить (часть модуля automelter).
 * Переключает состояние автоплавки руды (AutoMelterModule) вкл/выкл.
 */
public class MeltCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean newState = AutoMelterModule.toggle();

        if (newState) {
            sender.sendMessage(ChatColor.GREEN + "[АвтоПлавка] Автоплавка руды включена.");
        } else {
            sender.sendMessage(ChatColor.RED + "[АвтоПлавка] Автоплавка руды выключена.");
        }
        return true;
    }
}