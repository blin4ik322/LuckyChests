package me.blin4ik322.luckychests.modules.invest;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Модуль "Вложения" — виртуальный двойной сундук (/invest, /вложить).
 *
 * Игрок кладёт в него предметы; название сундука на лету показывает сумму
 * очков за уже положенные предметы из ценника (ценник задаётся операторами
 * командой /invest add {предмет} {очки}). Последний, 54-й слот всегда занят
 * кнопкой-подтверждением (лаймовый краситель, жирная зелёная надпись
 * "Подтвердить") — его нельзя вынуть или заменить. Слева снизу (45-й слот)
 * — кнопка "Что можно продавать?" (оранжевый краситель): открывает
 * отдельный read-only сундук-ценник со всеми предметами из ценника и их
 * ценой в описании, а в нём на той же позиции — кнопка "Назад" обратно
 * к вложениям.
 *
 * Два сценария:
 *  - Игрок нажимает на кнопку "Подтвердить" — предметы, которые ЕСТЬ в
 *    ценнике, продаются (очки идут клану), а всё остальное (предметы не из
 *    ценника) сразу возвращается игроку. Меню НЕ закрывается — счётчик в
 *    названии сбрасывается на 0, и можно сразу докладывать следующую партию
 *    и жать "Подтвердить" ещё раз, не открывая сундук заново.
 *  - Игрок просто закрывает сундук (крестик/Esc/выход из мира и т.д.) — ничего
 *    не продаётся, ВСЁ содержимое (включая предметы из ценника) возвращается
 *    игроку. Если в инвентаре нет места — предметы выбрасываются под ноги
 *    игрока (dropItemNaturally), а не пропадают.
 *
 * Ценник предметов хранится в invest.yml рядом с остальными данными плагина.
 * Начисление очков идёт через ClanManager.getClanByPlayer(UUID) +
 * ClanManager.addScore(Clan, long).
 */
public class InvestModule {

    private static final int INVENTORY_SIZE = 54; // двойной сундук (6 рядов по 9)

    /** Последний слот сундука — всегда кнопка "Подтвердить", в расчёт очков и продажу не входит. */
    public static final int CONFIRM_SLOT = INVENTORY_SIZE - 1;

    /** Слева снизу в сундуке вложений — кнопка "Что можно продавать?" (открывает ценник). */
    public static final int SELL_INFO_SLOT = INVENTORY_SIZE - 9;

    /** В GUI ценника — та же позиция (слева снизу) занята кнопкой "Назад". */
    public static final int PRICE_LIST_BACK_SLOT = INVENTORY_SIZE - 9;

    private final JavaPlugin plugin;
    private final ClanManager clanManager;
    private final Map<Material, Integer> itemValues = new EnumMap<>(Material.class);

    private File file;
    private FileConfiguration config;

    public InvestModule(JavaPlugin plugin, ClanManager clanManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
    }

    public void enable() {
        loadConfig();

        InvestCommand command = new InvestCommand(this);
        if (plugin.getCommand("invest") != null) {
            plugin.getCommand("invest").setExecutor(command);
            plugin.getCommand("invest").setTabCompleter(command);
        } else {
            plugin.getLogger().warning("[Invest] команда 'invest' не объявлена в plugin.yml — добавьте её.");
        }

        Bukkit.getPluginManager().registerEvents(new InvestListener(this), plugin);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    // ---------------------------------------------------------------
    // Меню
    // ---------------------------------------------------------------

    public void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new InvestHolder(), INVENTORY_SIZE, buildTitle(0));
        inventory.setItem(CONFIRM_SLOT, buildConfirmButton());
        inventory.setItem(SELL_INFO_SLOT, buildSellInfoButton());
        player.openInventory(inventory);
    }

    /** Название сундука, отражающее текущую сумму вложенных очков. */
    public String buildTitle(int points) {
        return ChatColor.DARK_GREEN + "Вложения: " + ChatColor.YELLOW + points
                + ChatColor.DARK_GREEN + " очков";
    }

    private ItemStack buildConfirmButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Подтвердить");
            meta.setLore(Collections.singletonList(
                    ChatColor.GRAY + "Продать предметы из ценника и вернуть остальное"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildSellInfoButton() {
        ItemStack item = new ItemStack(Material.ORANGE_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Что можно продавать?");
            meta.setLore(Collections.singletonList(
                    ChatColor.GRAY + "Нажми, чтобы посмотреть ценник"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Открывает игроку read-only сундук-ценник: все предметы из
     * {@link #itemValues} с их ценой в описании, и кнопка "Назад" (слева
     * снизу), возвращающая в сундук вложений.
     */
    public void openPriceList(Player player) {
        Inventory inventory = Bukkit.createInventory(new InvestPriceListHolder(), INVENTORY_SIZE,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Что можно продавать?");

        ItemStack glass = buildBackgroundGlass();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
        }

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : itemValues.entrySet()) {
            if (slot == PRICE_LIST_BACK_SLOT) {
                slot++; // не занимаем слот кнопки "Назад" товарами
            }
            if (slot >= INVENTORY_SIZE) {
                break; // ценник больше не помещается в один сундук
            }
            inventory.setItem(slot, buildPriceIcon(entry.getKey(), entry.getValue()));
            slot++;
        }

        inventory.setItem(PRICE_LIST_BACK_SLOT, buildBackButton());
        player.openInventory(inventory);
    }

    private ItemStack buildBackgroundGlass() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack buildPriceIcon(Material material, int price) {
        ItemStack icon = new ItemStack(material, 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + material.name());
            meta.setLore(Collections.singletonList(
                    ChatColor.GRAY + "Цена: " + ChatColor.YELLOW + price + ChatColor.GRAY + " очков/шт."));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Назад");
            meta.setLore(Collections.singletonList(
                    ChatColor.GRAY + "Вернуться к сундуку вложений"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Считает сумму очков по предметам из ценника, лежащим в инвентаре прямо сейчас (кнопка не учитывается). */
    public int calculatePoints(Inventory inventory) {
        int total = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i == CONFIRM_SLOT || i == SELL_INFO_SLOT) {
                continue;
            }
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            Integer value = itemValues.get(item.getType());
            if (value == null || value <= 0) {
                continue;
            }
            total += value * item.getAmount();
        }
        return total;
    }

    /**
     * Вызывается при клике по кнопке "Подтвердить". Продаёт предметы из ценника,
     * сразу возвращает игроку всё остальное. Меню НЕ закрывается — игрок может
     * докладывать предметы и подтверждать сколько угодно раз подряд, не открывая
     * сундук заново.
     */
    public void confirmInvestment(Player player, Inventory inventory) {
        int points = 0;
        ItemStack[] contents = inventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            if (i == CONFIRM_SLOT || i == SELL_INFO_SLOT) {
                continue;
            }
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            Integer value = itemValues.get(item.getType());
            if (value != null && value > 0) {
                points += value * item.getAmount(); // предмет из ценника — продан
            } else {
                giveOrDrop(player, item); // не из ценника — возвращаем сразу
            }
            inventory.setItem(i, null);
        }

        creditPoints(player, points);
    }

    /**
     * Вызывается при обычном закрытии сундука (не через кнопку "Подтвердить").
     * Ничего не продаётся — всё содержимое (кроме самой кнопки) возвращается игроку.
     */
    public void returnItems(Player player, Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i == CONFIRM_SLOT || i == SELL_INFO_SLOT) {
                continue;
            }
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            giveOrDrop(player, item);
            inventory.setItem(i, null);
        }
    }

    /** Кладёт предмет в инвентарь игрока, а если места нет — роняет под ноги. */
    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private void creditPoints(Player player, int points) {
        if (points <= 0) {
            player.sendMessage(ChatColor.GRAY + "Вложение пустое — очки не начислены.");
            return;
        }

        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вложение засчитано (" + points
                    + " очков), но ты не состоишь в клане — очки некому начислить.");
            return;
        }

        clanManager.addScore(clan, points);
        player.sendMessage(ChatColor.GREEN + "Вложено! Клан получил "
                + ChatColor.YELLOW + points + ChatColor.GREEN + " очков.");
    }

    // ---------------------------------------------------------------
    // Ценник предметов (операторская настройка)
    // ---------------------------------------------------------------

    public void setItemValue(Material material, int points) {
        if (points <= 0) {
            itemValues.remove(material);
        } else {
            itemValues.put(material, points);
        }
        saveConfig();
    }

    public boolean removeItemValue(Material material) {
        boolean removed = itemValues.remove(material) != null;
        if (removed) {
            saveConfig();
        }
        return removed;
    }

    public Integer getItemValue(Material material) {
        return itemValues.get(material);
    }

    public Map<Material, Integer> getItemValues() {
        return itemValues;
    }

    private void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "invest.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[Invest] Не удалось создать invest.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        itemValues.clear();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("[Invest] Неизвестный материал в invest.yml: " + key);
                    continue;
                }
                itemValues.put(material, section.getInt(key));
            }
        }
    }

    private void saveConfig() {
        config.set("items", null); // чистим секцию перед перезаписью, чтобы не оставались удалённые ключи
        for (Map.Entry<Material, Integer> entry : itemValues.entrySet()) {
            config.set("items." + entry.getKey().name(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[Invest] Не удалось сохранить invest.yml: " + e.getMessage());
        }
    }
}