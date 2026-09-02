package me.blin4ik322.luckychests.modules.clans;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Модель данных одного клана (часть модуля clan).
 * Хранит название, лидера, участников и очки клана (ClanScores).
 *
 * Очки (score) — это и есть "счётчик ClanScores": он живёт вместе с кланом
 * и сохраняется на диск через ClanManager. Удаляется только вместе с кланом,
 * то есть по команде /clan disband.
 */
public class Clan {

    private String name;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private long score;

    public Clan(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
        this.score = 0L;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public void addScore(long amount) {
        this.score += amount;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }
}