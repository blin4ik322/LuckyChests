package me.blin4ik322.luckychests.modules.announce;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обработчик команды /announce (часть модуля announce).
 *
 * Формат: /announce "<title>" "<subtitle>" <секунды>s
 * Пустой title/subtitle указывается как "".
 *
 * Показывает всем игрокам title/subtitle на экране (с поддержкой цветовых
 * и форматирующих кодов, например &l&c) и один раз проигрывает звук
 * колокола каждому игроку в момент показа.
 */
public class AnnounceCommand implements CommandExecutor {

    private static final String PERMISSION = "server.announce";

    // Лимиты по количеству символов, чтобы текст не "вылезал" за края экрана.
    private static final int TITLE_MAX_LEN = 32;
    private static final int SUBTITLE_MAX_LEN = 48;

    // Тайминги появления/исчезновения (в тиках, 20 тиков = 1 сек)
    private static final int FADE_IN = 10;  // 0.5 сек
    private static final int FADE_OUT = 10; // 0.5 сек

    // "title" "subtitle" длительность(s)
    private static final Pattern ANNOUNCE_PATTERN =
            Pattern.compile("^\"(.*)\"\\s+\"(.*)\"\\s+(\\d+)s?$", Pattern.DOTALL);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String full = String.join(" ", args);
        Matcher matcher = ANNOUNCE_PATTERN.matcher(full);
        if (!matcher.matches()) {
            sendUsage(sender);
            return true;
        }

        String rawTitle = matcher.group(1);
        String rawSubtitle = matcher.group(2);
        int seconds;
        try {
            seconds = Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Длительность должна быть числом секунд, например 30s.");
            return true;
        }

        if (seconds <= 0) {
            sender.sendMessage(ChatColor.RED + "Длительность должна быть больше 0.");
            return true;
        }

        if (rawTitle.length() > TITLE_MAX_LEN) {
            sender.sendMessage(ChatColor.YELLOW + "Внимание: title длиннее " + TITLE_MAX_LEN
                    + " символов, он будет обрезан, чтобы не вылезать за экран.");
            rawTitle = rawTitle.substring(0, TITLE_MAX_LEN);
        }
        if (rawSubtitle.length() > SUBTITLE_MAX_LEN) {
            sender.sendMessage(ChatColor.YELLOW + "Внимание: subtitle длиннее " + SUBTITLE_MAX_LEN
                    + " символов, он будет обрезан, чтобы не вылезать за экран.");
            rawSubtitle = rawSubtitle.substring(0, SUBTITLE_MAX_LEN);
        }

        String title = translateColors(rawTitle);
        String subtitle = translateColors(rawSubtitle);

        int stayTicks = seconds * 20;
        int adjustedStay = Math.max(0, stayTicks - FADE_IN - FADE_OUT);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, subtitle, FADE_IN, adjustedStay, FADE_OUT);
            // звук колокола ровно один раз каждому игроку
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        }

        sender.sendMessage(ChatColor.GREEN + "Объявление отправлено (" + seconds + " сек.).");
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Использование: /announce \"<title>\" \"<subtitle>\" <секунды>s");
        sender.sendMessage(ChatColor.GRAY + "Чтобы оставить title или subtitle пустым — просто \"\".");
        sender.sendMessage(ChatColor.GRAY + "Примеры:");
        sender.sendMessage(ChatColor.GRAY + "  /announce \"\" \"Привет всех!\" 30s");
        sender.sendMessage(ChatColor.GRAY + "  /announce \"&l&cПривет ребят!\" \"\" 30s");
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
}
