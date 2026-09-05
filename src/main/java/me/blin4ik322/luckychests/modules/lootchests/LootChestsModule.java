package me.blin4ik322.luckychests.modules.lootchests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Модуль "Хранилища" (/lootchests). Оператор задаёт набор возможных координат
 * и возможный дроп; по /lootchests start в случайных из этих координат
 * начинают спавниться сундук/бочка (случайно) с рандомным лутом по дроп-таблице —
 * одновременно ровно столько, сколько задано в /lootchests max.
 *
 * Хранилище "живёт" бесконечно, пока его не откроют: как только игрок открыл
 * его первый раз — над ним появляется летающий текст обратного отсчёта
 * (время из /lootchests timer set). По истечении — блок и оставшийся лут
 * исчезают, текст пропадает, а если модуль всё ещё "запущен" (/start), на его
 * месте (по факту — в случайном свободном месте из списка) спавнится новое
 * хранилище. После /lootchests stop новые хранилища не спавнятся, но уже
 * стоящие продолжают ждать открытия/истечения как обычно.
 */
public class LootChestsModule {

    private final JavaPlugin plugin;
    private final File configFile;
    private YamlConfiguration config;

    private final List<Location> locations = new ArrayList<>();
    private final Map<String, LootDrop> drops = new LinkedHashMap<>(); // ключ — имя материала
    private final Map<String, ActiveLootChest> activeChests = new HashMap<>(); // ключ — locationKey

    // ---------------------------------------------------------------
    // Настройка разброса предметов по слотам хранилища.
    // MIN_SPLIT_PIECES — минимум кусков на которые делится каждый предмет.
    // MAX_SPLIT_PIECES — максимум кусков (реальное число выбирается случайно в этом диапазоне).
    // Например: MIN=3, MAX=6 → 48 алмазов разобьётся на 3-6 кучек примерно по 8-16 штук.
    // ---------------------------------------------------------------
    private static final int MIN_SPLIT_PIECES = 3;
    private static final int MAX_SPLIT_PIECES = 6;

    private int maxActive = 3;
    private long timerSeconds = 60L;
    private boolean running = false;

    public LootChestsModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "lootchests.yml");
        load();
    }

    // ---------------------------------------------------------------
    // Персистентность (lootchests.yml)
    // ---------------------------------------------------------------

    private void load() {
        if (!configFile.exists()) {
            config = new YamlConfiguration();
            save();
            return;
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        maxActive = Math.max(0, config.getInt("max", 3));
        timerSeconds = Math.max(1L, config.getLong("timer-seconds", 60L));
        running = config.getBoolean("running", false);

        locations.clear();
        for (String raw : config.getStringList("locations")) {
            Location loc = parseLocationKey(raw);
            if (loc != null) {
                locations.add(loc);
            }
        }

        drops.clear();
        ConfigurationSection dropsSection = config.getConfigurationSection("drops");
        if (dropsSection != null) {
            for (String key : dropsSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    continue;
                }
                int min = dropsSection.getInt(key + ".min", 1);
                int max = dropsSection.getInt(key + ".max", 1);
                double chance = dropsSection.getDouble(key + ".chance", 100.0);
                drops.put(material.name(), new LootDrop(material, min, max, chance));
            }
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("max", maxActive);
        config.set("timer-seconds", timerSeconds);
        config.set("running", running);

        List<String> locStrings = new ArrayList<>();
        for (Location loc : locations) {
            locStrings.add(locationKey(loc));
        }
        config.set("locations", locStrings);

        config.set("drops", null);
        for (LootDrop drop : drops.values()) {
            String path = "drops." + drop.getName();
            config.set(path + ".min", drop.getMin());
            config.set(path + ".max", drop.getMax());
            config.set(path + ".chance", drop.getChance());
        }

        try {
            File parent = plugin.getDataFolder();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[LootChests] Не удалось сохранить lootchests.yml: " + e.getMessage());
        }
    }

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location parseLocationKey(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Location toBlockLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    // ---------------------------------------------------------------
    // Управление координатами
    // ---------------------------------------------------------------

    public boolean addLocation(Location loc) {
        Location blockLoc = toBlockLocation(loc);
        for (Location existing : locations) {
            if (sameBlock(existing, blockLoc)) {
                return false;
            }
        }
        locations.add(blockLoc);
        save();
        return true;
    }

    public boolean removeLocation(Location loc) {
        Location blockLoc = toBlockLocation(loc);
        Iterator<Location> it = locations.iterator();
        while (it.hasNext()) {
            if (sameBlock(it.next(), blockLoc)) {
                it.remove();
                save();
                return true;
            }
        }
        return false;
    }

    public List<Location> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    // ---------------------------------------------------------------
    // Настройки max / timer
    // ---------------------------------------------------------------

    public void setMax(int max) {
        this.maxActive = Math.max(0, max);
        save();
        if (running) {
            fillActiveChests();
        }
    }

    public int getMax() {
        return maxActive;
    }

    public void setTimerSeconds(long seconds) {
        this.timerSeconds = Math.max(1L, seconds);
        save();
    }

    public long getTimerSeconds() {
        return timerSeconds;
    }

    // ---------------------------------------------------------------
    // Дроп-таблица
    // ---------------------------------------------------------------

    public void addDrop(LootDrop drop) {
        drops.put(drop.getName(), drop);
        save();
    }

    public boolean removeDrop(String materialName) {
        boolean removed = drops.remove(materialName.toUpperCase()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Collection<LootDrop> getDrops() {
        return Collections.unmodifiableCollection(drops.values());
    }

    // ---------------------------------------------------------------
    // Старт/стоп спавна
    // ---------------------------------------------------------------

    public void start() {
        if (running) {
            return;
        }
        running = true;
        save();
        fillActiveChests();
    }

    public void stop() {
        running = false;
        save();
        // Уже стоящие хранилища не трогаем — они продолжают жить своим чередом.
    }

    public boolean isRunning() {
        return running;
    }

    public int getActiveCount() {
        return activeChests.size();
    }

    /**
     * Проверяет, является ли блок по данной локации активным хранилищем.
     * Используется в LootChestsListener для защиты от разрушения.
     */
    public boolean isActiveChest(Location loc) {
        return activeChests.containsKey(locationKey(toBlockLocation(loc)));
    }

    /**
     * Проверяет, зарегистрирована ли локация в списке мест для хранилищ.
     * Используется в LootChestsListener чтобы запретить ставить туда блоки вручную.
     */
    public boolean isRegisteredLocation(Location loc) {
        Location blockLoc = toBlockLocation(loc);
        for (Location registered : locations) {
            if (sameBlock(registered, blockLoc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Добавляет случайные хранилища на свободных координатах, пока их не станет maxActive.
     * Каждое следующее хранилище спавнится через 1 тик после предыдущего — это гарантирует,
     * что к моменту выбора следующего места предыдущий блок уже зарегистрирован в activeChests
     * и никогда не будет выбран повторно.
     */
    private void fillActiveChests() {
        if (!running) {
            return;
        }
        int needed = maxActive - activeChests.size();
        if (needed <= 0) {
            return;
        }
        scheduleNextSpawn(needed, 0L);
    }

    private void scheduleNextSpawn(int remaining, long delayTicks) {
        if (remaining <= 0 || !running) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) {
                return;
            }
            boolean spawned = spawnRandomChest();
            if (spawned) {
                scheduleNextSpawn(remaining - 1, 1L);
            }
        }, delayTicks);
    }

    private boolean spawnRandomChest() {
        List<Location> free = new ArrayList<>();
        for (Location loc : locations) {
            // Пропускаем если уже числится в активных
            if (activeChests.containsKey(locationKey(loc))) {
                continue;
            }
            // Пропускаем если в мире на этом месте уже стоит сундук/бочка
            Material blockType = loc.getBlock().getType();
            if (blockType == Material.CHEST || blockType == Material.BARREL
                    || blockType == Material.TRAPPED_CHEST) {
                continue;
            }
            free.add(loc);
        }
        if (free.isEmpty()) {
            return false;
        }
        Location loc = free.get(ThreadLocalRandom.current().nextInt(free.size()));
        spawnChestAt(loc);
        return true;
    }

    private void spawnChestAt(Location loc) {
        Block block = loc.getBlock();
        Material material = ThreadLocalRandom.current().nextBoolean() ? Material.CHEST : Material.BARREL;
        block.setType(material);

        ActiveLootChest chest = new ActiveLootChest(toBlockLocation(loc));
        activeChests.put(locationKey(loc), chest);

        // Заполняем лут на следующем тике — к этому моменту TileEntity блока
        // гарантированно инициализирован сервером и инвентарь не будет сброшен.
        Bukkit.getScheduler().runTask(plugin, () -> {
            BlockState freshState = block.getState();
            if (freshState instanceof Container container) {
                fillLoot(container.getInventory());
            }
        });
    }

    private void fillLoot(Inventory inventory) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int size = inventory.getSize();

        // Шаг 1: определяем какие предметы выпали и их количество (без дробления)
        List<int[]> pending = new ArrayList<>(); // [material ordinal не нужен, храним drop + total]
        List<LootDrop> pendingDrops = new ArrayList<>();
        for (LootDrop drop : drops.values()) {
            if (random.nextDouble(100.0) >= drop.getChance()) {
                continue;
            }
            int total = drop.getMax() > drop.getMin()
                    ? drop.getMin() + random.nextInt(drop.getMax() - drop.getMin() + 1)
                    : drop.getMin();
            if (total <= 0) {
                continue;
            }
            pendingDrops.add(drop);
            pending.add(new int[]{total});
        }

        if (pendingDrops.isEmpty()) {
            return;
        }

        // Шаг 2: подбираем количество кусков так, чтобы суммарно не превысить size слотов.
        // Начинаем с MAX_SPLIT_PIECES и уменьшаем пока сумма не влезет.
        int effectiveMax = MAX_SPLIT_PIECES;
        while (effectiveMax > 1) {
            int totalPieces = 0;
            for (int[] entry : pending) {
                int total = entry[0];
                int minP = Math.min(MIN_SPLIT_PIECES, total);
                int maxP = Math.min(effectiveMax, total);
                totalPieces += Math.max(minP, maxP); // worst case = maxP кусков
            }
            if (totalPieces <= size) {
                break;
            }
            effectiveMax--;
        }

        // Шаг 3: дробим каждый предмет на куски с учётом подобранного effectiveMax
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < pendingDrops.size(); i++) {
            LootDrop drop = pendingDrops.get(i);
            int total = pending.get(i)[0];
            int pieces;
            if (total == 1 || effectiveMax == 1) {
                pieces = 1;
            } else {
                int minP = Math.min(MIN_SPLIT_PIECES, total);
                int maxP = Math.min(effectiveMax, total);
                pieces = minP >= maxP ? minP : minP + random.nextInt(maxP - minP + 1);
            }
            splitIntoChunks(total, pieces, random).forEach(count ->
                    items.add(new ItemStack(drop.getMaterial(), count))
            );
        }

        // Шаг 4: перемешиваем и раскладываем по случайным слотам
        Collections.shuffle(items);

        List<Integer> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);

        int slotIndex = 0;
        for (ItemStack item : items) {
            if (slotIndex >= slots.size()) {
                break;
            }
            inventory.setItem(slots.get(slotIndex), item);
            slotIndex++;
        }
    }

    /**
     * Разбивает число total на pieces случайных частей (каждая >= 1).
     * Например, splitIntoChunks(20, 4) → [3, 7, 5, 5].
     */
    private List<Integer> splitIntoChunks(int total, int pieces, ThreadLocalRandom random) {
        List<Integer> result = new ArrayList<>(pieces);
        int remaining = total;
        for (int i = 0; i < pieces - 1; i++) {
            // Оставляем минимум по 1 на каждый оставшийся кусок
            int max = remaining - (pieces - 1 - i);
            int chunk = max <= 1 ? 1 : 1 + random.nextInt(max - 1);
            result.add(chunk);
            remaining -= chunk;
        }
        result.add(remaining); // последний кусок — остаток
        return result;
    }

    // ---------------------------------------------------------------
    // Открытие хранилища и обратный отсчёт
    // ---------------------------------------------------------------

    /** Вызывается из LootChestsListener при открытии инвентаря сундука/бочки. */
    public void onChestOpened(Location loc) {
        ActiveLootChest chest = activeChests.get(locationKey(loc));
        if (chest == null || chest.isCountdownStarted()) {
            return;
        }
        chest.setCountdownStarted(true);
        startCountdown(chest);
    }

    private void startCountdown(ActiveLootChest chest) {
        Location loc = chest.getLocation();
        Location textLoc = loc.clone().add(0.5, 1.5, 0.5);

        TextDisplay display = loc.getWorld().spawn(textLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
        });
        chest.setTextDisplay(display);
        updateDisplayText(display, timerSeconds);

        BukkitTask task = new BukkitRunnable() {
            long remaining = timerSeconds;

            @Override
            public void run() {
                remaining--;
                if (remaining < 0) {
                    expireChest(chest);
                    cancel();
                    return;
                }
                updateDisplayText(display, remaining);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        chest.setCountdownTask(task);
    }

    private void updateDisplayText(TextDisplay display, long secondsLeft) {
        String time = String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60);
        Component text = Component.text("Хранилище исчезнет через ", NamedTextColor.GRAY)
                .append(Component.text(time, NamedTextColor.RED, TextDecoration.BOLD));
        display.text(text);
    }

    private void expireChest(ActiveLootChest chest) {
        Location loc = chest.getLocation();
        Block block = loc.getBlock();
        BlockState state = block.getState();
        if (state instanceof Container container) {
            container.getInventory().clear();
        }
        block.setType(Material.AIR);

        if (chest.getTextDisplay() != null) {
            chest.getTextDisplay().remove();
        }

        activeChests.remove(locationKey(loc));

        if (running) {
            spawnRandomChest();
        }
    }

    /** Вызывается из LuckyChests#onDisable() — просто отменяет активные задачи отсчёта. */
    public void shutdown() {
        for (ActiveLootChest chest : activeChests.values()) {
            if (chest.getCountdownTask() != null) {
                chest.getCountdownTask().cancel();
            }
        }
    }
}