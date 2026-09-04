package me.blin4ik322.luckychests.modules.guide;

import me.blin4ik322.luckychests.modules.invest.InvestModule;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Точка входа модуля "Путеводитель" (guide):
 *
 *  - регистрирует настоящий крафт-рецепт кастомного тотема
 *    (CustomTotem.createRecipe) на верстаке сервера;
 *  - регистрирует TotemRecipeListener — защиту GUI-витрины рецепта
 *    (/guide totem) от кликов/перетаскивания;
 *  - регистрирует команду /guide (и алиасы /гайд, /help, /помощь —
 *    все объявлены как aliases одной команды "guide" в plugin.yml).
 *
 * Подключается из LuckyChests#onEnable():
 *   new GuideModule(this, investModule).enable();
 * Обязательно после того, как InvestModule уже включён (enable()),
 * так как GuideCommand использует его ценник для /guide invest.
 */
public class GuideModule {

    private final JavaPlugin plugin;
    private final InvestModule investModule;

    public GuideModule(JavaPlugin plugin, InvestModule investModule) {
        this.plugin = plugin;
        this.investModule = investModule;
    }

    public void enable() {
        registerTotemRecipe();

        Bukkit.getPluginManager().registerEvents(new TotemRecipeListener(), plugin);

        GuideCommand guideCommand = new GuideCommand(plugin, investModule);
        if (plugin.getCommand("guide") != null) {
            plugin.getCommand("guide").setExecutor(guideCommand);
            plugin.getCommand("guide").setTabCompleter(guideCommand);
        } else {
            plugin.getLogger().warning("[Guide] команда 'guide' не объявлена в plugin.yml — добавьте её в секцию commands.");
        }

        plugin.getLogger().info("[Guide] модуль путеводителя включён (/guide, /гайд, /help, /помощь).");
    }

    /** Настоящий крафт тотема на верстаке — тот же рецепт, что показывает /guide totem. */
    private void registerTotemRecipe() {
        ShapedRecipe recipe = CustomTotem.createRecipe(plugin);
        Bukkit.addRecipe(recipe);
    }
}
