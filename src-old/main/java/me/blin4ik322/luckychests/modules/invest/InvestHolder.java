package me.blin4ik322.luckychests.modules.invest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Пустой маркер-владелец инвентаря. Используется только для того, чтобы
 * в обработчиках событий отличить "сундук вложений" от любого другого GUI:
 * {@code inventory.getHolder() instanceof InvestHolder}.
 * getInventory() намеренно не используется и возвращает null.
 */
public class InvestHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}