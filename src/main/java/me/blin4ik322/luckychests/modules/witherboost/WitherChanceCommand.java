package me.blin4ik322.luckychests.modules.witherboost;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Обработчик команды /witherchance (часть модуля witherboost).
 * Доступна только операторам сервера (см. проверку sender.isOp() ниже
 * и permission "luckychests.witherchance" в plugin.yml).
 *
 * Использование:
 *  /witherchance         — показать текущий множитель и итоговый шанс (без Looting)
 *  /witherchance 3       — увеличить шанс выпадения черепа в 3 раза и включить модуль
 *  /witherchance off     — выключить увеличение шанса (вернуться к ванильному дропу)
 */
public class WitherChanceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Команда только для операторов.
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "У вас недостаточно прав для использования этой команды.");
            return true;
        }

        if (args.length == 0) {
            String status = WitherBoostModule.isEnabled() ? ChatColor.GREEN + "включено" : ChatColor.RED + "выключено";
            double chance = WitherBoostModule.calculateChance(0);
            sender.sendMessage(ChatColor.YELLOW + "[WitherChance] Множитель: x" + WitherBoostModule.getMultiplier()
                    + ", " + status + ChatColor.YELLOW + ".");
            sender.sendMessage(ChatColor.YELLOW + String.format(
                    "Итоговый шанс дропа черепа без зачарования Looting: %.2f%%", chance * 100));
            sender.sendMessage(ChatColor.YELLOW + "Использование: /witherchance <множитель> | /witherchance off");
            return true;
        }

        String arg = args[0];

        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("выкл")) {
            WitherBoostModule.setEnabled(false);
            sender.sendMessage(ChatColor.RED + "[WitherChance] Увеличенный шанс дропа черепа выключен"
                    + " (действует стандартная логика игры).");
            return true;
        }

        double multiplier;
        try {
            multiplier = Double.parseDouble(arg.replace(',', '.'));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "[WitherChance] Некорректное число: " + arg
                    + ". Пример: /witherchance 3");
            return true;
        }

        if (multiplier <= 0) {
            sender.sendMessage(ChatColor.RED + "[WitherChance] Множитель должен быть больше 0."
                    + " Чтобы выключить модуль, используй /witherchance off.");
            return true;
        }

        WitherBoostModule.setMultiplier(multiplier);
        WitherBoostModule.setEnabled(true);
        double chance = WitherBoostModule.calculateChance(0);
        sender.sendMessage(ChatColor.GREEN + String.format(
                "[WitherChance] Множитель установлен: x%s (итоговый шанс без Looting: %.2f%%).",
                multiplier, chance * 100));
        return true;
    }
}
