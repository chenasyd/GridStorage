package com.gridstorage.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.gridstorage.GridStorage;
import com.gridstorage.gui.GridGuiHolder;
import com.gridstorage.gui.StorageSlotHolder;
import com.gridstorage.manager.GUIManager;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.nbt.GridItemTags;

public class GUIListener implements Listener {

    private final GridStorage plugin;
    private final GUIManager guiManager;

    public GUIListener(GridStorage plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGuiManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (guiManager.isGridGUI(top)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta()) {
                handleGridClick(player, clicked);
            }
            return;
        }

        StorageSlotHolder holder = guiManager.asSlotHolder(top);
        if (holder == null) {
            return;
        }

        if (event.getAction() != org.bukkit.event.inventory.InventoryAction.NOTHING) {
            plugin.getScheduler().runDelayedAtEntity(player, () -> autoSaveSlotContents(player, top, holder), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (guiManager.isGridGUI(top)) {
            event.setCancelled(true);
            return;
        }

        StorageSlotHolder holder = guiManager.asSlotHolder(top);
        if (holder == null) {
            return;
        }

        boolean touchesTop = false;
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                touchesTop = true;
                break;
            }
        }
        if (touchesTop) {
            plugin.getScheduler().runDelayedAtEntity(player, () -> autoSaveSlotContents(player, top, holder), 1L);
        }
    }

    private void handleGridClick(Player player, ItemStack clicked) {
        String type = GridItemTags.getType(clicked);
        if (type == null || type.isEmpty()) {
            return;
        }

        switch (type) {
            case "slot" -> {
                String slotIdStr = GridItemTags.getSlotId(clicked);
                if (slotIdStr == null || slotIdStr.isEmpty()) {
                    return;
                }
                try {
                    int slotId = Integer.parseInt(slotIdStr);
                    plugin.getStorageManager().openSlot(player, slotId);
                } catch (NumberFormatException e) {
                    player.sendMessage(getPrefix()
                            + plugin.getConfigManager().getMessage("storage.messages.invalid-slot"));
                }
            }
            case "navigation" -> {
                String action = GridItemTags.getAction(clicked);
                PlayerStorage storage = plugin.getStorageManager().getCachedStorage(player.getUniqueId());
                if (storage == null) {
                    storage = plugin.getStorageManager().getPlayerStorage(player);
                }
                if (storage == null || action == null || action.isEmpty()) {
                    return;
                }
                if ("prev".equals(action)) {
                    if (storage.getCurrentPage() > 0) {
                        storage.previousPage();
                        guiManager.updateGridGUI(player, storage);
                    } else {
                        player.sendMessage(getPrefix()
                                + plugin.getConfigManager().getMessage("storage.gui.first-page"));
                    }
                } else if ("next".equals(action)) {
                    if (storage.getCurrentPage() < storage.getMaxPages() - 1) {
                        storage.nextPage();
                        guiManager.updateGridGUI(player, storage);
                    } else {
                        player.sendMessage(getPrefix()
                                + plugin.getConfigManager().getMessage("storage.gui.last-page"));
                    }
                }
            }
            default -> {
            }
        }
    }

    private void autoSaveSlotContents(Player player, Inventory inv, StorageSlotHolder holder) {
        if (!player.isOnline()) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof StorageSlotHolder open) || open.getSlotId() != holder.getSlotId()) {
            return;
        }
        ItemStack[] snapshot = cloneContents(inv.getContents());
        guiManager.saveSlotContentsFromSnapshot(player, snapshot, holder.getSlotId(), true);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] snapshot = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            snapshot[i] = contents[i] != null ? contents[i].clone() : null;
        }
        return snapshot;
    }

    private String getPrefix() {
        return plugin.getConfigManager().getPrefix();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inv = event.getInventory();
        StorageSlotHolder slotHolder = guiManager.asSlotHolder(inv);
        if (slotHolder != null) {
            plugin.getStorageManager().handleSlotClose(player, slotHolder, inv);
            return;
        }

        GridGuiHolder gridHolder = guiManager.asGridHolder(inv);
        if (gridHolder != null) {
            plugin.getStorageManager().closeStorage(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getStorageManager().releaseSessionIfIdle(event.getPlayer().getUniqueId());
    }
}
