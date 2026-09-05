package me.blin4ik322.luckychests.modules.lootchests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * /lootchests — администрирование модуля "Хранилища":
 * add/rem <x> <y> <z>, max <n>, list, timer set <m:s>,
 * drop add/rem/list, start/stop.
 */
public class LootChestsCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "luckychests.admin";
    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:(\\d+)m)?(?:(\\d+)s)?$");

    private final LootChestsModule module;

    public LootChestsCommand(LootChestsModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> handleAdd(sender, args);
            case "rem" -> handleRem(sender, args);
            case "max" -> handleMax(sender, args);
            case "list" -> handleList(sender);
            case "timer" -> handleTimer(sender, args);
            case "drop" -> handleDrop(sender, args);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ---------------------------------------------------------------
    // add / rem
    // ---------------------------------------------------------------

    private void handleAdd(CommandSender sender, String[] args) {
        Location loc = parseCoords(sender, args);
        if (loc == null) {
            sender.sendMessage(Component.text("Использование: /lootchests add <x> <y> <z>", NamedTextColor.RED));
            return;
        }
        if (module.addLocation(loc)) {
            sender.sendMessage(Component.text("Координата добавлена: ", NamedTextColor.GREEN)
                    .append(formatCoords(loc)));
        } else {
            sender.sendMessage(Component.text("Такая координата уже есть в списке.", NamedTextColor.YELLOW));
        }
    }

    private void handleRem(CommandSender sender, String[] args) {
        // Удаление по номеру: /lootchests rem <номер>
        if (args.length == 2) {
            try {
                int index = Integer.parseInt(args[1]);
                List<Location> locations = module.getLocations();
                if (index < 1 || index > locations.size()) {
                    sender.sendMessage(Component.text(
                            "Номер должен быть от 1 до " + locations.size() + ".", NamedTextColor.RED));
                    return;
                }
                Location loc = locations.get(index - 1);
                module.removeLocation(loc);
                sender.sendMessage(Component.text("Координата #" + index + " удалена: ", NamedTextColor.GREEN)
                        .append(formatCoords(loc)));
                return;
            } catch (NumberFormatException ignored) {
                // не число — значит это не номер, падаем вниз к старой логике
            }
        }
        // Удаление по координатам: /lootchests rem <x> <y> <z>
        Location loc = parseCoords(sender, args);
        if (loc == null) {
            sender.sendMessage(Component.text("Использование: /lootchests rem <номер>  или  rem <x> <y> <z>", NamedTextColor.RED));
            return;
        }
        if (module.removeLocation(loc)) {
            sender.sendMessage(Component.text("Координата удалена: ", NamedTextColor.GREEN)
                    .append(formatCoords(loc)));
        } else {
            sender.sendMessage(Component.text("Такой координаты нет в списке.", NamedTextColor.YELLOW));
        }
    }

    private Location parseCoords(CommandSender sender, String[] args) {
        if (args.length != 4) {
            return null;
        }
        World world = sender instanceof Player player ? player.getWorld() : Bukkit.getWorlds().get(0);
        try {
            int x = Integer.parseInt(args[1]);
            int y = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Component formatCoords(Location loc) {
        return Component.text(loc.getWorld().getName() + " (" + loc.getBlockX() + ", "
                + loc.getBlockY() + ", " + loc.getBlockZ() + ")", NamedTextColor.YELLOW);
    }

    // ---------------------------------------------------------------
    // max
    // ---------------------------------------------------------------

    private void handleMax(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Использование: /lootchests max <кол-во>", NamedTextColor.RED));
            return;
        }
        try {
            int max = Integer.parseInt(args[1]);
            module.setMax(max);
            sender.sendMessage(Component.text("Максимум одновременных хранилищ: " + max, NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Кол-во должно быть числом.", NamedTextColor.RED));
        }
    }

    // ---------------------------------------------------------------
    // list
    // ---------------------------------------------------------------

    private void handleList(CommandSender sender) {
        List<Location> locations = module.getLocations();
        sender.sendMessage(Component.text("═══ Координаты хранилищ ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Статус: ", NamedTextColor.GRAY)
                .append(module.isRunning()
                        ? Component.text("запущен", NamedTextColor.GREEN)
                        : Component.text("остановлен", NamedTextColor.RED))
                .append(Component.text("  |  Активно: " + module.getActiveCount() + "/" + module.getMax(), NamedTextColor.GRAY)));

        if (locations.isEmpty()) {
            sender.sendMessage(Component.text("Список пуст.", NamedTextColor.GRAY));
            return;
        }
        int index = 1;
        for (Location loc : locations) {
            sender.sendMessage(Component.text(" " + index + ". ", NamedTextColor.DARK_GRAY)
                    .append(formatCoords(loc)));
            index++;
        }
    }

    // ---------------------------------------------------------------
    // timer set <m:s>
    // ---------------------------------------------------------------

    private void handleTimer(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("set")) {
            sender.sendMessage(Component.text("Использование: /lootchests timer set <время, например 2m30s>", NamedTextColor.RED));
            return;
        }
        Long seconds = parseTime(args[2]);
        if (seconds == null || seconds <= 0) {
            sender.sendMessage(Component.text("Неверный формат времени. Пример: 2m30s, 45s, 3m", NamedTextColor.RED));
            return;
        }
        module.setTimerSeconds(seconds);
        sender.sendMessage(Component.text("Таймер исчезновения хранилищ: "
                + String.format("%d:%02d", seconds / 60, seconds % 60), NamedTextColor.GREEN));
    }

    private Long parseTime(String raw) {
        Matcher matcher = TIME_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }
        String minutesGroup = matcher.group(1);
        String secondsGroup = matcher.group(2);
        if (minutesGroup == null && secondsGroup == null) {
            return null;
        }
        long minutes = minutesGroup != null ? Long.parseLong(minutesGroup) : 0L;
        long seconds = secondsGroup != null ? Long.parseLong(secondsGroup) : 0L;
        return minutes * 60 + seconds;
    }

    // ---------------------------------------------------------------
    // drop add/rem/list
    // ---------------------------------------------------------------

    private void handleDrop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendDropUsage(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> handleDropAdd(sender, args);
            case "rem" -> handleDropRem(sender, args);
            case "list" -> handleDropList(sender);
            default -> sendDropUsage(sender);
        }
    }

    private void handleDropAdd(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage(Component.text(
                    "Использование: /lootchests drop add <название> <мин> <макс> <шанс%>", NamedTextColor.RED));
            return;
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null) {
            sender.sendMessage(Component.text("Неизвестный материал: " + args[2], NamedTextColor.RED));
            return;
        }
        try {
            int min = Integer.parseInt(args[3]);
            int max = Integer.parseInt(args[4]);
            double chance = Double.parseDouble(args[5].replace("%", ""));
            if (min <= 0 || max < min || chance <= 0 || chance > 100) {
                sender.sendMessage(Component.text(
                        "Проверь значения: мин>0, макс>=мин, 0<шанс<=100.", NamedTextColor.RED));
                return;
            }
            module.addDrop(new LootDrop(material, min, max, chance));
            sender.sendMessage(Component.text("Добавлен дроп: ", NamedTextColor.GREEN)
                    .append(Component.text(material.name() + " (" + min + "-" + max + ", " + chance + "%)", NamedTextColor.YELLOW)));
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Мин/макс/шанс должны быть числами.", NamedTextColor.RED));
        }
    }

    private void handleDropRem(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Component.text("Использование: /lootchests drop rem <название>", NamedTextColor.RED));
            return;
        }
        if (module.removeDrop(args[2])) {
            sender.sendMessage(Component.text("Дроп удалён: " + args[2].toUpperCase(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Такого дропа нет в списке.", NamedTextColor.YELLOW));
        }
    }

    private void handleDropList(CommandSender sender) {
        sender.sendMessage(Component.text("═══ Возможный дроп хранилищ ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        if (module.getDrops().isEmpty()) {
            sender.sendMessage(Component.text("Список пуст.", NamedTextColor.GRAY));
            return;
        }
        for (LootDrop drop : module.getDrops()) {
            sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(drop.getName(), NamedTextColor.WHITE))
                    .append(Component.text("  x" + drop.getMin() + "-" + drop.getMax(), NamedTextColor.GRAY))
                    .append(Component.text("  " + drop.getChance() + "%", NamedTextColor.YELLOW)));
        }
    }

    private void sendDropUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Использование: /lootchests drop <add|rem|list> ...", NamedTextColor.RED));
    }

    // ---------------------------------------------------------------
    // start / stop
    // ---------------------------------------------------------------

    private void handleStart(CommandSender sender) {
        module.start();
        sender.sendMessage(Component.text("Спавн хранилищ запущен.", NamedTextColor.GREEN));
    }

    private void handleStop(CommandSender sender) {
        module.stop();
        sender.sendMessage(Component.text(
                "Спавн новых хранилищ остановлен. Уже стоящие останутся до открытия/истечения.", NamedTextColor.YELLOW));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("═══ /lootchests ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("add/rem <x> <y> <z>, max <n>, list,", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("timer set <2m30s>, drop add/rem/list, start/stop", NamedTextColor.GRAY));
    }

    // ---------------------------------------------------------------
    // Tab-completion
    // ---------------------------------------------------------------

    private static final List<String> ROOT = Arrays.asList("add", "rem", "max", "list", "timer", "drop", "start", "stop");
    private static final List<String> DROP_SUB = Arrays.asList("add", "rem", "list");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        if (args[0].equalsIgnoreCase("timer") && args.length == 2) {
            return filter(List.of("set"), args[1]);
        }
        if (args[0].equalsIgnoreCase("drop") && args.length == 2) {
            return filter(DROP_SUB, args[1]);
        }
        // Для rem подсказываем номера из списка
        if (args[0].equalsIgnoreCase("rem") && args.length == 2) {
            List<String> numbers = new ArrayList<>();
            for (int i = 1; i <= module.getLocations().size(); i++) {
                numbers.add(String.valueOf(i));
            }
            return filter(numbers, args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.startsWith(lower)).collect(Collectors.toList());
    }
}