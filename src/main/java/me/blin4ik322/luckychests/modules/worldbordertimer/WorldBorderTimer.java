package me.blin4ik322.luckychests.modules;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Модуль "WorldBorderTimer" — сужающийся барьер мира с обратным отсчётом.
 *
 * /event start {время} {радиус} — за {время} барьер мира стягивается
 * до диаметра 2*{радиус} блоков. Пока ивент активен, справа у всех игроков
 * висит маленький scoreboard, обновляющийся каждую секунду:
 *
 *  До конца
 *  3ч54м21с
 *
 *  Радиус:
 *  1554бл
 *
 * Сжатие барьера полностью выполняется движком Minecraft через
 * {@link WorldBorder#setSize(double, long)} — плагин лишь один раз запускает
 * анимацию и раз в секунду пересчитывает текущий радиус для табло, так как
 * WorldBorder#getSize() в Bukkit API (актуально и для Paper 1.21.11) отдаёт
 * КОНЕЧНЫЙ размер, а не текущий (анимированный), поэтому текущее значение
 * интерполируется вручную.
 */
public class WorldBorderTimer implements Listener {

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([dhmsDHMS])");

    // Уникальные "невидимые" ключи строк табло (последовательность цветовых кодов).
    // Не пересекаются с реальными именами игроков, поэтому безопасны как entry в Team.
    private static final String[] LINE_ENTRIES = {
            ChatColor.BLACK + "" + ChatColor.RESET,
            ChatColor.DARK_BLUE + "" + ChatColor.RESET,
            ChatColor.DARK_GREEN + "" + ChatColor.RESET,
            ChatColor.DARK_AQUA + "" + ChatColor.RESET,
            ChatColor.DARK_RED + "" + ChatColor.RESET
    };

    // Насколько "жёстко" ведёт себя граница для тех, кого она застала снаружи.
    // damageAmount/damageBuffer — стандартные ванильные значения (0.2 урона за
    // блок за границей в секунду, буфер 5 блоков без урона). Их можно менять.
    private static final double DAMAGE_AMOUNT = 0.2;
    private static final double DAMAGE_BUFFER = 5.0;
    // На сколько блоков внутрь от текущей границы отталкивать застигнутого игрока.
    private static final double PUSHBACK_MARGIN = 1.0;

    private final JavaPlugin plugin;

    private BukkitTask task;
    private Scoreboard scoreboard;
    private Team[] lines;

    private World world;
    private long startMillis;
    private long endMillis;
    private long durationSeconds;
    private double startDiameter;
    private double targetDiameter;
    private boolean active;

    public WorldBorderTimer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean isActive() {
        return active;
    }

    public double getTargetRadius() {
        return targetDiameter / 2.0;
    }

    public long getRemainingSeconds() {
        if (!active) {
            return 0L;
        }
        return Math.max(0, (endMillis - System.currentTimeMillis()) / 1000L);
    }

    // ---------------------------------------------------------------
    // Запуск / остановка
    // ---------------------------------------------------------------

    /**
     * Запускает ивент: за {@code durationSeconds} барьер мира {@code world}
     * стягивается так, чтобы расстояние от центра до границы стало равно
     * {@code targetRadius} блоков.
     */
    public void start(World world, long durationSeconds, double targetRadius) {
        stop(false); // если уже был активен другой ивент — сначала гасим его

        this.world = world;
        this.durationSeconds = durationSeconds;

        WorldBorder border = world.getWorldBorder();
        this.startDiameter = border.getSize();
        this.targetDiameter = targetRadius * 2.0;

        this.startMillis = System.currentTimeMillis();
        this.endMillis = startMillis + durationSeconds * 1000L;

        border.setSize(targetDiameter, durationSeconds);
        // Урон за нахождение снаружи барьера, как в ваниле (можно подкрутить значения выше).
        border.setDamageAmount(DAMAGE_AMOUNT);
        border.setDamageBuffer(DAMAGE_BUFFER);

        setupScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }

        active = true;
        tick(); // сразу отрисовать первое значение, не дожидаясь секунды

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Ивент] "
                + ChatColor.YELLOW + "Барьер мира начал сужаться до радиуса "
                + ChatColor.WHITE + (long) targetRadius + ChatColor.YELLOW + " блоков за "
                + ChatColor.WHITE + formatCountdown(durationSeconds) + ChatColor.YELLOW + "!");
    }

    /** Останавливает текущий ивент (без объявления, если {@code announce} = false). */
    public void stop(boolean announce) {
        if (!active) {
            return;
        }
        active = false;

        if (task != null) {
            task.cancel();
            task = null;
        }

        if (world != null) {
            // Фиксируем барьер на его текущем (интерполированном на этот момент) размере,
            // чтобы он не "доехал" рывком до цели после остановки ивента.
            world.getWorldBorder().setSize(currentDiameter());
        }

        removeScoreboardFromPlayers();

        if (announce) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Ивент] "
                    + ChatColor.YELLOW + "Сужение барьера остановлено.");
        }
    }

    private void finish() {
        active = false;
        if (task != null) {
            task.cancel();
            task = null;
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "[Ивент] "
                + ChatColor.YELLOW + "Барьер сжался до радиуса "
                + ChatColor.WHITE + (long) (targetDiameter / 2.0) + ChatColor.YELLOW + " блоков. Ивент завершён!");

        removeScoreboardFromPlayers();
    }

    // ---------------------------------------------------------------
    // Обновление табло
    // ---------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        if (now >= endMillis) {
            finish();
            return;
        }

        pushStragglersInside();

        long remainingSeconds = Math.max(0, (endMillis - now) / 1000L);
        long currentRadius = Math.round(currentDiameter() / 2.0);

        lines[0].setPrefix(ChatColor.GRAY + "До конца");
        lines[1].setPrefix(ChatColor.WHITE + "" + ChatColor.BOLD + formatCountdown(remainingSeconds));
        lines[2].setPrefix(""); // разделитель
        lines[3].setPrefix(ChatColor.GRAY + "Радиус:");
        lines[4].setPrefix(ChatColor.WHITE + "" + ChatColor.BOLD + currentRadius + "бл");
    }

    /**
     * Барьер стягивается сам по себе, и если игрок стоит на месте, граница может
     * "проехать" мимо него — ванильная коллизия блокирует только попытки шагнуть
     * за барьер, а не выталкивает того, кого уже настигла сжимающаяся граница.
     * Раз в секунду проверяем всех игроков в мире события и отталкиваем внутрь
     * тех, кто оказался снаружи текущей (анимированной) границы.
     */
    private void pushStragglersInside() {
        if (world == null) {
            return;
        }

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double safeRadius = Math.max(0.0, currentDiameter() / 2.0 - PUSHBACK_MARGIN);

        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            if (border.isInside(loc)) {
                continue;
            }

            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 0.001) {
                continue; // игрок ровно в центре — деление на 0, но такого снаружи не бывает
            }

            double scale = safeRadius / distance;
            double newX = center.getX() + dx * scale;
            double newZ = center.getZ() + dz * scale;
            int safeY = world.getHighestBlockYAt((int) Math.floor(newX), (int) Math.floor(newZ)) + 1;

            Location safeLocation = new Location(world, newX, safeY, newZ, loc.getYaw(), loc.getPitch());
            player.teleport(safeLocation);
            player.sendMessage(ChatColor.RED + "Барьер настиг тебя — тебя оттолкнуло внутрь!");
        }
    }

    /** Линейно интерполированный ТЕКУЩИЙ диаметр барьера в данный момент времени. */
    private double currentDiameter() {
        long now = System.currentTimeMillis();
        double progress = (now - startMillis) / (double) (durationSeconds * 1000L);
        progress = Math.max(0.0, Math.min(1.0, progress));
        return startDiameter + (targetDiameter - startDiameter) * progress;
    }

    // ---------------------------------------------------------------
    // Scoreboard
    // ---------------------------------------------------------------

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        Objective objective = scoreboard.registerNewObjective("event_border", "dummy",
                ChatColor.GOLD + "" + ChatColor.BOLD + "⏳ Ивент");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        lines = new Team[LINE_ENTRIES.length];
        int score = LINE_ENTRIES.length;
        for (int i = 0; i < LINE_ENTRIES.length; i++) {
            Team team = scoreboard.registerNewTeam("event_line_" + i);
            team.addEntry(LINE_ENTRIES[i]);
            lines[i] = team;
            objective.getScore(LINE_ENTRIES[i]).setScore(score--);
        }
    }

    private void removeScoreboardFromPlayers() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getScoreboard() == scoreboard) {
                player.setScoreboard(main);
            }
        }
        scoreboard = null;
        lines = null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active && scoreboard != null) {
            event.getPlayer().setScoreboard(scoreboard);
        }
    }

    // ---------------------------------------------------------------
    // Разбор времени и форматирование
    // ---------------------------------------------------------------

    /**
     * Разбирает время из аргумента команды. Поддерживает как чистое число секунд
     * ("300"), так и сокращения "1d2h30m15s" (можно указывать не все части,
     * порядок d-h-m-s, каждая часть необязательна).
     * Возвращает null, если строку разобрать не удалось.
     */
    public static Long parseDurationSeconds(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        if (input.matches("\\d+")) {
            return Long.parseLong(input);
        }

        Matcher matcher = DURATION_PART.matcher(input);
        long total = 0;
        boolean found = false;
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                return null; // мусор между частями — считаем строку некорректной
            }
            found = true;
            long value = Long.parseLong(matcher.group(1));
            switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                case 'd':
                    total += value * 86400L;
                    break;
                case 'h':
                    total += value * 3600L;
                    break;
                case 'm':
                    total += value * 60L;
                    break;
                case 's':
                    total += value;
                    break;
            }
            lastEnd = matcher.end();
        }

        if (!found || lastEnd != input.length()) {
            return null;
        }
        return total;
    }

    /** Форматирует секунды как "3ч54м21с" (часы опускаются, если их 0). */
    public static String formatCountdown(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append("ч");
        }
        if (h > 0 || m > 0) {
            sb.append(m).append("м");
        }
        sb.append(s).append("с");
        return sb.toString();
    }
}