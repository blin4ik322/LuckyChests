package me.blin4ik322.luckychests.modules.guide;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Пустой маркер-владелец для GUI-витрины рецепта тотема (см. GuideCommand,
 * TotemRecipeListener). По аналогии с InvestHolder из модуля invest —
 * нужен только чтобы отличать это окно от прочих инвентарей в обработчиках
 * событий: {@code inventory.getHolder() instanceof TotemRecipeHolder}.
 */
public class TotemRecipeHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
