package me.blin4ik322.luckychests.modules.shop;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /shop, /магазин, /store — открывает виртуальный магазин.
 * Алиасы задаются в plugin.yml, доступна всем игрокам.
 */
public class ShopCommand implements CommandExecutor {

    private final ShopModule shopModule;

    public ShopCommand(ShopModule shopModule) {
        this.shopModule = shopModule;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок.");
            return true;
        }

        shopModule.openShop((Player) sender);
        return true;
    }
}
