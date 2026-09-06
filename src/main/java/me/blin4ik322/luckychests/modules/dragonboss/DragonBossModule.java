package me.blin4ik322.luckychests.modules.dragonboss;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import me.blin4ik322.luckychests.modules.playersbattle.PlayerBattleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Модуль "Дракон Края" — награда клану за убийство эндер-дракона
 * (таблица экономики, строка "Kill Dragon": H3 = очки, I3 = надбавка,
 * J3 = кулдаун).
 *
 * Что делает:
 *  - при убийстве дракона игроком его клан получает {@link #KILL_REWARD}
 *    очков в счётчик ClanScores;
 *  - собственная награда за голову убийцы растёт на
 *    {@link PlayerBattleManager#DRAGON_KILL_BONUS} (+10%);
 *  - награда выдаётся не чаще одного раза в {@link #COOLDOWN_MILLIS}
 *    (1 час) на весь сервер. Кулдаун именно общий, а не персональный:
 *    дракона можно перепризывать эндер-кристаллами, и без общего
 *    ограничения одна и та же группа игроков фармила бы его по кругу;
 *  - пока идёт кулдаун, дракона вообще нельзя призвать заново: установка
 *    эндер-кристаллов на выходной портал отменяется (кристаллы при этом
 *    не тратятся);
 *  - над порталом в центре Края висит табло с обратным отсчётом до момента,
 *    когда дракона можно будет призвать снова.
 *
 * Момент последней выдачи хранится в dragon.yml, поэтому кулдаун переживает
 * перезапуск сервера.
 */
public class DragonBossModule implements Listener {

    /** Сколько очков ClanScores получает клан игрока за убийство дракона (H3). */
    public static final long KILL_REWARD = 100L;

    /** Кулдаун награды за дракона — 1 час (J3), общий на весь сервер. */
    public static final long COOLDOWN_MILLIS = 60L * 60L * 1000L;

    /**
     * Радиус вокруг центра портала, в котором установка эндер-кристалла
     * считается попыткой призыва. Площадка портала — 5x5 блоков, берём с
     * запасом, чтобы накрыть все четыре угловых кристалла.
     */
    private static final double PORTAL_RADIUS = 8.0;

    /** На сколько блоков выше основания портала висит табло. */
    private static final double TEXT_HEIGHT = 6.0;

    private final JavaPlugin plugin;
    private final ClanManager clanManager;
    private final PlayerBattleManager battleManager;
    private final File file;
    private final NamespacedKey timerKey;

    private long lastRewardMillis;
    private TextDisplay timerDisplay;
    private BukkitTask timerTask;

    public DragonBossModule(JavaPlugin plugin, ClanManager clanManager, PlayerBattleManager battleManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
        this.battleManager = battleManager;
        this.file = new File(plugin.getDataFolder(), "dragon.yml");
        this.timerKey = new NamespacedKey(plugin, "dragon_timer");
    }

    /** Регистрирует слушатель событий. Вызывается один раз из LuckyChests#onEnable. */
    public void enable() {
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Раз в секунду обновляем табло над порталом (и убираем его, когда кулдаун вышел).
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTimer, 20L, 20L);
        plugin.getLogger().info("[DragonBoss] модуль награды за дракона включён.");
    }

    /**
     * Вызывается из LuckyChests#onDisable(): гасит задачу и убирает летающий
     * текст. TextDisplay — обычная сущность мира, без явного удаления она
     * осталась бы висеть над порталом и после перезапуска.
     */
    public void shutdown() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        removeTimerDisplay();
    }

    // ---------------------------------------------------------------
    // Кулдаун (dragon.yml)
    // ---------------------------------------------------------------

    private void load() {
        if (!file.exists()) {
            return;
        }
        lastRewardMillis = YamlConfiguration.loadConfiguration(file).getLong("last-reward-millis", 0L);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("last-reward-millis", lastRewardMillis);
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[DragonBoss] не удалось сохранить dragon.yml", e);
        }
    }

    /** Сколько миллисекунд осталось до следующей возможной награды (0 — уже можно). */
    public long getCooldownRemaining() {
        long passed = System.currentTimeMillis() - lastRewardMillis;
        return Math.max(0L, COOLDOWN_MILLIS - passed);
    }

    // ---------------------------------------------------------------
    // Запрет призыва дракона во время кулдауна
    // ---------------------------------------------------------------

    /**
     * Пока идёт кулдаун, дракона нельзя призвать заново: отменяем установку
     * эндер-кристаллов на выходной портал.
     *
     * Ловим именно установку кристалла, а не спавн самого дракона: если
     * отменять спавн, кристаллы уже потрачены, а бой Края остаётся в
     * подвешенном состоянии. Так игрок просто не может начать призыв и не
     * теряет ресурсы. Кристаллы в любых других местах Края (в бою, для
     * украшения) не трогаем — только на площадке портала.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) {
            return;
        }

        long remaining = getCooldownRemaining();
        if (remaining <= 0) {
            return;
        }

        if (!isOnEndPortal(event.getBlock().getLocation())) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (player != null) {
            player.sendMessage(ChatColor.RED + "Дракона Края пока нельзя призвать — осталось "
                    + formatRemaining(remaining) + ".");
        }
    }

    /** Площадка выходного портала: тот же мир и не дальше PORTAL_RADIUS от центра по горизонтали. */
    private boolean isOnEndPortal(Location loc) {
        Location portal = getPortalLocation();
        if (portal == null || loc.getWorld() == null || !loc.getWorld().equals(portal.getWorld())) {
            return false;
        }
        double dx = loc.getX() - portal.getX();
        double dz = loc.getZ() - portal.getZ();
        return dx * dx + dz * dz <= PORTAL_RADIUS * PORTAL_RADIUS;
    }

    /** Центр основания выходного портала, или null, если Край не загружен/портал ещё не создан. */
    private Location getPortalLocation() {
        World end = getEndWorld();
        if (end == null) {
            return null;
        }
        DragonBattle battle = end.getEnderDragonBattle();
        return battle == null ? null : battle.getEndPortalLocation();
    }

    private World getEndWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                return world;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Табло обратного отсчёта над порталом
    // ---------------------------------------------------------------

    private void tickTimer() {
        long remaining = getCooldownRemaining();
        if (remaining <= 0) {
            removeTimerDisplay(); // кулдаун вышел — табло больше не нужно
            return;
        }

        Location portal = getPortalLocation();
        if (portal == null) {
            // Край не загружен или портал ещё не создан — показывать не над чем.
            removeTimerDisplay();
            return;
        }

        // Если чанк портала не загружен, в Крае сейчас никого нет. Табло там
        // всё равно некому увидеть, а spawn() принудительно подгрузил бы чанк —
        // и так каждую секунду весь час кулдауна. Проверяем через
        // World#isChunkLoaded, а не Location#getChunk(): последний сам грузит чанк.
        if (!portal.getWorld().isChunkLoaded(portal.getBlockX() >> 4, portal.getBlockZ() >> 4)) {
            return;
        }

        if (timerDisplay == null || !timerDisplay.isValid()) {
            timerDisplay = spawnTimerDisplay(portal.clone().add(0.5, TEXT_HEIGHT, 0.5));
        }
        updateTimerText(timerDisplay, remaining);
    }

    private TextDisplay spawnTimerDisplay(Location loc) {
        // Подчищаем возможные "висяки" от прошлого запуска: если сервер завершился
        // аварийно, shutdown() не отработал и старое табло осталось в мире.
        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 16.0, 16.0, 16.0)) {
            if (nearby instanceof TextDisplay
                    && nearby.getPersistentDataContainer().has(timerKey, PersistentDataType.BYTE)) {
                nearby.remove();
            }
        }

        return loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(true);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.getPersistentDataContainer().set(timerKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void updateTimerText(TextDisplay display, long remainingMillis) {
        // Корень намеренно пустой: в Adventure дочерние компоненты наследуют
        // стиль родителя, и если сделать корнем жирный заголовок, жирными
        // станут и обе остальные строки.
        Component text = Component.empty()
                .append(Component.text("Дракон Края", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Можно призвать через ", NamedTextColor.GRAY))
                .append(Component.text(formatClock(remainingMillis), NamedTextColor.RED, TextDecoration.BOLD));
        display.text(text);
    }

    private void removeTimerDisplay() {
        if (timerDisplay != null) {
            timerDisplay.remove();
            timerDisplay = null;
        }
    }

    /** "59:58" — мм:сс, для табло над порталом. */
    private String formatClock(long millis) {
        long totalSeconds = (millis + 999L) / 1000L; // округляем вверх, чтобы не показывать 0:00 раньше времени
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    // ---------------------------------------------------------------
    // Смерть дракона / награда клану
    // ---------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof EnderDragon)) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        long remaining = getCooldownRemaining();
        if (remaining > 0) {
            killer.sendMessage(ChatColor.GRAY + "Дракон повержен, но награда за него уже выдавалась недавно."
                    + " Следующая — через " + formatRemaining(remaining) + ".");
            return;
        }

        lastRewardMillis = System.currentTimeMillis();
        save();

        // Надбавка к собственной награде за голову — независимо от того,
        // состоит ли убийца в клане.
        battleManager.registerBossKill(killer.getUniqueId(), PlayerBattleManager.DRAGON_KILL_BONUS);

        Clan clan = clanManager.getClanByPlayer(killer.getUniqueId());
        if (clan == null) {
            killer.sendMessage(ChatColor.YELLOW + "Вы убили Дракона Края, но вы не состоите в клане — очки не начислены.");
            return;
        }

        clanManager.addScore(clan, KILL_REWARD);

        killer.sendMessage(ChatColor.LIGHT_PURPLE + "Вы убили Дракона Края! Клану "
                + ChatColor.BOLD + ChatColor.GOLD + clan.getName() + ChatColor.LIGHT_PURPLE + " начислено "
                + ChatColor.AQUA + KILL_REWARD + ChatColor.LIGHT_PURPLE + " очков.");

        for (UUID memberId : clan.getMembers()) {
            if (memberId.equals(killer.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(ChatColor.LIGHT_PURPLE + killer.getName()
                        + " убил Дракона Края! Клану начислено " + KILL_REWARD + " очков.");
            }
        }
    }

    /** "42м", "1ч05м" — для сообщения об оставшемся кулдауне. */
    private String formatRemaining(long millis) {
        long totalMinutes = (millis + 59_999L) / 60_000L; // округляем вверх до минуты
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return hours + "ч" + String.format("%02d", minutes) + "м";
        }
        return minutes + "м";
    }
}
