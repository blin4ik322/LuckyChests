package me.blin4ik322.luckychests.modules.shop;

import org.bukkit.Material;

/**
 * Товары магазина "/shop". Каждый товар знает свой слот в GUI (54-слотовый
 * двойной сундук), материал, количество за одну покупку и цену в очках клана.
 *
 * Ассортимент и цены — из таблицы экономики сервера (колонки D/E). Товары
 * разложены по смысловым рядам, как в самой таблице: блоки, руда/слитки,
 * расходники, еда, снаряжение, кастомные предметы. В каждом ряду занят
 * центральный блок из 5 столбцов (со 2-го по 6-й, считая с нуля), поэтому
 * слоты ряда r — это r*9+2 ... r*9+6.
 *
 * ENEMY_RADAR_POTION и METEORITE_ITEM — особый случай: материал здесь нужен
 * только для запасной иконки; реальный ItemStack (с правильными именем, лором
 * и PDC-меткой) строится в ShopModule/ShopListener через EnemyPotionModule
 * и MeteoriteModule.
 */
public enum ShopItem {

    // Ряд 0 — блоки
    COBBLESTONE(2, Material.COBBLESTONE, 64, 2, "§7Булыжник"),
    PLANKS(3, Material.OAK_PLANKS, 64, 2, "§6Доски"),
    OBSIDIAN(4, Material.OBSIDIAN, 16, 10, "§5Обсидиан"),
    BED(5, Material.RED_BED, 1, 15, "§cКровать"),

    // Ряд 1 — руда и слитки
    IRON_INGOT(11, Material.IRON_INGOT, 1, 3, "§fЖелезный слиток"),
    GOLD_INGOT(12, Material.GOLD_INGOT, 1, 5, "§6Золотой слиток"),
    LAPIS_LAZULI(13, Material.LAPIS_LAZULI, 32, 5, "§9Лазурит"),
    DIAMOND(14, Material.DIAMOND, 1, 15, "§bАлмаз"),

    // Ряд 2 — расходники
    ENDER_PEARL(20, Material.ENDER_PEARL, 1, 5, "§bЭндер-жемчуг"),
    SLIME_BALL(21, Material.SLIME_BALL, 16, 20, "§aСлизь"),
    ANCIENT_DEBRIS(22, Material.ANCIENT_DEBRIS, 1, 35, "§8Древние обломки"),

    // Ряд 3 — еда
    STEAK(29, Material.COOKED_BEEF, 16, 15, "§cСтейк"),
    GOLDEN_APPLE(30, Material.GOLDEN_APPLE, 1, 15, "§6Золотое яблоко"),
    ENCHANTED_GOLDEN_APPLE(31, Material.ENCHANTED_GOLDEN_APPLE, 1, 50, "§eЗачарованное золотое яблоко"),

    // Ряд 4 — снаряжение и прочее
    SADDLE(38, Material.SADDLE, 1, 15, "§6Седло"),
    CHORUS_FRUIT(39, Material.CHORUS_FRUIT, 16, 20, "§dХорус"),
    VILLAGER_SPAWN_EGG(40, Material.VILLAGER_SPAWN_EGG, 1, 45, "§2Яйцо жителя"),
    END_CRYSTAL(41, Material.END_CRYSTAL, 16, 50, "§5Кристалл Края"),
    FIREWORK_ROCKET(42, Material.FIREWORK_ROCKET, 16, 15, "§dФейерверки"),

    // Ряд 5 — кастомные предметы плагина
    ENEMY_RADAR_POTION(47, Material.POTION, 1, 200, "§cЗелье Чутья Врагов"),
    METEORITE_ITEM(48, Material.POPPED_CHORUS_FRUIT, 1, 300, "§4Фрiкадэльки");

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