package me.blin4ik322.luckychests.modules.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Кастомный holder для GUI магазина. Используется вместо сверки по
 * заголовку/размеру инвентаря, чтобы игрок не мог обойти проверки,
 * переименовав обычный сундук в клиенте.
 */
public class ShopHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
