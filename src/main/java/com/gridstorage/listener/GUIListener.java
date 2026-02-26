package com.gridstorage.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.gridstorage.GridStorage;
import com.gridstorage.manager.GUIManager;
import com.gridstorage.model.PlayerStorage;

/**
 * GUI 事件监听器
 * 处理所有GUI的点击和关闭事件
 * 支持槽位GUI内的物品存取，模拟普通箱子行为
 */
public class GUIListener implements Listener {

    private final GridStorage plugin;
    private final GUIManager guiManager;

    public GUIListener(GridStorage plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGuiManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        // 检查是否是网格GUI
        if (guiManager.isGridGUI(event.getInventory())) {
            event.setCancelled(true);
            if (clicked != null && clicked.hasItemMeta()) {
                plugin.getLogger().info("点击了网格GUI，槽位: " + event.getRawSlot());
                handleGridClick(player, clicked, event.getRawSlot());
            }
            return;
        }

        // 检查是否是槽位GUI
        if (guiManager.isSlotGUI(event.getInventory())) {
            // 槽位GUI内的操作是允许的（模拟普通箱子）
            boolean isSlotAction = false;

            if (event.getRawSlot() < event.getInventory().getSize()) {
                // 点击的是槽位GUI内部，允许操作
                plugin.getLogger().info("槽位GUI内部操作: 槽位 " + event.getRawSlot());
                isSlotAction = true;
            } else {
                // 点击的是玩家背包，允许操作（与普通箱子一样可以拖入拖出）
                plugin.getLogger().info("玩家背包操作: 槽位 " + event.getRawSlot());
                isSlotAction = true;
            }

            // 在操作后自动保存数据（延迟1 tick以避免干扰）
            if (isSlotAction && event.getAction() != org.bukkit.event.inventory.InventoryAction.NOTHING) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    autoSaveSlotContents(player, event.getInventory());
                }, 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // 检查是否是网格GUI
        if (guiManager.isGridGUI(event.getInventory())) {
            event.setCancelled(true);
            return;
        }

        // 检查是否是槽位GUI
        if (guiManager.isSlotGUI(event.getInventory())) {
            // 允许拖拽操作（模拟普通箱子）
            boolean isSlotAction = false;

            // 检查拖拽是否涉及槽位GUI
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    // 拖拽到槽位GUI，允许
                    plugin.getLogger().info("槽位GUI拖拽操作");
                    isSlotAction = true;
                    break;
                }
            }

            // 在拖拽后自动保存数据（延迟1 tick以避免干扰）
            if (isSlotAction) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    autoSaveSlotContents(player, event.getInventory());
                }, 1L);
            }
        }
    }

    /**
     * 处理网格GUI点击
     */
    private void handleGridClick(Player player, ItemStack clicked, int rawSlot) {
        // 使用NBT API获取NBT数据
        de.tr7zw.nbtapi.NBTItem nbtItem = new de.tr7zw.nbtapi.NBTItem(clicked);
        String type = nbtItem.getString("gridstorage_type");

        plugin.getLogger().info("NBT类型: " + type);

        if (type == null || type.isEmpty()) {
            plugin.getLogger().info("NBT类型为空，无法处理点击");
            return;
        }

        switch (type) {
            case "slot":
                // 点击了槽位
                String slotIdStr = nbtItem.getString("gridstorage_slot_id");
                plugin.getLogger().info("槽位ID: " + slotIdStr);
                if (slotIdStr != null && !slotIdStr.isEmpty()) {
                    try {
                        int slotId = Integer.parseInt(slotIdStr);
                        plugin.getLogger().info("正在打开槽位: " + slotId);
                        plugin.getStorageManager().openSlot(player, slotId);
                    } catch (NumberFormatException e) {
                        player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.messages.invalid-slot"));
                    }
                }
                break;

            case "navigation":
                // 点击了导航按钮
                String action = nbtItem.getString("gridstorage_action");
                PlayerStorage storage = plugin.getStorageManager().getPlayerStorage(player);

                plugin.getLogger().info("导航操作: " + action);
                plugin.getLogger().info("当前页: " + (storage != null ? storage.getCurrentPage() : "null") + ", 最大页: " + (storage != null ? storage.getMaxPages() : "null"));

                if (storage != null && action != null && !action.isEmpty()) {
                    if (action.equals("prev")) {
                        if (storage.getCurrentPage() > 0) {
                            storage.previousPage();
                            guiManager.updateGridGUI(player, storage);
                        } else {
                            player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.gui.first-page"));
                        }
                    } else if (action.equals("next")) {
                        if (storage.getCurrentPage() < storage.getMaxPages() - 1) {
                            storage.nextPage();
                            guiManager.updateGridGUI(player, storage);
                        } else {
                            player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.gui.last-page"));
                        }
                    }
                }
                break;
        }
    }

    /**
     * 自动保存槽位内容
     * 在玩家对槽位GUI进行操作后触发
     */
    private void autoSaveSlotContents(Player player, org.bukkit.inventory.Inventory inv) {
        // 异步自动保存，不显示保存消息
        plugin.getScheduler().runAsync(() -> {
            guiManager.saveSlotContents(player, inv, true);
        });
    }

    private String getPrefix() {
        return plugin.getConfigManager().getPrefix();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();

        // 关闭槽位 GUI：先保存内容再清理映射
        if (guiManager.isSlotGUI(inv)) {
            plugin.getLogger().info("槽位GUI 关闭，保存内容");
            guiManager.saveSlotContents(player, inv);      // 会异步写数据库
            guiManager.closeSlotGUI(player);
        } else if (guiManager.isGridGUI(inv)) {
            plugin.getLogger().info("网格GUI 关闭，清理状态");
            guiManager.closeGUI(player);
            plugin.getStorageManager().closeStorage(player);
        }
    }
}
