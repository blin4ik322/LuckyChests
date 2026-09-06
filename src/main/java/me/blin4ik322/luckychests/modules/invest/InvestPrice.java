package me.blin4ik322.luckychests.modules.invest;

/**
 * Цена одного предмета в ценнике вложений: сколько очков даётся за партию
 * из {@code unit} штук.
 *
 * Ценник задаётся партиями, а не поштучно, потому что в экономике сервера
 * есть предметы дешевле одного очка: стак булыжника глубинного сланца (64 шт.)
 * стоит 1 очко, 32 бревна — тоже 1. Поштучной целочисленной ценой такое
 * не выразить, поэтому цена — это пара "очки за партию".
 *
 * Предметы продаются только ЦЕЛЫМИ партиями: если игрок положил 100 булыжников
 * при партии в 64, продадутся 64 (1 очко), а оставшиеся 36 вернутся ему обратно.
 * Так игрок никогда не теряет предметы, за которые ему не заплатили.
 */
public class InvestPrice {

    private final int points;
    private final int unit;

    public InvestPrice(int points, int unit) {
        this.points = points;
        this.unit = Math.max(1, unit);
    }

    /** Очков за одну полную партию. */
    public int getPoints() {
        return points;
    }

    /** Размер партии в штуках (1 — предмет продаётся поштучно). */
    public int getUnit() {
        return unit;
    }

    /** Сколько очков дадут за {@code amount} штук (неполная партия не оплачивается). */
    public int pointsFor(int amount) {
        return (amount / unit) * points;
    }

    /** Сколько штук из {@code amount} будет фактически продано (остаток вернётся игроку). */
    public int consumedFor(int amount) {
        return (amount / unit) * unit;
    }
}
