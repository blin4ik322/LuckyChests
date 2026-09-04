package me.blin4ik322.luckychests.modules.witherboost;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Слушатель увеличенного шанса выпадения черепа визера (часть модуля witherboost).
 *
 * При смерти визера-скелета (WitherSkeleton):
 *  1) убирает из списка дропа ванильный череп, если игра уже "решила" его выдать —
 *     чтобы не смешивать ванильную и нашу вероятность;
 *  2) заново бросает кубик с учётом уровня зачарования Looting на оружии убийцы
 *     и множителя WitherBoostModule.getMultiplier();
 *  3) при успехе добавляет один череп визера в дроп.
 *
 * Модуль можно включать/выключать и настраивать множитель командой /witherchance
 * (только для операторов, см. WitherChanceCommand).
 */
public class WitherSkullListener implements Listener {

    // Современные версии Bukkit API (Paper/Spigot 1.20.6+) убрали статичные поля вроде
    // Enchantment.LOOT_BONUS_MOBS / Enchantment.LOOTING — зачарования теперь достаются
    // через реестр по ключу. Такой способ работает на широком диапазоне версий сервера.
    private static final Enchantment LOOTING = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("looting"));

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!WitherBoostModule.isEnabled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.getType() != EntityType.WITHER_SKELETON) {
            return;
        }

        // Убираем ванильный череп из списка дропа, если он там уже есть — считаем шанс сами.
        List<ItemStack> drops = event.getDrops();
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (item.getType() == Material.WITHER_SKELETON_SKULL) {
                iterator.remove();
            }
        }

        int lootingLevel = 0;
        Player killer = entity.getKiller();
        if (killer != null && LOOTING != null) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            if (weapon != null) {
                lootingLevel = weapon.getEnchantmentLevel(LOOTING);
            }
        }

        double chance = WitherBoostModule.calculateChance(lootingLevel);

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            drops.add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
        }
    }
}
