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

        if (guiManager.isGridGUI(event.getInventory())) {
            event.setCancelled(true);
            if (clicked != null && clicked.hasItemMeta()) {
                plugin.getPluginLogger().debug("点击了网格GUI，槽位: " + event.getRawSlot());
                handleGridClick(player, clicked, event.getRawSlot());
            }
            return;
        }

        if (guiManager.isSlotGUI(event.getInventory())) {
            boolean isSlotAction = false;

            if (event.getRawSlot() < event.getInventory().getSize()) {
                plugin.getPluginLogger().debug("槽位GUI内部操作: 槽位 " + event.getRawSlot());
                isSlotAction = true;
            } else {
                plugin.getPluginLogger().debug("玩家背包操作: 槽位 " + event.getRawSlot());
                isSlotAction = true;
            }

            if (isSlotAction && event.getAction() != org.bukkit.event.inventory.InventoryAction.NOTHING) {
                final Inventory inv = event.getInventory();
                plugin.getScheduler().runDelayedAtEntity(player, () -> {
                    autoSaveSlotContents(player, inv);
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

        if (guiManager.isGridGUI(event.getInventory())) {
            event.setCancelled(true);
            return;
        }

        if (guiManager.isSlotGUI(event.getInventory())) {
            boolean isSlotAction = false;

            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    plugin.getPluginLogger().debug("槽位GUI拖拽操作");
                    isSlotAction = true;
                    break;
                }
            }

            if (isSlotAction) {
                final Inventory inv = event.getInventory();
                plugin.getScheduler().runDelayedAtEntity(player, () -> {
                    autoSaveSlotContents(player, inv);
                }, 1L);
            }
        }
    }

    private void handleGridClick(Player player, ItemStack clicked, int rawSlot) {
        de.tr7zw.nbtapi.NBTItem nbtItem = new de.tr7zw.nbtapi.NBTItem(clicked);
        String type = nbtItem.getString("gridstorage_type");

        plugin.getPluginLogger().debug("NBT类型: " + type);

        if (type == null || type.isEmpty()) {
            plugin.getPluginLogger().debug("NBT类型为空，无法处理点击");
            return;
        }

        switch (type) {
            case "slot":
                String slotIdStr = nbtItem.getString("gridstorage_slot_id");
                plugin.getPluginLogger().debug("槽位ID: " + slotIdStr);
                if (slotIdStr != null && !slotIdStr.isEmpty()) {
                    try {
                        int slotId = Integer.parseInt(slotIdStr);
                        plugin.getPluginLogger().debug("正在打开槽位: " + slotId);
                        plugin.getStorageManager().openSlot(player, slotId);
                    } catch (NumberFormatException e) {
                        player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage("storage.messages.invalid-slot"));
                    }
                }
                break;

            case "navigation":
                String action = nbtItem.getString("gridstorage_action");
                PlayerStorage storage = plugin.getStorageManager().getPlayerStorage(player);

                plugin.getPluginLogger().debug("导航操作: " + action);
                plugin.getPluginLogger().debug("当前页: " + (storage != null ? storage.getCurrentPage() : "null") + ", 最大页: " + (storage != null ? storage.getMaxPages() : "null"));

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

    private void autoSaveSlotContents(Player player, Inventory inv) {
        ItemStack[] snapshot = new ItemStack[inv.getSize()];
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && i < snapshot.length; i++) {
            snapshot[i] = contents[i] != null ? contents[i].clone() : null;
        }

        Integer slotId = guiManager.getPlayerSlotId(player);
        guiManager.saveSlotContentsFromSnapshot(player, snapshot, slotId, true);
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

        if (guiManager.isSlotGUI(inv)) {
            plugin.getPluginLogger().info("槽位GUI 关闭，保存内容");

            ItemStack[] snapshot = new ItemStack[inv.getSize()];
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < contents.length && i < snapshot.length; i++) {
                snapshot[i] = contents[i] != null ? contents[i].clone() : null;
            }

            Integer slotId = guiManager.getPlayerSlotId(player);
            guiManager.saveSlotContentsFromSnapshot(player, snapshot, slotId, true);
            guiManager.closeSlotGUI(player);
        } else if (guiManager.isGridGUI(inv)) {
            plugin.getPluginLogger().debug("网格GUI 关闭，清理状态");
            guiManager.closeGUI(player);
            plugin.getStorageManager().closeStorage(player);
        }
    }
}
