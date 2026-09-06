package me.blin4ik322.luckychests.modules.shop;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Обрабатывает клики по GUI магазина: отменяет любые попытки забрать
 * предметы из меню и проводит покупку по клику на товар.
 */
public class ShopListener implements Listener {

    private final JavaPlugin plugin;
    private final ShopModule shopModule;
    private final ClanManager clanManager;

    public ShopListener(JavaPlugin plugin, ShopModule shopModule) {
        this.plugin = plugin;
        this.shopModule = shopModule;
        this.clanManager = shopModule.getClanManager();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) {
            return;
        }

        // Запрещаем вообще любые манипуляции внутри GUI магазина:
        // забрать предмет, шифт-клик, перетаскивание и т.д.
        event.setCancelled(true);

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !(clickedInventory.getHolder() instanceof ShopHolder)) {
            // Клик пришёлся на инвентарь игрока (нижняя часть экрана), а не
            // на сам магазин — просто игнорируем.
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ShopItem shopItem = resolveShopItem(clicked);
        if (shopItem == null) {
            // Клик по фоновому стеклу или по чему-то без нашего PDC-ключа.
            return;
        }

        handlePurchase(player, shopItem);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // При закрытии магазина ничего не происходит — по ТЗ.
    }

    private ShopItem resolveShopItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(shopModule.getKeyItemId(), PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return ShopItem.valueOf(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void handlePurchase(Player player, ShopItem shopItem) {
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы больше не состоите в клане.");
            player.closeInventory();
            return;
        }

        int price = shopItem.getPrice();
        long score = clan.getScore();

        if (score < price) {
            player.sendMessage(ChatColor.RED + "Не хватает очков клана! Баланс: "
                    + ChatColor.YELLOW + score + ChatColor.RED + " очков.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // ENEMY_RADAR_POTION — особый случай: выдаём не «сырой» ItemStack по
        // материалу/количеству, а настоящее Зелье Чутья Врагов из
        // EnemyPotionModule, со своей PDC-меткой и Adventure-лором — иначе
        // предмет из магазина выглядел бы как зелье, но не работал бы как радар.
        ItemStack purchase = shopItem == ShopItem.ENEMY_RADAR_POTION
                ? shopModule.getEnemyPotionModule().getRadarPotion()
                : shopItem == ShopItem.METEORITE_ITEM
                ? shopModule.getMeteoriteModule().getMeteoriteItem()
                : new ItemStack(shopItem.getMaterial(), shopItem.getAmount());

        // Предварительная проверка места — не трогаем реальный инвентарь
        // игрока, пока не убедимся, что предмет точно поместится.
        if (!hasSpaceFor(player, purchase)) {
            player.sendMessage(ChatColor.RED + "Ваш инвентарь переполнен!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }

        // Место есть — списываем очки и выдаём предмет.
        clanManager.addScore(clan, -price);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(purchase);

        if (!leftover.isEmpty()) {
            // Подстраховка на случай, если инвентарь игрока успел
            // измениться между проверкой места и фактической выдачей —
            // не теряем предмет, роняем его под ноги.
            for (ItemStack notAdded : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), notAdded);
            }
        }

        player.sendMessage(ChatColor.GREEN + "Вы успешно купили " + shopItem.getDisplayName()
                + ChatColor.GREEN + " за " + ChatColor.YELLOW + price + ChatColor.GREEN + " очков!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        // Переоткрываем GUI следующим тиком, чтобы в заголовке отобразился
        // обновлённый баланс очков.
        Bukkit.getScheduler().runTask(plugin, () -> shopModule.openShop(player));
    }

    /**
     * Проверяет, поместится ли itemStack в инвентарь игрока, не трогая
     * реальный инвентарь: симулирует addItem() на копии основного
     * инвентаря (36 слотов хранения — ровно то, с чем работает addItem()).
     */
    private boolean hasSpaceFor(Player player, ItemStack itemStack) {
        ItemStack[] original = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] == null ? null : original[i].clone();
        }

        Inventory temp = Bukkit.createInventory(null, 36);
        temp.setContents(copy);

        Map<Integer, ItemStack> leftover = temp.addItem(itemStack.clone());
        return leftover.isEmpty();
    }
}