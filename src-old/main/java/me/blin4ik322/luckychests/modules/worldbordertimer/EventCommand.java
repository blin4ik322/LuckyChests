package me.blin4ik322.luckychests.modules.worldbordertimer;

import me.blin4ik322.luckychests.modules.WorldBorderTimer;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Команда /event — управление модулем {link worldbordertimer}.
 *
 * /event start {время} {радиус} — запустить сужение барьера мира.
 *   {время} — либо число секунд ("600"), либо сокращённая запись
 *   вида "1d2h30m15s" (день/час/минута/секунда, каждая часть необязательна).
 *   {радиус} — конечный радиус барьера в блоках (расстояние от центра мира
 *   до границы), число может быть дробным.
 * /event stop — досрочно остановить текущий ивент.
 */
public class EventCommand implements CommandExecutor, TabCompleter {

    private final WorldBorderTimer worldBorderTimer;

    public EventCommand(WorldBorderTimer worldBorderTimer) {
        this.worldBorderTimer = worldBorderTimer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("start")) {
            handleStart(sender, args);
            return true;
        }

        if (sub.equals("stop")) {
            handleStop(sender);
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /event start <время> <радиус>");
            sender.sendMessage(ChatColor.GRAY + "Пример: /event start 1h30m 2000");
            return;
        }

        Long durationSeconds = WorldBorderTimer.parseDurationSeconds(args[1]);
        if (durationSeconds == null || durationSeconds <= 0) {
            sender.sendMessage(ChatColor.RED + "Не удалось разобрать время. Примеры: 600, 1h30m, 2d4h.");
            return;
        }

        double radius;
        try {
            radius = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Радиус должен быть числом, например 1500.");
            return;
        }

        if (radius <= 0) {
            sender.sendMessage(ChatColor.RED + "Радиус должен быть больше нуля.");
            return;
        }

        // Мир события всегда main-world того, кто выполнил команду (или default world
        // для консоли), поскольку у WorldBorder привязка идёт к конкретному миру.
        org.bukkit.World world = (sender instanceof Player)
                ? ((Player) sender).getWorld()
                : org.bukkit.Bukkit.getWorlds().get(0);

        worldBorderTimer.start(world, durationSeconds, radius);
        sender.sendMessage(ChatColor.GREEN + "Ивент запущен: барьер мира \"" + world.getName()
                + "\" сузится до радиуса " + (long) radius + " блоков за "
                + WorldBorderTimer.formatCountdown(durationSeconds) + ".");
    }

    private void handleStop(CommandSender sender) {
        if (!worldBorderTimer.isActive()) {
            sender.sendMessage(ChatColor.RED + "Сейчас нет активного ивента сужения барьера.");
            return;
        }
        worldBorderTimer.stop(true);
        sender.sendMessage(ChatColor.GREEN + "Ивент остановлен.");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Ивент] " + ChatColor.YELLOW + "Команды:");
        sender.sendMessage(ChatColor.WHITE + "/event start <время> <радиус>" + ChatColor.GRAY
                + " — запустить сужение барьера мира");
        sender.sendMessage(ChatColor.WHITE + "/event stop" + ChatColor.GRAY
                + " — остановить текущий ивент");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("start", "stop"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return filter(Arrays.asList("30s", "5m", "10m", "1h", "1h30m", "2h", "1d"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return filter(Arrays.asList("100", "500", "1000", "2000", "5000"), args[2]);
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}