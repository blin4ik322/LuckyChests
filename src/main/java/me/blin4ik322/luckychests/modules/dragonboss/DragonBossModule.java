package me.blin4ik322.luckychests.modules.dragonboss;

import me.blin4ik322.luckychests.modules.clans.Clan;
import me.blin4ik322.luckychests.modules.clans.ClanManager;
import me.blin4ik322.luckychests.modules.playersbattle.PlayerBattleManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

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
 *    ограничения одна и та же группа игроков фармила бы его по кругу.
 *    Сам дракон при этом убивается как обычно — ограничена только награда.
 *
 * Момент последней выдачи хранится в dragon.yml, поэтому кулдаун переживает
 * перезапуск сервера.
 */
public class DragonBossModule implements Listener {

    /** Сколько очков ClanScores получает клан игрока за убийство дракона (H3). */
    public static final long KILL_REWARD = 100L;

    /** Кулдаун награды за дракона — 1 час (J3), общий на весь сервер. */
    public static final long COOLDOWN_MILLIS = 60L * 60L * 1000L;

    private final JavaPlugin plugin;
    private final ClanManager clanManager;
    private final PlayerBattleManager battleManager;
    private final File file;

    private long lastRewardMillis;

    public DragonBossModule(JavaPlugin plugin, ClanManager clanManager, PlayerBattleManager battleManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
        this.battleManager = battleManager;
        this.file = new File(plugin.getDataFolder(), "dragon.yml");
    }

    /** Регистрирует слушатель событий. Вызывается один раз из LuckyChests#onEnable. */
    public void enable() {
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[DragonBoss] модуль награды за дракона включён.");
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
