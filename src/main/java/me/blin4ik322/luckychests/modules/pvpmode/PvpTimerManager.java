package me.blin4ik322.luckychests.modules.pvpmode;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет боссбарами и таймерами для каждой PVP-пары.
 *
 * Ключ пары — строка "uuid1:uuid2" где uuid1 < uuid2 (лексически),
 * чтобы пара A→B и B→A давали один и тот же ключ.
 */
public class PvpTimerManager {

    // Длительность PVP-режима в секундах.
    private static final int PVP_DURATION = 10;

    // Текст ActionBar после выхода из режима.
    private static final String EXIT_MESSAGE =
            ChatColor.RED + "Вы вышли из режима ПВП";

    // Сколько тиков показывать ActionBar «выход» (1 тик = 0.05 с; 60 тиков = 3 с).
    private static final int EXIT_ACTIONBAR_TICKS = 60;

    private final JavaPlugin plugin;

    // pairKey → активная сессия (боссбары + задача)
    private final Map<String, PvpSession> sessions = new HashMap<>();

    public PvpTimerManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Запускает (или перезапускает) PVP-режим между двумя игроками.
     * Вызывается каждый раз при ударе.
     */
    public void startOrReset(Player attacker, Player victim) {
        String key = pairKey(attacker.getUniqueId(), victim.getUniqueId());

        // Если сессия уже есть — отменяем старый таймер и переиспользуем боссбары.
        if (sessions.containsKey(key)) {
            PvpSession existing = sessions.get(key);
            existing.task.cancel();
            existing.resetProgress(); // вернуть полосу на 100%
            existing.task = scheduleTick(key, existing);
        } else {
            // Создаём новые боссбары для каждого игрока.
            BossBar barA = createBar(attacker.getName());
            BossBar barV = createBar(victim.getName());
            barA.addPlayer(attacker);
            barV.addPlayer(victim);

            PvpSession session = new PvpSession(attacker.getUniqueId(), victim.getUniqueId(), barA, barV);
            session.task = scheduleTick(key, session);
            sessions.put(key, session);
        }
    }

    /**
     * Возвращает true, если оба игрока прямо сейчас в PVP-режиме друг с другом.
     * (Нужно, чтобы не выдавать боссбар при ударе по союзнику — но это проверяется
     * на уровне клана, здесь просто утилита для отладки.)
     */
    public boolean isInCombat(UUID a, UUID b) {
        return sessions.containsKey(pairKey(a, b));
    }

    /**
     * Обрабатывает выход игрока из сервера во время PVP.
     * Если игрок был в активной сессии — убивает его ударом от противника.
     */
    public void handlePlayerQuit(UUID playerId) {
        String keyToRemove = null;

        // Ищем сессию с этим игроком
        for (String key : sessions.keySet()) {
            PvpSession session = sessions.get(key);
            if (session.playerA.equals(playerId) || session.playerB.equals(playerId)) {
                keyToRemove = key;
                break;
            }
        }

        if (keyToRemove != null) {
            PvpSession session = sessions.get(keyToRemove);

            // Определяем противника
            UUID opponentId = session.playerA.equals(playerId) ? session.playerB : session.playerA;
            Player opponent = Bukkit.getPlayer(opponentId);
            Player quitter = Bukkit.getPlayer(playerId);

            // Убиваем уходящего игрока ударом от противника
            if (quitter != null && quitter.isOnline()) {
                quitter.damage(quitter.getHealth() + 10, opponent);
            }

            // Очищаем сессию
            session.task.cancel();
            session.barA.removeAll();
            session.barV.removeAll();
            sessions.remove(keyToRemove);
        }
    }

    // ── Внутренние методы ────────────────────────────────────────────────────

    private BossBar createBar(String opponentName) {
        return Bukkit.createBossBar(
                ChatColor.RED + "⚔ ПВП-режим: " + ChatColor.WHITE + "10с",
                BarColor.RED,
                BarStyle.SOLID
        );
    }

    /**
     * Запускает repeating-задачу: раз в 20 тиков (1 с) уменьшает прогресс боссбаров.
     * Когда таймер истекает — убирает боссбары и показывает ActionBar обоим.
     */
    private BukkitTask scheduleTick(String key, PvpSession session) {
        return Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int remaining = PVP_DURATION;

            @Override
            public void run() {
                remaining--;
                double progress = (double) remaining / PVP_DURATION;

                String title = ChatColor.RED + "⚔ ПВП-режим: "
                        + ChatColor.WHITE + remaining + "с";

                session.barA.setTitle(title);
                session.barA.setProgress(Math.max(0.0, progress));
                session.barV.setTitle(title);
                session.barV.setProgress(Math.max(0.0, progress));

                if (remaining <= 0) {
                    endSession(key, session);
                }
            }
        }, 20L, 20L); // первый тик через 1 с, затем каждую секунду
    }

    /**
     * Завершает сессию: убирает боссбары, показывает ActionBar «выход» каждому живому игроку.
     */
    private void endSession(String key, PvpSession session) {
        session.task.cancel();
        sessions.remove(key);

        session.barA.removeAll();
        session.barV.removeAll();

        showExitActionBar(session.playerA);
        showExitActionBar(session.playerB);
    }

    /**
     * Показывает ActionBar «Вы вышли из режима ПВП» на EXIT_ACTIONBAR_TICKS тиков.
     * Bukkit-API не поддерживает «длительность» для ActionBar напрямую,
     * поэтому используем повторяющуюся задачу.
     */
    private void showExitActionBar(UUID playerId) {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks = 0;
            private BukkitTask self;

            // Ленивая инициализация: сохраняем ссылку на себя через обёртку.
            {
                // Задача будет установлена сразу после запуска (см. ниже).
            }

            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || ticks >= EXIT_ACTIONBAR_TICKS) {
                    if (self != null) self.cancel();
                    return;
                }
                player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent(EXIT_MESSAGE)
                );
                ticks += 2; // задача раз в 2 тика, чтобы сообщение не моргало
            }
        }, 0L, 2L) /* ← возвращённую задачу нам нужно связать с self */;

        // Упрощённый вариант без self-ссылки:
        // запускаем отдельную задачу через EXIT_ACTIONBAR_TICKS, которая ничего не делает —
        // ActionBar сам пропадёт, если его перестать обновлять (клиент сбрасывает его ~через 3 с).
        // Описанный выше runTaskTimer уже держит текст 60 тиков (3 с), после чего
        // перестаёт слать пакеты и бар гаснет сам.
    }

    // ── Утилиты ─────────────────────────────────────────────────────────────

    /** Стабильный ключ пары: всегда uuid_меньший:uuid_больший (лексически). */
    private static String pairKey(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) < 0 ? sa + ":" + sb : sb + ":" + sa;
    }

    // ── Внутренний класс сессии ──────────────────────────────────────────────

    private static class PvpSession {
        final UUID playerA;
        final UUID playerB;
        final BossBar barA;
        final BossBar barV;
        BukkitTask task;

        PvpSession(UUID playerA, UUID playerB, BossBar barA, BossBar barV) {
            this.playerA = playerA;
            this.playerB = playerB;
            this.barA = barA;
            this.barV = barV;
        }

        /** Сбрасывает прогресс и заголовок боссбаров на начальные значения. */
        void resetProgress() {
            String title = ChatColor.RED + "⚔ ПВП-режим: " + ChatColor.WHITE + "10с";
            barA.setProgress(1.0);
            barA.setTitle(title);
            barV.setProgress(1.0);
            barV.setTitle(title);
        }
    }
}