package me.blin4ik322.luckychests.modules.meteorite;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /givemeteorite <игрок> — выдаёт целевому игроку "Фрiкадэльки".
 * Требует право luckychests.admin.
 */
public class MeteoriteCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "luckychests.admin";

    private final MeteoriteModule module;

    public MeteoriteCommand(MeteoriteModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Component.text("Использование: /givemeteorite <игрок>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок \"" + args[0] + "\" сейчас не в сети.", NamedTextColor.RED));
            return true;
        }

        ItemStack meteorite = module.getMeteoriteItem();
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(meteorite);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItem(target.getLocation(), meteorite);
            sender.sendMessage(Component.text("Инвентарь " + target.getName()
                    + " был полон — \"Фрiкадэльки\" уронены под ноги.", NamedTextColor.YELLOW));
        }

        sender.sendMessage(Component.text("\"Фрiкадэльки\" выданы игроку " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Тебе выдали \"Фрiкадэльки\"! Используй с осторожностью.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return new ArrayList<>();
        }
        String lower = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
