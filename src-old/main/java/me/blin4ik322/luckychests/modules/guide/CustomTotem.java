package me.blin4ik322.luckychests.modules.guide;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Кастомный "Тотем Стойкости" — визуально TOTEM_OF_UNDYING, но со своим
 * именем/лором и меткой в PersistentDataContainer, чтобы отличать его от
 * обычного тотема (например, дропа со стража разбойников).
 *
 * Рецепт (3x3), придуман для этого модуля — при желании поменяйте
 * ингредиенты в createRecipe()/recipeIcons():
 *
 *   [ ]              [Иглобрюх]        [ ]
 *   [Золотой блок]   [Блок алмаза]     [Золотой блок]
 *   [ ]              [Золотой блок]    [ ]
 *
 * Регистрируется как обычный ShapedRecipe (Bukkit.addRecipe в
 * GuideModule.enable()) — реально крафтится на верстаке. Отдельно
 * GuideCommand ("/guide totem") показывает этот же рецепт визуально,
 * через GUI-витрину (см. TotemRecipeHolder, TotemRecipeListener).
 */
public final class CustomTotem {

    public static final String DISPLAY_NAME = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Тотем Стойкости";

    private static final String RECIPE_KEY = "custom_totem_recipe";
    private static final String TAG_KEY = "custom_totem_item";

    private CustomTotem() {
    }

    /** Готовый предмет тотема со всеми метаданными (имя, лор, метка). */
    public static ItemStack createItem(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(DISPLAY_NAME);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Усиленная версия тотема стойкости.",
                ChatColor.GRAY + "Скрафтен, а не найден.",
                "",
                ChatColor.DARK_PURPLE + "Рецепт: /guide totem"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(tagKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Проверяет, что это именно кастомный тотем, а не обычный ванильный. */
    public static boolean isCustomTotem(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(tagKey(plugin), PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    /** Регистрирует рецепт крафта на сервере. Вызывается один раз из GuideModule.enable(). */
    public static ShapedRecipe createRecipe(JavaPlugin plugin) {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey(plugin), createItem(plugin));
        recipe.shape(" P ", "GDG", " G ");
        recipe.setIngredient('P', Material.PUFFERFISH);
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        return recipe;
    }

    /**
     * Иконки рецепта построчно (пусто,P,пусто, G,D,G, пусто,G,пусто) —
     * используются GuideCommand для визуального показа в GUI-витрине.
     * {@code null} означает пустую ячейку сетки крафта (GuideCommand
     * оставляет там фоновый заполнитель, а не показывает иконку).
     */
    public static List<ItemStack> recipeIcons() {
        return Arrays.asList(
                null, new ItemStack(Material.PUFFERFISH), null,
                new ItemStack(Material.GOLD_BLOCK), new ItemStack(Material.DIAMOND_BLOCK), new ItemStack(Material.GOLD_BLOCK),
                null, new ItemStack(Material.GOLD_BLOCK), null
        );
    }

    private static NamespacedKey tagKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, TAG_KEY);
    }

    private static NamespacedKey recipeKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, RECIPE_KEY);
    }
}
