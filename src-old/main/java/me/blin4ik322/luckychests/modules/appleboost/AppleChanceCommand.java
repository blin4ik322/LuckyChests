package me.blin4ik322.luckychests.modules.appleboost;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Обработчик команды /applechance (часть модуля appleboost).
 * Доступна только операторам сервера.
 *
 * Использование:
 *  /applechance         — показать текущий множитель и итоговый шанс
 *  /applechance 5       — увеличить шанс выпадения яблока в 5 раз и включить модуль
 *  /applechance off     — выключить увеличение шанса (вернуться к ванильному дропу)
 */
public class AppleChanceCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Команда только для операторов.
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "У вас недостаточно прав для использования этой команды.");
            return true;
        }

        if (args.length == 0) {
            String status = AppleBoostModule.isEnabled() ? ChatColor.GREEN + "включено" : ChatColor.RED + "выключено";
            double chance = AppleBoostModule.calculateChance();
            sender.sendMessage(ChatColor.YELLOW + "[AppleChance] Множитель: x" + AppleBoostModule.getMultiplier()
                    + ", " + status + ChatColor.YELLOW + ".");
            sender.sendMessage(ChatColor.YELLOW + String.format(
                    "Итоговый шанс дропа яблока с листвы: %.2f%%", chance * 100));
            sender.sendMessage(ChatColor.YELLOW + "Использование: /applechance <множитель> | /applechance off");
            return true;
        }

        String arg = args[0];

        if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("выкл")) {
            AppleBoostModule.setEnabled(false);
            sender.sendMessage(ChatColor.RED + "[AppleChance] Увеличенный шанс дропа яблока выключен"
                    + " (действует стандартная логика игры).");
            return true;
        }

        double multiplier;
        try {
            multiplier = Double.parseDouble(arg.replace(',', '.'));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "[AppleChance] Некорректное число: " + arg
                    + ". Пример: /applechance 5");
            return true;
        }

        if (multiplier <= 0) {
            sender.sendMessage(ChatColor.RED + "[AppleChance] Множитель должен быть больше 0."
                    + " Чтобы выключить модуль, используй /applechance off.");
            return true;
        }

        AppleBoostModule.setMultiplier(multiplier);
        AppleBoostModule.setEnabled(true);
        double chance = AppleBoostModule.calculateChance();
        sender.sendMessage(ChatColor.GREEN + String.format(
                "[AppleChance] Множитель установлен: x%s (итоговый шанс: %.2f%%).",
                multiplier, chance * 100));
        return true;
    }
}
