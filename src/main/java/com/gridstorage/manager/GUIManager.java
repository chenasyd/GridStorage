package com.gridstorage.manager;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.gridstorage.GridStorage;
import com.gridstorage.gui.GridGuiHolder;
import com.gridstorage.gui.StorageSlotHolder;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.model.StorageSlot;
import com.gridstorage.nbt.GridItemTags;

public class GUIManager {

    private final GridStorage plugin;

    public static final int GRID_SIZE = 54;
    public static final int PAGE_SIZE = 45;
    public static final int SLOT_SIZE = 54;
    public static final int PREV_PAGE_SLOT = 45;
    public static final int NEXT_PAGE_SLOT = 53;

    public GUIManager(GridStorage plugin) {
        this.plugin = plugin;
    }

    public void openGridGUI(Player player, PlayerStorage storage) {
        int page = storage.getCurrentPage();
        String title = plugin.getConfigManager().getMessage("storage.gui.title",
                String.valueOf(page + 1),
                String.valueOf(storage.getMaxPages()));

        GridGuiHolder holder = new GridGuiHolder(player.getUniqueId(), page);
        Inventory inv = Bukkit.createInventory(holder, GRID_SIZE, title);
        holder.setInventory(inv);
        fillGridInventory(inv, storage, page);

        player.openInventory(inv);
    }

    private void fillGridInventory(Inventory inv, PlayerStorage storage, int page) {
        int startSlot = page * PAGE_SIZE + 1;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int slotId = startSlot + i;
            if (slotId <= storage.getMaxSlots()) {
                inv.setItem(i, createSlotItem(slotId));
            } else {
                inv.setItem(i, null);
            }
        }

        inv.setItem(PREV_PAGE_SLOT, createNavigationButton(Material.ARROW, "prev"));

        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 46; i <= 52; i++) {
            inv.setItem(i, glass.clone());
        }

        inv.setItem(NEXT_PAGE_SLOT, createNavigationButton(Material.ARROW, "next"));
    }

    private ItemStack createSlotItem(int slotId) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getConfigManager().getMessage("storage.gui.slot-title", String.valueOf(slotId)));
        List<String> lore = plugin.getConfigManager().getMessageList("storage.gui.slot-lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        GridItemTags.markSlot(item, slotId);
        return item;
    }

    private ItemStack createNavigationButton(Material material, String buttonType) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String displayName = plugin.getConfigManager().getMessage("storage.gui.buttons." + buttonType + "-page");
        List<String> lore = plugin.getConfigManager().getMessageList("storage.gui.buttons." + buttonType + "-lore");
        meta.setDisplayName(displayName);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        GridItemTags.markNavigation(item, buttonType);
        return item;
    }

    public void openSlotGUI(Player player, StorageSlot slot) {
        UUID uuid = player.getUniqueId();
        int newSlotId = slot.getSlotId();

        Integer current = plugin.getStorageManager().getSessionSlot(uuid);
        if (current != null && current == newSlotId
                && player.getOpenInventory().getTopInventory().getHolder() instanceof StorageSlotHolder) {
            plugin.getPluginLogger().debug("玩家 " + player.getName() + " 已打开槽位 #" + newSlotId);
            return;
        }

        String title = plugin.getConfigManager().getMessage("storage.gui.slot-title", String.valueOf(newSlotId));
        StorageSlotHolder holder = new StorageSlotHolder(uuid, newSlotId);
        Inventory inv = Bukkit.createInventory(holder, SLOT_SIZE, title);
        holder.setInventory(inv);

        ItemStack[] contents = slot.getContents();
        for (int i = 0; i < contents.length && i < SLOT_SIZE; i++) {
            ItemStack item = contents[i];
            inv.setItem(i, item != null ? item.clone() : null);
        }
        holder.captureOpenSnapshot(inv.getContents());

        player.openInventory(inv);
        plugin.getPluginLogger().debug("玩家 " + player.getName() + " 打开了槽位 #" + newSlotId);
    }

    public void updateGridGUI(Player player, PlayerStorage storage) {
        openGridGUI(player, storage);
    }

    /**
     * Apply inventory snapshot to memory and schedule one async save.
     */
    public void saveSlotContentsFromSnapshot(Player player, ItemStack[] snapshot, int slotId, boolean autoSave) {
        plugin.getStorageManager().applySnapshotToSlot(player.getUniqueId(), slotId, snapshot);
        if (!autoSave && player.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("storage.messages.slot-saved", String.valueOf(slotId)));
        }
        plugin.getStorageManager().savePlayerStorageAsync(player.getUniqueId());
    }

    public Integer getPlayerSlotId(Player player) {
        return plugin.getStorageManager().getSessionSlot(player.getUniqueId());
    }

    public boolean isGridGUI(Inventory inv) {
        return inv != null && inv.getHolder() instanceof GridGuiHolder;
    }

    public boolean isSlotGUI(Inventory inv) {
        return inv != null && inv.getHolder() instanceof StorageSlotHolder;
    }

    public StorageSlotHolder asSlotHolder(Inventory inv) {
        if (inv != null && inv.getHolder() instanceof StorageSlotHolder holder) {
            return holder;
        }
        return null;
    }

    public GridGuiHolder asGridHolder(Inventory inv) {
        if (inv != null && inv.getHolder() instanceof GridGuiHolder holder) {
            return holder;
        }
        return null;
    }
}
