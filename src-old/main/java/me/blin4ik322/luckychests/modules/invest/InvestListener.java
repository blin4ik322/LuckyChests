package me.blin4ik322.luckychests.modules.invest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Отслеживает содержимое "сундука вложений" и read-only GUI ценника
 * ("Что можно продавать?"):
 *  - слот {@link InvestModule#CONFIRM_SLOT} (последний, 54-й) — кнопка
 *    "Подтвердить": клик по нему всегда отменяется как обычное
 *    инвентарное действие и вместо этого вызывает
 *    {@link InvestModule#confirmInvestment(Player, Inventory)} (продажа
 *    предметов из ценника + мгновенный возврат остального). Меню при этом
 *    НЕ закрывается — сразу после продажи название сундука обновляется на
 *    "0 очков", и можно докладывать следующую партию. То же самое — для
 *    драгов, задевающих этот слот: они просто отменяются, чтобы кнопку
 *    нельзя было вынуть или перезаписать;
 *  - слот {@link InvestModule#SELL_INFO_SLOT} (слева снизу) — кнопка
 *    "Что можно продавать?": клик отменяется и открывает отдельный
 *    read-only сундук-ценник ({@link InvestModule#openPriceList(Player)});
 *  - в самом ценнике ({@link InvestPriceListHolder}) вообще ничего нельзя
 *    взять/переложить — любой клик и драг отменяются, а клик по кнопке
 *    "Назад" (тот же слот, слева снизу) возвращает игрока обратно в
 *    сундук вложений;
 *  - при любом другом клике/перетаскивании в сундуке вложений — на
 *    следующий тик пересчитывает сумму очков и, если название должно
 *    поменяться, пере-открывает игроку тот же инвентарь с новым названием
 *    (Bukkit не даёт переименовать уже открытый инвентарь "на месте" без
 *    NMS-пакетов, поэтому это стандартный обходной путь: пересоздать
 *    Inventory с теми же слотами и другим title);
 *  - при настоящем закрытии сундука вложений (не через кнопку
 *    "Подтвердить" и не переход в ценник/обратно) — ничего не продаётся,
 *    всё содержимое возвращается игроку через
 *    {@link InvestModule#returnItems(Player, Inventory)}. Пере-открытие
 *    ради смены названия или перехода в ценник и обратно тоже формально
 *    закрывает старый инвентарь (InventoryCloseEvent с причиной OPEN_NEW),
 *    поэтому такие срабатывания явно игнорируются. Если игрок в итоге
 *    закрывает сундук уже после одного или нескольких подтверждений, это
 *    тоже безопасно: на момент закрытия в инвентаре остаются только те
 *    предметы, что докинули уже ПОСЛЕ последнего подтверждения, — они, как
 *    обычно, возвращаются игроку.
 */
public class InvestListener implements Listener {

    private final InvestModule module;
    private final Map<UUID, String> lastTitles = new HashMap<>();
    // Пока игрок смотрит ценник ("Что можно продавать?"), тут хранится тот
    // самый сундук вложений (с уже положенными предметами), к которому нужно
    // вернуться по кнопке "Назад" — чтобы предметы не терялись.
    private final Map<UUID, Inventory> pendingInvestInventory = new HashMap<>();

    public InvestListener(InvestModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (top.getHolder() instanceof InvestPriceListHolder) {
            handlePriceListClick(event, top);
            return;
        }

        if (!(top.getHolder() instanceof InvestHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        boolean touchesConfirmButton = event.getClickedInventory() == top
                && event.getSlot() == InvestModule.CONFIRM_SLOT;

        if (touchesConfirmButton) {
            event.setCancelled(true);
            // На следующий тик — чтобы клик по кнопке гарантированно успел полностью обработаться.
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                module.confirmInvestment(player, top);
                // Меню не закрывается — сумма сброшена на 0, обновляем название сундука,
                // чтобы игрок сразу видел, что можно докладывать следующую партию.
                refreshTitle(player, top);
            });
            return;
        }

        boolean touchesSellInfoButton = event.getClickedInventory() == top
                && event.getSlot() == InvestModule.SELL_INFO_SLOT;

        if (touchesSellInfoButton) {
            event.setCancelled(true);
            pendingInvestInventory.put(player.getUniqueId(), top);
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
                if (player.isOnline()) {
                    module.openPriceList(player);
                }
            });
            return;
        }

        scheduleRefresh(player, top);
    }

    /** Ценник полностью read-only: любой клик отменяется, кроме кнопки "Назад". */
    private void handlePriceListClick(InventoryClickEvent event, Inventory top) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        boolean touchesBackButton = event.getClickedInventory() == top
                && event.getSlot() == InvestModule.PRICE_LIST_BACK_SLOT;

        if (touchesBackButton) {
            Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                Inventory previous = pendingInvestInventory.remove(player.getUniqueId());
                if (previous != null) {
                    // Возвращаем игрока в тот же самый инвентарь, в котором
                    // остались положенные им предметы — ничего не пересоздаём.
                    lastTitles.put(player.getUniqueId(), module.buildTitle(module.calculatePoints(previous)));
                    player.openInventory(previous);
                } else {
                    // Подстраховка на случай отсутствия записи (например,
                    // после перезагрузки листенера) — открываем чистое меню.
                    module.openMenu(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (top.getHolder() instanceof InvestPriceListHolder) {
            // Ценник полностью read-only — никаких перетаскиваний.
            event.setCancelled(true);
            return;
        }

        if (!(top.getHolder() instanceof InvestHolder)) {
            return;
        }

        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && (rawSlot == InvestModule.CONFIRM_SLOT || rawSlot == InvestModule.SELL_INFO_SLOT)) {
                // Перетаскивание задевает слот одной из кнопок — не даём её перезаписать/убрать.
                event.setCancelled(true);
                return;
            }
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        scheduleRefresh((Player) event.getWhoClicked(), top);
    }

    private void scheduleRefresh(Player player, Inventory involvedInventory) {
        // На следующий тик — чтобы клик/драг уже успел изменить содержимое инвентаря.
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> refreshTitle(player, involvedInventory));
    }

    private void refreshTitle(Player player, Inventory expectedTop) {
        if (!player.isOnline()) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof InvestHolder) || top != expectedTop) {
            return; // сундук уже закрыт/пересоздан по другой причине — обновлять нечего
        }

        int points = module.calculatePoints(top);
        String newTitle = module.buildTitle(points);

        if (newTitle.equals(lastTitles.get(player.getUniqueId()))) {
            return; // название не поменялось — не мигаем инвентарём зря
        }

        Inventory replacement = Bukkit.createInventory(new InvestHolder(), top.getSize(), newTitle);
        replacement.setContents(top.getContents());
        lastTitles.put(player.getUniqueId(), newTitle);
        player.openInventory(replacement);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InvestPriceListHolder) {
            if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) {
                // Либо это наш переход в сам ценник, либо переход "Назад" —
                // в обоих случаях это не настоящее закрытие, пропускаем.
                return;
            }
            if (!(event.getPlayer() instanceof Player)) {
                return;
            }
            Player player = (Player) event.getPlayer();
            lastTitles.remove(player.getUniqueId());

            // Игрок закрыл ценник напрямую (Esc/крестик), минуя кнопку
            // "Назад" — предметы из подвешенного сундука вложений всё равно
            // нужно вернуть игроку (или высыпать под ноги, если переполнен),
            // как при обычном закрытии сундука вложений.
            Inventory pending = pendingInvestInventory.remove(player.getUniqueId());
            if (pending != null) {
                module.returnItems(player, pending);
            }
            return;
        }

        if (!(event.getInventory().getHolder() instanceof InvestHolder)) {
            return;
        }
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) {
            // Это наш собственный ре-опен ради смены названия, а не настоящее закрытие игроком.
            return;
        }
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        lastTitles.remove(player.getUniqueId());

        // Обычное закрытие (не через кнопку "Подтвердить") — ничего не продаём,
        // всё содержимое возвращаем игроку (или роняем под ноги, если нет места).
        // Если закрытие произошло ПОСЛЕ confirmInvestment(), инвентарь уже пуст —
        // вызов безопасен и просто ничего не находит.
        module.returnItems(player, event.getInventory());
    }
}