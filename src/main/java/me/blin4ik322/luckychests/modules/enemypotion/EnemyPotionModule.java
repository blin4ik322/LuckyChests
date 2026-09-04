package me.blin4ik322.luckychests.modules.enemypotion;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Модуль "Зелье Чутья Врагов" (Enemy Radar Potion) для Paper 1.21.11.
 *
 * Игрок выпивает зелье → на 3 минуты получает "радар": каждые 200ms в
 * Actionbar показывается дистанция, стрелка направления и оставшееся время,
 * а компас в инвентаре указывает на ближайшего врага.
 *
 * "Враг" = любой другой онлайн-игрок в том же мире, кроме: самого себя,
 * членов того же клана, спектейторов и невидимых игроков.
 */
public class EnemyPotionModule {

    /** PDC-ключ, которым помечен ItemStack зелья — по нему опознаём его в PlayerItemConsumeEvent. */
    public static final String KEY_NAME = "radar_potion";

    private static final long RADAR_DURATION_MS = 3 * 60 * 1000L; // 3 минуты
    private static final long TICK_PERIOD = 4L; // каждые 4 тика = 200ms (плавнее чем 500ms)

    private static final String[] ARROWS = {
            "\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"
            // ↑        ↗        →        ↘        ↓        ↙        ←        ↖
    };

    private final JavaPlugin plugin;
    private final ClanManager clanManager; // может быть null — тогда "своих по клану" не различаем
    private final NamespacedKey radarKey;

    // UUID игрока -> момент времени (millis), когда радар истекает.
    private final Map<UUID, Long> activeRadars = new ConcurrentHashMap<>();

    private BukkitTask task;

    public EnemyPotionModule(JavaPlugin plugin, ClanManager clanManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
        this.radarKey = new NamespacedKey(plugin, KEY_NAME);
    }

    public NamespacedKey getKey() {
        return radarKey;
    }

    // ---------------------------------------------------------------
    // Крафт предмета (Adventure API для текста в 1.21.11)
    // ---------------------------------------------------------------

    /** Создаёт новый ItemStack "Зелья Чутья Врагов" со всей нужной разметкой. */
    public ItemStack getRadarPotion() {
        ItemStack item = new ItemStack(Material.POTION, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        // Имя предмета (жирный, без курсива - как оригинальный переименованный предмет)
        Component displayName = Component.text("Зелье Чутья Врагов", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        // Lore через Adventure Component
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Выпей, чтобы на 3 минуты", NamedTextColor.GRAY));
        lore.add(Component.text("обнаружить ближайшего врага", NamedTextColor.GRAY));
        lore.add(Component.text("через компас и Actionbar.", NamedTextColor.GRAY));
        meta.lore(lore);

        // PDC-маркер (Paper 1.21.11 совместимо)
        meta.getPersistentDataContainer().set(radarKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** Проверяет, что предмет — именно наше зелье (по PDC-ключу). */
    public boolean isRadarPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(radarKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------
    // Реестр активных радаров
    // ---------------------------------------------------------------

    public void activateRadar(Player player) {
        activeRadars.put(player.getUniqueId(), System.currentTimeMillis() + RADAR_DURATION_MS);
    }

    public boolean hasActiveRadar(UUID playerId) {
        Long expiry = activeRadars.get(playerId);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    // ---------------------------------------------------------------
    // Фоновый таск
    // ---------------------------------------------------------------

    public void startTask(JavaPlugin plugin) {
        stopTask();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, TICK_PERIOD, TICK_PERIOD);
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        activeRadars.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = activeRadars.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());

            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            if (now >= entry.getValue()) {
                iterator.remove();
                Component expiredMsg = Component.text("Действие зелья закончилось.", NamedTextColor.RED);
                player.sendActionBar(expiredMsg);
                player.setCompassTarget(player.getWorld().getSpawnLocation());
                continue;
            }

            updateRadarDisplay(player);
        }
    }

    private void updateRadarDisplay(Player player) {
        Player nearest = findNearestEnemy(player);

        // Считаем оставшееся время
        Long expiryTime = activeRadars.get(player.getUniqueId());
        long remainingMs = expiryTime != null ? expiryTime - System.currentTimeMillis() : 0;
        long remainingSeconds = Math.max(0, remainingMs / 1000L);

        String timeStr = String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60);

        if (nearest == null) {
            Component noEnemyMsg = Component.text("враг не найден", NamedTextColor.GRAY)
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text(timeStr, NamedTextColor.WHITE));
            player.sendActionBar(noEnemyMsg);
            return;
        }

        double distance = player.getLocation().distance(nearest.getLocation());
        String arrow = getDirectionArrow(player, nearest.getLocation());

        Component radarMsg = Component.text(Math.round(distance) + "m", NamedTextColor.YELLOW)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text(arrow, NamedTextColor.GREEN))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text(timeStr, NamedTextColor.WHITE));

        player.sendActionBar(radarMsg);
        player.setCompassTarget(nearest.getLocation());
    }

    private Player findNearestEnemy(Player player) {
        Player nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (Player other : player.getWorld().getPlayers()) {
            if (!isValidTarget(player, other)) {
                continue;
            }
            double distanceSq = player.getLocation().distanceSquared(other.getLocation());
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = other;
            }
        }

        return nearest;
    }

    private boolean isValidTarget(Player viewer, Player candidate) {
        if (candidate.equals(viewer)) {
            return false;
        }
        if (candidate.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (candidate.isInvisible()) {
            return false;
        }
        // Радар показывает только врагов из других кланов — своих кланов исключаем
        if (isSameClan(viewer, candidate)) {
            return false;
        }
        return true;
    }

    /**
     * Проверяет, находятся ли игроки в одном клане, используя ClanManager#getClanByPlayer(UUID).
     */
    private boolean isSameClan(Player a, Player b) {
        if (clanManager == null) {
            return false;
        }
        Clan clanA = clanManager.getClanByPlayer(a.getUniqueId());
        Clan clanB = clanManager.getClanByPlayer(b.getUniqueId());
        if (clanA == null || clanB == null) {
            return false;
        }
        return clanA.getName().equalsIgnoreCase(clanB.getName());
    }

    /**
     * Угол между направлением взгляда игрока и вектором на цель на горизонтальной
     * плоскости, переведённый в одну из 8 стрелок.
     */
    private String getDirectionArrow(Player player, Location target) {
        Vector toTarget = target.toVector().subtract(player.getLocation().toVector());
        toTarget.setY(0);
        if (toTarget.lengthSquared() < 1.0E-6) {
            return ARROWS[0];
        }
        toTarget.normalize();

        Vector facing = player.getLocation().getDirection();
        facing.setY(0);
        if (facing.lengthSquared() < 1.0E-6) {
            return ARROWS[0];
        }
        facing.normalize();

        double dot = facing.dot(toTarget);
        double det = facing.getX() * toTarget.getZ() - facing.getZ() * toTarget.getX();
        double angle = Math.toDegrees(Math.atan2(det, dot));
        angle = (angle + 360) % 360;

        int index = (int) Math.round(angle / 45.0) % ARROWS.length;
        return ARROWS[index];
    }
}