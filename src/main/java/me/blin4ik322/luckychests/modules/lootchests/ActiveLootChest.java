package me.blin4ik322.luckychests.modules.lootchests;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

/**
 * Одно активное (заспавненное) хранилище: его блок-локация, летающий текст
 * обратного отсчёта (появляется только после первого открытия) и задача
 * этого отсчёта.
 */
public class ActiveLootChest {

    private final Location location;
    private TextDisplay textDisplay;
    private BukkitTask countdownTask;
    private boolean countdownStarted;

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
}
