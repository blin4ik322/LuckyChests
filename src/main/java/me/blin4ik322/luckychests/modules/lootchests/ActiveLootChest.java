package me.blin4ik322.luckychests.modules.lootchests;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

/**
 * Одно активное (заспавненное) хранилище: его блок-локация, летающий текст
 * обратного отсчёта (появляется только после первого открытия) и задача
 * этого отсчёта.
 *
 * {@link #getSecondsLeft()} держит актуальный остаток отсчёта, чтобы
 * LootChestsModule мог сохранить его в lootchests.yml и продолжить отсчёт
 * с того же места после перезапуска сервера.
 */
public class ActiveLootChest {

    /** Значение {@link #secondsLeft}, означающее "хранилище ещё не открывали". */
    public static final long NO_COUNTDOWN = -1L;

    private final Location location;
    private TextDisplay textDisplay;
    private BukkitTask countdownTask;
    private boolean countdownStarted;
    private long secondsLeft = NO_COUNTDOWN;

    public ActiveLootChest(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public TextDisplay getTextDisplay() {
        return textDisplay;
    }

    public void setTextDisplay(TextDisplay textDisplay) {
        this.textDisplay = textDisplay;
    }

    public BukkitTask getCountdownTask() {
        return countdownTask;
    }

    public void setCountdownTask(BukkitTask countdownTask) {
        this.countdownTask = countdownTask;
    }

    public boolean isCountdownStarted() {
        return countdownStarted;
    }

    public void setCountdownStarted(boolean countdownStarted) {
        this.countdownStarted = countdownStarted;
    }

    /** Сколько секунд осталось до исчезновения, или {@link #NO_COUNTDOWN}, если отсчёт ещё не идёт. */
    public long getSecondsLeft() {
        return secondsLeft;
    }

    public void setSecondsLeft(long secondsLeft) {
        this.secondsLeft = secondsLeft;
    }
}
