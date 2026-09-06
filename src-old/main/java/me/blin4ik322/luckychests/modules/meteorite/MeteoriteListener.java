package me.blin4ik322.luckychests.modules.meteorite;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Обрабатывает ПКМ с предметом "Фрiкадэльки" в руке — списывает один предмет
 * из стака и запускает метеоритный дождь в точке использования.
 */
public class MeteoriteListener implements Listener {

    private final MeteoriteModule module;

    public MeteoriteListener(MeteoriteModule module) {
        this.module = module;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            // Игнорируем офф-хенд, чтобы событие не сработало дважды за один клик.
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!module.isMeteoriteItem(item)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        module.triggerMeteorShower(player.getLocation());
    }
}
