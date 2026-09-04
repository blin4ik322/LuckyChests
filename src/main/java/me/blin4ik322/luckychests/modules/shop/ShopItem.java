package me.blin4ik322.luckychests.modules.shop;

import org.bukkit.Material;

/**
 * Товары магазина "/shop". Каждый товар знает свой слот в GUI (54-слотовый
 * двойной сундук), материал, количество за одну покупку и цену в очках
 * клана. Слоты подобраны так, чтобы получился симметричный блок 5x3 по
 * центру инвентаря (ряды 2-4, столбцы 2-6, считая с нуля).
 *
 * ENEMY_RADAR_POTION — особый случай: материал POTION используется только
 * для запасной иконки; реальный ItemStack (с правильными именем/лором)
 * строится в ShopModule/ShopListener через EnemyPotionModule.
 */
public enum ShopItem {

    ELYTRA(20, Material.ELYTRA, 1, 5000, "§5Элитры"),
    ENDER_PEARL(21, Material.ENDER_PEARL, 1, 150, "§bЭндер-жемчуг"),
    FIREWORK_ROCKET(22, Material.FIREWORK_ROCKET, 16, 200, "§dФейерверки"),
    ENCHANTED_GOLDEN_APPLE(23, Material.ENCHANTED_GOLDEN_APPLE, 1, 2500, "§eЗачарованное золотое яблоко"),
    SADDLE(24, Material.SADDLE, 1, 150, "§6Седло"),

    STEAK(29, Material.COOKED_BEEF, 8, 60, "§cСтейк"),
    GOLDEN_APPLE(30, Material.GOLDEN_APPLE, 1, 50, "§6Золотое яблоко"),
    ENEMY_RADAR_POTION(31, Material.POTION, 1, 200, "§cЗелье Чутья Врагов"),
    OAK_LOG(32, Material.OAK_LOG, 16, 80, "§2Дубовое бревно"),
    BOOKSHELF(33, Material.BOOKSHELF, 3, 120, "§fКнижная полка"),

    WATER_BUCKET(38, Material.WATER_BUCKET, 1, 50, "§9Ведро воды"),
    IRON_INGOT(39, Material.IRON_INGOT, 1, 10, "§fЖелезный слиток"),
    DIAMOND(40, Material.DIAMOND, 1, 80, "§bАлмаз"),
    LAPIS_LAZULI(41, Material.LAPIS_LAZULI, 16, 100, "§9Лазурит"),
    LAVA_BUCKET(42, Material.LAVA_BUCKET, 1, 70, "§6Ведро лавы"),

    METEORITE_ITEM(49, Material.POPPED_CHORUS_FRUIT, 1, 300, "§4Фрiкадэльки");

    private final int slot;
    private final Material material;
    private final int amount;
    private final int price;
    private final String displayName;

    ShopItem(int slot, Material material, int amount, int price, String displayName) {
        this.slot = slot;
        this.material = material;
        this.amount = amount;
        this.price = price;
        this.displayName = displayName;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public int getPrice() {
        return price;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ShopItem bySlot(int slot) {
        for (ShopItem item : values()) {
            if (item.slot == slot) {
                return item;
            }
        }
        return null;
    }
}