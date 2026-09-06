package me.blin4ik322.luckychests.modules.shop;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import me.blin4ik322.luckychests.modules.enemypotion.EnemyPotionModule;
import me.blin4ik322.luckychests.modules.meteorite.MeteoriteModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Модуль "Магазин" (/shop) — виртуальный магазин на 54 слота, где игроки
 * тратят очки своего клана на предметы.
 */
public class ShopModule {

    private final ClanManager clanManager;
    private final EnemyPotionModule enemyPotionModule;
    private final MeteoriteModule meteoriteModule;

    private final NamespacedKey keyPrice;
    private final NamespacedKey keyAmount;
    private final NamespacedKey keyItemId;

    public ShopModule(JavaPlugin plugin, ClanManager clanManager, EnemyPotionModule enemyPotionModule,
                      MeteoriteModule meteoriteModule) {
        if (clanManager == null) {
            throw new IllegalArgumentException(
                    "[Shop] ClanManager == null. Убедитесь, что ShopModule создаётся "
                            + "ПОСЛЕ инициализации ClanManager в onEnable() и что туда "
                            + "передан тот же самый экземпляр ClanManager.");
        }
        if (enemyPotionModule == null) {
            throw new IllegalArgumentException(
                    "[Shop] EnemyPotionModule == null. Убедитесь, что ShopModule создаётся "
                            + "ПОСЛЕ инициализации EnemyPotionModule в onEnable() и что туда "
                            + "передан тот же самый экземпляр EnemyPotionModule (нужен для "
                            + "выдачи настоящего Зелья Чутья Врагов при покупке в магазине).");
        }
        if (meteoriteModule == null) {
            throw new IllegalArgumentException(
                    "[Shop] MeteoriteModule == null. Убедитесь, что ShopModule создаётся "
                            + "ПОСЛЕ инициализации MeteoriteModule в onEnable() и что туда "
                            + "передан тот же самый экземпляр MeteoriteModule (нужен для "
                            + "выдачи настоящих \"Фрiкадэлек\" при покупке в магазине).");
        }
        this.clanManager = clanManager;
        this.enemyPotionModule = enemyPotionModule;
        this.meteoriteModule = meteoriteModule;
        this.keyPrice = new NamespacedKey(plugin, "shop_price");
        this.keyAmount = new NamespacedKey(plugin, "shop_amount");
        this.keyItemId = new NamespacedKey(plugin, "shop_item_id");
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    /**
     * Используется ShopListener для выдачи настоящего "Зелья Чутья Врагов"
     * (со своей PDC-меткой и Adventure-лором) при покупке ShopItem.ENEMY_RADAR_POTION,
     * вместо обычного ItemStack по материалу/количеству.
     */
    public EnemyPotionModule getEnemyPotionModule() {
        return enemyPotionModule;
    }

    /**
     * Используется ShopListener для выдачи настоящих "Фрiкадэлек" (со своей
     * PDC-меткой и glint-эффектом) при покупке ShopItem.METEORITE_ITEM.
     */
    public MeteoriteModule getMeteoriteModule() {
        return meteoriteModule;
    }

    public NamespacedKey getKeyPrice() {
        return keyPrice;
    }

    public NamespacedKey getKeyAmount() {
        return keyAmount;
    }

    public NamespacedKey getKeyItemId() {
        return keyItemId;
    }

    /**
     * Открывает магазин игроку. Если игрок не состоит в клане — магазин
     * не открывается, вместо этого игрок получает сообщение в чат.
     */
    public void openShop(Player player) {
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatColor.RED + "Вы не состоите в клане — магазин недоступен.");
            return;
        }

        player.openInventory(buildInventory(clan));
    }

    /**
     * Собирает GUI магазина: фон из серого стекла + товары, с актуальным
     * балансом очков клана прямо в заголовке.
     */
    private Inventory buildInventory(Clan clan) {
        long score = clan.getScore();
        String title = ChatColor.DARK_GRAY + "Магазин | Очки: " + ChatColor.YELLOW + score;

        ShopHolder holder = new ShopHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inventory);

        ItemStack glass = createGlassPane();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, glass);
        }

        for (ShopItem shopItem : ShopItem.values()) {
            inventory.setItem(shopItem.getSlot(), createShopIcon(shopItem));
        }

        return inventory;
    }

    private ItemStack createGlassPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createShopIcon(ShopItem shopItem) {
        // ENEMY_RADAR_POTION — особый случай: берём за основу настоящий предмет
        // из EnemyPotionModule (с его именем/лором), а не обычный ItemStack по
        // материалу — так иконка в магазине выглядит так же, как и предмет,
        // который игрок реально получит при покупке.
        ItemStack icon;
        if (shopItem == ShopItem.ENEMY_RADAR_POTION) {
            icon = enemyPotionModule.getRadarPotion();
        } else if (shopItem == ShopItem.METEORITE_ITEM) {
            icon = meteoriteModule.getMeteoriteItem();
        } else {
            icon = new ItemStack(shopItem.getMaterial(), shopItem.getAmount());
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(shopItem.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Количество: " + ChatColor.WHITE + shopItem.getAmount() + " шт.");
            lore.add(ChatColor.GRAY + "Цена: " + ChatColor.YELLOW + shopItem.getPrice() + " очков");
            lore.add("");
            lore.add(ChatColor.GREEN + "Нажмите, чтобы купить");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(keyPrice, PersistentDataType.INTEGER, shopItem.getPrice());
            meta.getPersistentDataContainer().set(keyAmount, PersistentDataType.INTEGER, shopItem.getAmount());
            meta.getPersistentDataContainer().set(keyItemId, PersistentDataType.STRING, shopItem.name());

            icon.setItemMeta(meta);
        }
        return icon;
    }
}