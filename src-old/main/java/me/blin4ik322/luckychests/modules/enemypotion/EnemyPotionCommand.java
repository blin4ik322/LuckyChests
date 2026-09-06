package me.blin4ik322.luckychests.modules.enemypotion;

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
import java.util.stream.Collectors;

/**
 * /giveradarpotion <игрок> — выдаёт целевому игроку "Зелье Чутья Врагов".
 * Требует право luckychests.admin.
 */
public class EnemyPotionCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "luckychests.admin";

    private final EnemyPotionModule module;

    public EnemyPotionCommand(EnemyPotionModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Component.text("Использование: /giveradarpotion <игрок>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок \"" + args[0] + "\" сейчас не в сети.", NamedTextColor.RED));
            return true;
        }

        ItemStack potion = module.getRadarPotion();
        java.util.Map<Integer, ItemStack> leftover = target.getInventory().addItem(potion);
        if (!leftover.isEmpty()) {
            target.getWorld().dropItem(target.getLocation(), potion);
            Component fullMsg = Component.text("Инвентарь " + target.getName()
                    + " был полон — зелье уронено под ноги.", NamedTextColor.YELLOW);
            sender.sendMessage(fullMsg);
        }

        Component successMsg = Component.text("Зелье Чутья Врагов выдано игроку " + target.getName() + ".", NamedTextColor.GREEN);
        sender.sendMessage(successMsg);

        Component notifyMsg = Component.text("Тебе выдали Зелье Чутья Врагов!", NamedTextColor.GREEN);
        target.sendMessage(notifyMsg);
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