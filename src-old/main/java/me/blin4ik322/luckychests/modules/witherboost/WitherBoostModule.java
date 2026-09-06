package me.blin4ik322.luckychests.modules.witherboost;

import me.blin4ik322.luckychests.modules.core.PluginModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль увеличенного шанса выпадения черепа визера (wither skeleton skull).
 * Вся логика фичи живёт в этой папке (modules/witherboost):
 *
 *  - WitherBoostModule   — точка входа, регистрация + состояние вкл/выкл + множитель
 *  - WitherSkullListener — EntityDeathEvent: заново кидает кубик на дроп черепа
 *  - WitherChanceCommand — команда /witherchance (только для операторов)
 *
 * По умолчанию используется ванильная база: 2.5% шанс + 1% за уровень Looting.
 * Множитель применяется к обеим величинам, так что "/witherchance 3" утраивает шанс.
 *
 * Подключается из основного класса через ModuleManager.loadAll(this, new WitherBoostModule()).
 */
public final class WitherBoostModule implements PluginModule {

    private static boolean enabled = true;

    // Множитель шанса выпадения черепа. 1.0 = ванильный шанс (эффективно ничего не меняется).
    private static double multiplier = 1.0;

    // Ванильные базовые значения: 2.5% шанс дропа + 1% за каждый уровень зачарования Looting.
    private static final double BASE_CHANCE = 0.025;
    private static final double LOOTING_BONUS_PER_LEVEL = 0.01;

    @Override
    public void load(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new WitherSkullListener(), plugin);

        WitherChanceCommand command = new WitherChanceCommand();
        if (plugin.getCommand("witherchance") != null) {
            plugin.getCommand("witherchance").setExecutor(command);
        } else {
            plugin.getLogger().warning("[WitherBoost] команда 'witherchance' не объявлена в plugin.yml"
                    + " — добавьте её в секцию commands.");
        }
    }

    @Override
    public String getName() {
        return "WitherBoost";
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static double getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(double value) {
        multiplier = value;
    }

    /**
     * Вычисляет итоговый шанс выпадения черепа визера с учётом уровня Looting
     * убийцы и текущего множителя. Результат всегда в диапазоне [0.0, 1.0].
     */
    public static double calculateChance(int lootingLevel) {
        double chance = (BASE_CHANCE + LOOTING_BONUS_PER_LEVEL * lootingLevel) * multiplier;
        return Math.max(0.0, Math.min(1.0, chance));
    }
}
