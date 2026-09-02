package me.blin4ik322.luckychests;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

public class ChunkLoadListener implements Listener {

    private final LuckyChests plugin;
    private final NamespacedKey processedChunkKey;

    public ChunkLoadListener(LuckyChests plugin) {
        this.plugin = plugin;
        this.processedChunkKey = new NamespacedKey(plugin, "processed_chunk");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        PersistentDataContainer container = chunk.getPersistentDataContainer();

        // Проверяем, обрабатывался ли этот чанк ранее
        if (container.has(processedChunkKey, PersistentDataType.BYTE)) {
            return;
        }

        // Помечаем чанк как обработанный, чтобы избежать повторного спавна при перезагрузке чанка
        container.set(processedChunkKey, PersistentDataType.BYTE, (byte) 1);

        // Шанс 1/5 (20%), что в чанке появится сундук
        if (ThreadLocalRandom.current().nextInt(5) != 0) {
            return;
        }

        spawnLootBox(chunk);
    }

    private void spawnLootBox(Chunk chunk) {
        World world = chunk.getWorld();

        // Исключаем спавн в Незере и Энде (по желанию можно убрать эту проверку)
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        // Выбираем случайные координаты внутри чанка (от 0 до 15)
        int randomX = ThreadLocalRandom.current().nextInt(16);
        int randomZ = ThreadLocalRandom.current().nextInt(16);

        // Переводим в глобальные координаты мира
        int blockX = (chunk.getX() << 4) + randomX;
        int blockZ = (chunk.getZ() << 4) + randomZ;

        // Находим самый высокий блок на этой координате
        Block highestBlock = world.getHighestBlockAt(blockX, blockZ);

        // Проверяем, что под сундуком твердая поверхность (не вода, не лава и не воздух)
        if (highestBlock.getType().isAir() || highestBlock.isLiquid()) {
            return;
        }

        // Сундук ставим на блок выше поверхности
        Block chestBlock = highestBlock.getRelative(BlockFace.UP);
        if (!chestBlock.getType().isAir()) {
            return; // Если место занято, отменяем спавн
        }

        // Устанавливаем блок сундука
        chestBlock.setType(Material.CHEST);

        if (chestBlock.getState() instanceof Chest) {
            Chest chest = (Chest) chestBlock.getState();

            // Устанавливаем имя сундука
            chest.setCustomName("Loot Box");
            chest.update(); // Применяем изменения имени

            // Снова получаем состояние блока, чтобы гарантировать корректную работу с инвентарем
            Chest updatedChest = (Chest) chestBlock.getState();
            Inventory inventory = updatedChest.getInventory();

            // Генерируем случайное количество алмазов от 3 до 55
            int diamondCount = ThreadLocalRandom.current().nextInt(3, 56);

            // Раскидываем алмазы по случайным слотам сундука (всего 27 слотов)
            for (int i = 0; i < diamondCount; i++) {
                int randomSlot = ThreadLocalRandom.current().nextInt(inventory.getSize());
                ItemStack itemInSlot = inventory.getItem(randomSlot);

                if (itemInSlot == null || itemInSlot.getType() == Material.AIR) {
                    inventory.setItem(randomSlot, new ItemStack(Material.DIAMOND, 1));
                } else if (itemInSlot.getType() == Material.DIAMOND && itemInSlot.getAmount() < 64) {
                    itemInSlot.setAmount(itemInSlot.getAmount() + 1);
                } else {
                    // Если слот занят или заполнен, ищем первый свободный слот
                    int firstEmpty = inventory.firstEmpty();
                    if (firstEmpty != -1) {
                        inventory.setItem(firstEmpty, new ItemStack(Material.DIAMOND, 1));
                    }
                }
            }
        }
    }
}