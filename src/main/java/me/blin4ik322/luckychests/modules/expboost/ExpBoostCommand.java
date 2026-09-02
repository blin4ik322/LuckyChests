package me.blin4ik322.luckychests.modules.expboost;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Обработчик команды /expboost (часть модуля expboost).
 *
 * Использование:
 *  /expboost           — показать текущий множитель и состояние
 *  /expboost 1.5       — установить множитель x1.5 и включить модуль
 *  /expboost off       — выключить увеличение опыта (множитель сохраняется)
 */
public class ExpBoostCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            String status = ExpBoostModule.isEnabled() ? ChatColor.GREEN + "включено" : ChatColor.RED + "выключено";
            sender.sendMessage(ChatColor.YELLOW + "[ExpBoost] Текущий множитель: x"
                    + ExpBoostModule.getMultiplier() + ", " + status + ChatColor.YELLOW + ".");
            sender.sendMessage(ChatColor.YELLOW + "Использование: /expboost <множитель> | /expboost off");
            return true;
        }

        String arg = args[0];

        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("выкл")) {
            ExpBoostModule.setEnabled(false);
            sender.sendMessage(ChatColor.RED + "[ExpBoost] Увеличение опыта выключено.");
            return true;
        }

        double multiplier;
        try {
            multiplier = Double.parseDouble(arg.replace(',', '.'));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "[ExpBoost] Некорректное число: " + arg
                    + ". Пример: /expboost 1.5");
            return true;
        }

        if (multiplier <= 0) {
            sender.sendMessage(ChatColor.RED + "[ExpBoost] Множитель должен быть больше 0."
                    + " Чтобы выключить модуль, используй /expboost off.");
            return true;
        }

        ExpBoostModule.setMultiplier(multiplier);
        ExpBoostModule.setEnabled(true);
        sender.sendMessage(ChatColor.GREEN + "[ExpBoost] Множитель опыта установлен: x" + multiplier + ".");
        return true;
    }
}