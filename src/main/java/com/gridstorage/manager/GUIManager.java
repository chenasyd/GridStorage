package com.gridstorage.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.gridstorage.GridStorage;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.model.StorageSlot;

import de.tr7zw.nbtapi.NBT;

public class GUIManager {

    private final GridStorage plugin;
    private final Map<UUID, Inventory> openSlots;
    private final Map<UUID, Inventory> openGrids;
    private final Map<UUID, Integer> playerSlotMap;

    public static final int GRID_SIZE = 54;
    public static final int PAGE_SIZE = 45;
    public static final int SLOT_SIZE = 54;
    public static final int PREV_PAGE_SLOT = 45;
    public static final int NEXT_PAGE_SLOT = 53;

    public GUIManager(GridStorage plugin) {
        this.plugin = plugin;
        this.openSlots = new ConcurrentHashMap<>();
        this.openGrids = new ConcurrentHashMap<>();
        this.playerSlotMap = new ConcurrentHashMap<>();
    }

    public void openGridGUI(Player player, PlayerStorage storage) {
        int page = storage.getCurrentPage();
        String title = plugin.getConfigManager().getMessage("storage.gui.title",
            String.valueOf(page + 1),
            String.valueOf(storage.getMaxPages()));

        Inventory inv = Bukkit.createInventory(null, GRID_SIZE, title);
        fillGridInventory(inv, storage, page);

        player.openInventory(inv);
        openGrids.put(player.getUniqueId(), inv);
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

        ItemStack prevButton = createNavigationButton(Material.ARROW, "prev");
        inv.setItem(PREV_PAGE_SLOT, prevButton);

        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 46; i <= 52; i++) {
            inv.setItem(i, glass.clone());
        }

        ItemStack nextButton = createNavigationButton(Material.ARROW, "next");
        inv.setItem(NEXT_PAGE_SLOT, nextButton);
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

        NBT.modify(item, nbt -> {
            nbt.setString("gridstorage_slot_id", String.valueOf(slotId));
            nbt.setString("gridstorage_type", "slot");
        });

        return item;
    }

    private ItemStack createNavigationButton(Material material, String buttonType) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String nameKey = "storage.gui.buttons." + buttonType + "-page";
        String loreKey = "storage.gui.buttons." + buttonType + "-lore";

        String displayName = plugin.getConfigManager().getMessage(nameKey);
        List<String> lore = plugin.getConfigManager().getMessageList(loreKey);

        meta.setDisplayName(displayName);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }

        item.setItemMeta(meta);

        NBT.modify(item, nbt -> {
            nbt.setString("gridstorage_type", "navigation");
            nbt.setString("gridstorage_action", buttonType);
        });

        return item;
    }

    public void openSlotGUI(Player player, StorageSlot slot) {
        UUID uuid = player.getUniqueId();
        int newSlotId = slot.getSlotId();

        Integer currentSlotId = playerSlotMap.get(uuid);
        if (currentSlotId != null && currentSlotId == newSlotId) {
            plugin.getPluginLogger().warning("玩家 " + player.getName() + " 尝试重复打开槽位 #" + newSlotId);
            return;
        }

        if (openSlots.containsKey(uuid)) {
            Inventory oldInv = openSlots.get(uuid);
            if (oldInv != null && currentSlotId != null) {
                saveSlotContents(player, oldInv, currentSlotId, true);
                plugin.getPluginLogger().debug("关闭之前的槽位 #" + currentSlotId + "，保存内容");
            }
            openSlots.remove(uuid);
            playerSlotMap.remove(uuid);
        }

        String title = plugin.getConfigManager().getMessage("storage.gui.slot-title", String.valueOf(newSlotId));
        Inventory inv = Bukkit.createInventory(null, SLOT_SIZE, title);

        ItemStack[] contents = slot.getContents();
        for (int i = 0; i < contents.length && i < SLOT_SIZE; i++) {
            ItemStack item = contents[i];
            inv.setItem(i, item != null ? item.clone() : null);
        }

        playerSlotMap.put(uuid, newSlotId);

        player.openInventory(inv);
        openSlots.put(uuid, inv);

        plugin.getPluginLogger().debug("玩家 " + player.getName() + " 打开了槽位 #" + newSlotId);
    }

    public void updateGridGUI(Player player, PlayerStorage storage) {
        openGridGUI(player, storage);
    }

    public void saveSlotContents(Player player, Inventory inv, Integer slotId, boolean autoSave) {
        if (slotId == null) {
            slotId = playerSlotMap.get(player.getUniqueId());
        }

        if (slotId != null) {
            final Integer finalSlotId = slotId;
            StorageSlot slot = plugin.getStorageManager().getSlot(player.getUniqueId(), slotId);
            if (slot != null) {
                ItemStack[] inventoryContents = inv.getContents();
                ItemStack[] slotContents = slot.getContents();

                int itemCount = 0;
                for (int i = 0; i < Math.min(inventoryContents.length, slotContents.length); i++) {
                    ItemStack item = inventoryContents[i];
                    slotContents[i] = item != null ? item.clone() : null;
                    if (item != null) {
                        itemCount++;
                    }
                }

                slot.updateAccessTime();

                plugin.getPluginLogger().debug("保存槽位 #" + slotId + " 的内容到内存，共 " + itemCount + " 个物品");

                if (!autoSave) {
                    player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage(
                        "storage.messages.slot-saved", String.valueOf(slotId)));
                }

                if (plugin.isEnabled()) {
                    plugin.getScheduler().runAsync(() -> {
                        plugin.getPluginLogger().debug("异步保存槽位 #" + finalSlotId + " 到数据库");
                        plugin.getStorageManager().savePlayerStorage(player.getUniqueId());
                    });
                } else {
                    plugin.getPluginLogger().info("插件禁用期间同步保存槽位 #" + finalSlotId);
                    plugin.getStorageManager().savePlayerStorage(player.getUniqueId());
                }
            } else {
                plugin.getPluginLogger().warning("槽位 #" + slotId + " 不存在，无法保存");
            }
        } else {
            plugin.getPluginLogger().warning("无法确定槽位ID，无法保存内容");
        }
    }

    public void saveSlotContentsFromSnapshot(Player player, ItemStack[] snapshot, Integer slotId, boolean autoSave) {
        if (slotId == null) {
            slotId = playerSlotMap.get(player.getUniqueId());
        }

        if (slotId == null) {
            plugin.getPluginLogger().warning("无法确定槽位ID，无法保存内容");
            return;
        }

        final Integer finalSlotId = slotId;
        StorageSlot slot = plugin.getStorageManager().getSlot(player.getUniqueId(), slotId);
        if (slot == null) {
            plugin.getPluginLogger().warning("槽位 #" + slotId + " 不存在，无法保存");
            return;
        }

        ItemStack[] slotContents = slot.getContents();
        int itemCount = 0;
        for (int i = 0; i < Math.min(snapshot.length, slotContents.length); i++) {
            slotContents[i] = snapshot[i] != null ? snapshot[i].clone() : null;
            if (snapshot[i] != null) {
                itemCount++;
            }
        }

        slot.updateAccessTime();

        plugin.getPluginLogger().debug("保存槽位 #" + slotId + " 的内容到内存（快照方式），共 " + itemCount + " 个物品");

        if (!autoSave) {
            player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage(
                "storage.messages.slot-saved", String.valueOf(slotId)));
        }

        if (plugin.isEnabled()) {
            plugin.getScheduler().runAsync(() -> {
                plugin.getPluginLogger().debug("异步保存槽位 #" + finalSlotId + " 到数据库");
                plugin.getStorageManager().savePlayerStorage(player.getUniqueId());
            });
        } else {
            plugin.getPluginLogger().info("插件禁用期间同步保存槽位 #" + finalSlotId);
            plugin.getStorageManager().savePlayerStorage(player.getUniqueId());
        }
    }

    public void saveSlotContents(Player player, Inventory inv) {
        saveSlotContents(player, inv, null, false);
    }

    public void saveSlotContents(Player player, Inventory inv, boolean autoSave) {
        saveSlotContents(player, inv, null, autoSave);
    }

    public Integer getPlayerSlotId(Player player) {
        return playerSlotMap.get(player.getUniqueId());
    }

    private String getPrefix() {
        return plugin.getConfigManager().getPrefix();
    }

    public void closeGUI(Player player) {
        openGrids.remove(player.getUniqueId());
    }

    public void closeSlotGUI(Player player) {
        openSlots.remove(player.getUniqueId());
        playerSlotMap.remove(player.getUniqueId());
    }

    public boolean isGridGUI(Inventory inv) {
        if (inv == null || inv.getSize() != GRID_SIZE) {
            return false;
        }
        for (Inventory openInv : openGrids.values()) {
            if (openInv == inv) {
                return true;
            }
        }
        return false;
    }

    public boolean isSlotGUI(Inventory inv) {
        if (inv == null || inv.getSize() != SLOT_SIZE) {
            return false;
        }
        for (Inventory openInv : openSlots.values()) {
            if (openInv == inv) {
                return true;
            }
        }
        return false;
    }
}
