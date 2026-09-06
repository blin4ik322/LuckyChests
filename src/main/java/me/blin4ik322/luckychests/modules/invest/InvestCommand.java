package me.blin4ik322.luckychests.modules.invest;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /invest, /вложить — открыть меню вложений (доступно всем игрокам).
 * /invest add <предмет> <очки> [количество] — задать/обновить цену предмета (операторы).
 * Цена задаётся за партию: "add COBBLED_DEEPSLATE 1 64" = 1 очко за 64 штуки.
 * /invest remove <предмет> — убрать предмет из ценника (операторы).
 * /invest list — посмотреть текущий ценник (операторы).
 */
public class InvestCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "luckychests.invest.admin";

    private final InvestModule module;

    public InvestCommand(InvestModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эту команду может выполнить только игрок.");
                return true;
            }
            module.openMenu((Player) sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("add")) {
            handleAdd(sender, args);
            return true;
        }

        if (sub.equals("remove") || sub.equals("del")) {
            handleRemove(sender, args);
            return true;
        }

        if (sub.equals("list")) {
            handleList(sender);
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Недостаточно прав.");
            return;
        }
        if (args.length != 3 && args.length != 4) {
            sender.sendMessage(ChatColor.RED + "Использование: /invest add <предмет> <очки> [количество]");
            sender.sendMessage(ChatColor.GRAY + "Пример: /invest add COBBLED_DEEPSLATE 1 64"
                    + " — 1 очко за 64 штуки. Без последнего аргумента цена считается за 1 штуку.");
            return;
        }

        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(ChatColor.RED + "Неизвестный предмет: " + args[1]);
            return;
        }

        int points;
        try {
            points = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Очки должны быть целым числом.");
            return;
        }

        if (points <= 0) {
            sender.sendMessage(ChatColor.RED
                    + "Очки должны быть больше нуля (для удаления используй /invest remove).");
            return;
        }

        int unit = 1;
        if (args.length == 4) {
            try {
                unit = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Количество должно быть целым числом.");
                return;
            }
            if (unit <= 0) {
                sender.sendMessage(ChatColor.RED + "Количество должно быть больше нуля.");
                return;
            }
        }

        module.setItemValue(material, points, unit);
        sender.sendMessage(ChatColor.GREEN + "Теперь " + material.name() + " стоит "
                + ChatColor.YELLOW + points + ChatColor.GREEN + " очков за "
                + ChatColor.YELLOW + unit + ChatColor.GREEN + " шт.");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Недостаточно прав.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /invest remove <предмет>");
            return;
        }

        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            sender.sendMessage(ChatColor.RED + "Неизвестный предмет: " + args[1]);
            return;
        }

        if (module.removeItemValue(material)) {
            sender.sendMessage(ChatColor.GREEN + material.name() + " убран из ценника.");
        } else {
            sender.sendMessage(ChatColor.RED + "У этого предмета и так не было цены.");
        }
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Недостаточно прав.");
            return;
        }
        Map<Material, InvestPrice> values = module.getItemValues();
        if (values.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Ценник пуст. Добавь предметы через /invest add.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Ценник вложений:");
        values.forEach((material, price) ->
                sender.sendMessage(ChatColor.WHITE + material.name() + ChatColor.GRAY + " — "
                        + ChatColor.YELLOW + price.getPoints() + ChatColor.GRAY + " очков за "
                        + ChatColor.WHITE + price.getUnit() + ChatColor.GRAY + " шт."));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Вложения] " + ChatColor.YELLOW + "Команды:");
        sender.sendMessage(ChatColor.WHITE + "/invest" + ChatColor.GRAY + " — открыть меню вложений");
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.WHITE + "/invest add <предмет> <очки> [количество]" + ChatColor.GRAY
                    + " — задать цену предмета (очки за партию)");
            sender.sendMessage(ChatColor.WHITE + "/invest remove <предмет>" + ChatColor.GRAY
                    + " — убрать предмет из ценника");
            sender.sendMessage(ChatColor.WHITE + "/invest list" + ChatColor.GRAY + " — показать ценник");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = sender.hasPermission(ADMIN_PERMISSION)
                    ? Arrays.asList("add", "remove", "list")
                    : Collections.emptyList();
            return filter(subs, args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                return new ArrayList<>();
            }
            return filter(materialNames(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            return filter(Arrays.asList("1", "5", "10", "25", "50", "100"), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            // Размер партии: чаще всего стак/полстака/поштучно.
            return filter(Arrays.asList("1", "16", "32", "64"), args[3]);
        }

        return new ArrayList<>();
    }

    /** Все известные предметы (без блоков-заглушек вроде AIR), в нижнем регистре — для подсказки. */
    private List<String> materialNames() {
        return Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(m -> m.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}