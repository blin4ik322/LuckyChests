package me.blin4ik322.luckychests.modules.invest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Пустой маркер-владелец инвентаря для GUI "Что можно продавать?" (ценник).
 * Полностью read-only меню — используется только чтобы отличить его от
 * {@link InvestHolder} (сундук вложений) и любых других инвентарей:
 * {@code inventory.getHolder() instanceof InvestPriceListHolder}.
 * getInventory() намеренно не используется и возвращает null.
 */
public class InvestPriceListHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
