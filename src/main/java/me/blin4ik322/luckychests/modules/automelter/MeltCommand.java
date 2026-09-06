package me.blin4ik322.luckychests.modules.automelter;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Обработчик команд /melt и /плавить (часть модуля automelter).
 * Переключает состояние автоплавки руды (AutoMelterModule) вкл/выкл.
 *
 * Только для операторов: AutoMelterModule#enabled — ОДИН общий флаг на весь
 * сервер, а не персональная настройка игрока, поэтому любой, кто может
 * выполнить эту команду, включает и выключает автоплавку сразу всем.
 */
public class MeltCommand implements CommandExecutor {

    private static final String PERMISSION = "luckychests.melt";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Дублирует permission из plugin.yml — на случай, если команду
        // зарегистрируют в обход её объявления.
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "У вас недостаточно прав для использования этой команды.");
            return true;
        }

        boolean newState = AutoMelterModule.toggle();

        if (newState) {
            sender.sendMessage(ChatColor.GREEN + "[АвтоПлавка] Автоплавка руды включена.");
        } else {
            sender.sendMessage(ChatColor.RED + "[АвтоПлавка] Автоплавка руды выключена.");
        }
        return true;
    }
}