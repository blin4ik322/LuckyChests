package me.blin4ik322.luckychests.modules.guide;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * GUI-витрина рецепта тотема (TotemRecipeHolder) — окно только для
 * просмотра: предметы в ней декоративные копии, забрать их нельзя.
 * Любой клик/драг по этому окну просто отменяется.
 */
public class TotemRecipeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TotemRecipeHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TotemRecipeHolder) {
            event.setCancelled(true);
        }
    }
}
