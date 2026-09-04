package me.blin4ik322.luckychests.modules.meteorite;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Модуль "Метеоритный дождь". Кастомный предмет "Фрiкадэльки" (запечённый
 * хорус, светится как зачарованный). ПКМ с предметом в руке запускает
 * 5-секундный отсчёт (subtitle всем игрокам в радиусе 20 блоков от места
 * использования), после чего с высоты 30 блоков в радиусе 20 блоков падают
 * от 3 до 7 метеоритов (Fireball) со случайной мощностью взрыва (4-9).
 *
 * Заранее готов к продаже в /shop: наружу торчат getMeteoriteItem() и
 * isMeteoriteItem(), по аналогии с EnemyPotionModule.
 */
public class MeteoriteModule {

    public static final String KEY_NAME = "meteorite_item";

    private static final int COUNTDOWN_SECONDS = 5;
    private static final double RADIUS = 20.0;
    private static final double SPAWN_HEIGHT = 30.0;
    private static final int MIN_METEORS = 10;
    private static final int MAX_METEORS = 15;
    private static final float MIN_POWER = 2f;
    private static final float MAX_POWER = 4f;
    private static final int MIN_TICKS_BETWEEN_SPAWN = 4;
    private static final int MAX_TICKS_BETWEEN_SPAWN = 8;

    private final JavaPlugin plugin;
    private final NamespacedKey meteoriteKey;

    public MeteoriteModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.meteoriteKey = new NamespacedKey(plugin, KEY_NAME);
    }

    public NamespacedKey getKey() {
        return meteoriteKey;
    }

    // ---------------------------------------------------------------
    // Крафт предмета
    // ---------------------------------------------------------------

    /** Создаёт новый ItemStack "Фрiкадэльки" со всей нужной разметкой. */
    public ItemStack getMeteoriteItem() {
        ItemStack item = new ItemStack(Material.POPPED_CHORUS_FRUIT, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        // Название прямыми буквами, как в ванильном майнкрафте: тёмно-красный,
        // жирный, без курсива (как у предмета, переименованного в наковальне).
        Component displayName = Component.text("Фрiкадэльки", NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Используй, чтобы призвать метеориты", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("в радиусе 20 блоков.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("У тебя есть 5 секунд, чтобы спрятаться!", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // Светится как зачарованный предмет, но без реального зачарования
        // и без строки зачарований в тултипе (Paper 1.20.5+).
        meta.setEnchantmentGlintOverride(true);

        meta.getPersistentDataContainer().set(meteoriteKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** Проверяет, что предмет — именно наша "Фрiкадэлька" (по PDC-ключу). */
    public boolean isMeteoriteItem(ItemStack item) {
        if (item == null || item.getType() != Material.POPPED_CHORUS_FRUIT) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(meteoriteKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------
    // Логика метеоритного дождя
    // ---------------------------------------------------------------

    /**
     * Запускает отсчёт и метеоритный дождь в точке использования предмета.
     * Вызывается из MeteoriteListener при ПКМ с предметом в руке.
     */
    public void triggerMeteorShower(Location origin) {
        if (origin.getWorld() == null) {
            return;
        }

        new BukkitRunnable() {
            int secondsLeft = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (secondsLeft > 0) {
                    broadcastCountdown(origin, secondsLeft);
                    secondsLeft--;
                } else {
                    broadcastImpact(origin);
                    spawnMeteors(origin);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void broadcastCountdown(Location origin, int secondsLeft) {
        Component subtitle = Component.text(String.valueOf(secondsLeft), NamedTextColor.RED, TextDecoration.BOLD);
        Title title = Title.title(Component.empty(), subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO));

        for (Player player : nearbyPlayers(origin)) {
            player.showTitle(title);
        }
    }

    private void broadcastImpact(Location origin) {
        Component subtitle = Component.text("ФРІКАДЭЛЬКИ!", NamedTextColor.RED, TextDecoration.BOLD);
        Title title = Title.title(Component.empty(), subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300)));

        for (Player player : nearbyPlayers(origin)) {
            player.showTitle(title);
        }
    }

    private List<Player> nearbyPlayers(Location origin) {
        List<Player> result = new ArrayList<>();
        World world = origin.getWorld();
        if (world == null) {
            return result;
        }
        for (Player player : world.getPlayers()) {
            if (player.getWorld().equals(world) && player.getLocation().distance(origin) <= RADIUS) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * Спавнит от 3 до 7 метеоритов (Fireball) в случайных точках в радиусе
     * 20 блоков от origin, на высоте 30 блоков, с разной мощностью взрыва (4-9).
     * Метеориты падают не одновременно, а по одному — каждый следующий спавнится
     * с собственной случайной задержкой от 200мс до 1.5с после предыдущего.
     */
    private void spawnMeteors(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = random.nextInt(MIN_METEORS, MAX_METEORS + 1);

        long delayTicks = 0L;
        for (int i = 0; i < count; i++) {
            // 200мс = 4 тика, 1.5с = 30 тиков.
            delayTicks += random.nextInt(MIN_TICKS_BETWEEN_SPAWN, MAX_TICKS_BETWEEN_SPAWN);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double angle = random.nextDouble(0, Math.PI * 2);
                double distance = random.nextDouble(0, RADIUS);
                double offsetX = Math.cos(angle) * distance;
                double offsetZ = Math.sin(angle) * distance;

                Location spawnLocation = origin.clone().add(offsetX, SPAWN_HEIGHT, offsetZ);
                float power = MIN_POWER + random.nextFloat() * (MAX_POWER - MIN_POWER);

                world.spawn(spawnLocation, Fireball.class, fireball -> {
                    fireball.setYield(power);
                    fireball.setGravity(true);
                    // Начальный импульс вниз — дальше метеорит ускоряется гравитацией,
                    // как настоящий падающий объект, а не как обычный огненный шар.
                    fireball.setVelocity(new Vector(0, random.nextInt(-5, -2), 0));
                });
            }, delayTicks);
        }
    }
}