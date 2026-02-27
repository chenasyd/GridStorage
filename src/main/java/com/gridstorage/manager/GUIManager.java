package com.gridstorage.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/**
 * GUI 管理器
 * 管理所有GUI的创建和更新
 */
public class GUIManager {

    private final GridStorage plugin;
    private final Map<Player, Inventory> openSlots;
    private final Map<Player, Inventory> openGrids;
    private final Map<Player, Integer> playerSlotMap; // 记录玩家当前打开的槽位ID

    // GUI 常量
    public static final int GRID_SIZE = 54;
    public static final int PAGE_SIZE = 45;
    public static final int SLOT_SIZE = 54;
    public static final int PREV_PAGE_SLOT = 45;
    public static final int NEXT_PAGE_SLOT = 53;

    public GUIManager(GridStorage plugin) {
        this.plugin = plugin;
        this.openSlots = new HashMap<>();
        this.openGrids = new HashMap<>();
        this.playerSlotMap = new HashMap<>();
    }

    /**
     * 打开主网格GUI
     */
    public void openGridGUI(Player player, PlayerStorage storage) {
        int page = storage.getCurrentPage();
        String title = plugin.getConfigManager().getMessage("storage.gui.title",
            String.valueOf(page + 1),
            String.valueOf(storage.getMaxPages()));

        Inventory inv = Bukkit.createInventory(null, GRID_SIZE, title);
        fillGridInventory(inv, storage, page);

        player.openInventory(inv);
        openGrids.put(player, inv);
    }

    /**
     * 填充网格GUI
     */
    private void fillGridInventory(Inventory inv, PlayerStorage storage, int page) {
        // 填充槽位 0-44 (对应仓库编号 1-45)
        int startSlot = page * PAGE_SIZE + 1;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int slotId = startSlot + i;
            if (slotId <= storage.getMaxSlots()) {
                inv.setItem(i, createSlotItem(slotId));
            } else {
                inv.setItem(i, null); // 空槽位
            }
        }

        // 45槽位：前一页按钮
        ItemStack prevButton = createNavigationButton(Material.ARROW, "prev");
        inv.setItem(PREV_PAGE_SLOT, prevButton);

        // 46-52槽位：玻璃板填充
        ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 46; i <= 52; i++) {
            inv.setItem(i, glass.clone());
        }

        // 53槽位：后一页按钮
        ItemStack nextButton = createNavigationButton(Material.ARROW, "next");
        inv.setItem(NEXT_PAGE_SLOT, nextButton);
    }

    /**
     * 创建槽位物品
     */
    private ItemStack createSlotItem(int slotId) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getConfigManager().getMessage("storage.gui.slot-title", String.valueOf(slotId)));
        List<String> lore = plugin.getConfigManager().getMessageList("storage.gui.slot-lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }

        item.setItemMeta(meta);

        // 使用NBT标记槽位ID（在设置ItemMeta之后）
        NBT.modify(item, nbt -> {
            nbt.setString("gridstorage_slot_id", String.valueOf(slotId));
            nbt.setString("gridstorage_type", "slot");
        });

        return item;
    }

    /**
     * 创建导航按钮
     */
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

        // 使用NBT标记按钮类型（在设置ItemMeta之后）
        NBT.modify(item, nbt -> {
            nbt.setString("gridstorage_type", "navigation");
            nbt.setString("gridstorage_action", buttonType);
        });

        return item;
    }

    /**
     * 打开单个槽位GUI
     * @param player 玩家
     * @param slot 要打开的槽位
     */
    public void openSlotGUI(Player player, StorageSlot slot) {
        int newSlotId = slot.getSlotId();

        // 检查是否已经在打开这个槽位
        Integer currentSlotId = playerSlotMap.get(player);
        if (currentSlotId != null && currentSlotId == newSlotId) {
            plugin.getPluginLogger().warning("玩家 " + player.getName() + " 尝试重复打开槽位 #" + newSlotId);
            return; // 已经打开了，不要重复打开
        }

        // 如果玩家已经打开了其他槽位GUI，先保存并关闭
        if (openSlots.containsKey(player)) {
            Inventory oldInv = openSlots.get(player);
            if (oldInv != null && currentSlotId != null) {
                // 使用当前槽位ID进行保存，避免依赖playerSlotMap
                saveSlotContents(player, oldInv, currentSlotId, true); // 自动保存，不显示消息
                plugin.getPluginLogger().debug("关闭之前的槽位 #" + currentSlotId + "，保存内容");
            }
            openSlots.remove(player);
            playerSlotMap.remove(player);
        }

        String title = plugin.getConfigManager().getMessage("storage.gui.slot-title", String.valueOf(newSlotId));
        Inventory inv = Bukkit.createInventory(null, SLOT_SIZE, title);

        // 载入槽位内容（克隆物品以避免引用问题）
        ItemStack[] contents = slot.getContents();
        for (int i = 0; i < contents.length && i < SLOT_SIZE; i++) {
            ItemStack item = contents[i];
            inv.setItem(i, item != null ? item.clone() : null);
        }

        // 记录玩家打开的槽位ID
        playerSlotMap.put(player, newSlotId);

        player.openInventory(inv);
        openSlots.put(player, inv);

        plugin.getPluginLogger().debug("玩家 " + player.getName() + " 打开了槽位 #" + newSlotId);
    }

    /**
     * 更新网格GUI（仅对当前操作玩家可见）
     */
    public void updateGridGUI(Player player, PlayerStorage storage) {
        // 重新打开GUI以更新标题
        openGridGUI(player, storage);
    }

    /**
     * 保存槽位内容
     * 将GUI inventory中的物品同步回StorageSlot
     * @param player 玩家
     * @param inv 槽位GUI inventory
     * @param slotId 槽位ID（如果为null则从playerSlotMap获取）
     * @param autoSave 是否为自动保存（控制是否输出日志）
     */
    public void saveSlotContents(Player player, Inventory inv, Integer slotId, boolean autoSave) {
        // 如果没有提供槽位ID，尝试从映射中获取
        if (slotId == null) {
            slotId = playerSlotMap.get(player);
        }

        if (slotId != null) {
            final Integer finalSlotId = slotId; // 用于lambda表达式中
            StorageSlot slot = plugin.getStorageManager().getSlot(player.getUniqueId(), slotId);
            if (slot != null) {
                // 复制inventory中的物品到StorageSlot
                ItemStack[] inventoryContents = inv.getContents();
                ItemStack[] slotContents = slot.getContents();

                int itemCount = 0;
                // 逐个复制物品（支持潜影盒等特殊容器）
                for (int i = 0; i < Math.min(inventoryContents.length, slotContents.length); i++) {
                    ItemStack item = inventoryContents[i];
                    // 克隆物品以避免引用问题
                    slotContents[i] = item != null ? item.clone() : null;
                    if (item != null) {
                        itemCount++;
                    }
                }

                slot.updateAccessTime();

                plugin.getPluginLogger().debug("保存槽位 #" + slotId + " 的内容到内存，共 " + itemCount + " 个物品");

                if (!autoSave) {
                    // 手动关闭时发送保存消息
                    player.sendMessage(getPrefix() + plugin.getConfigManager().getMessage(
                        "storage.messages.slot-saved", String.valueOf(slotId)));
                }

                // 异步保存到数据库
                if (plugin.isEnabled()) {         // 新增判断
                    plugin.getScheduler().runAsync(() -> {
                        plugin.getPluginLogger().debug("异步保存槽位 #" + finalSlotId + " 到数据库");
                        plugin.getStorageManager().savePlayerStorage(player.getUniqueId());
                    });
                } else {
                    // 插件正在停用，直接写入
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

    /**
     * 保存槽位内容（便捷方法，自动保存）
     */
    public void saveSlotContents(Player player, Inventory inv) {
        saveSlotContents(player, inv, null, false);
    }

    /**
     * 保存槽位内容（便捷方法）
     * @param player 玩家
     * @param inv 槽位GUI inventory
     * @param autoSave 是否为自动保存（控制是否输出日志）
     */
    public void saveSlotContents(Player player, Inventory inv, boolean autoSave) {
        saveSlotContents(player, inv, null, autoSave);
    }

    private String getPrefix() {
        return plugin.getConfigManager().getPrefix();
    }

    /**
     * 关闭GUI（关闭网格GUI时调用）
     * 不处理槽位GUI的关闭，槽位GUI的关闭由 onInventoryClose 事件处理
     */
    public void closeGUI(Player player) {
        // 只清理网格GUI相关数据，不处理槽位GUI
        openGrids.remove(player);

        // 注意：不清理 playerSlotMap 和 openSlots，因为槽位GUI有自己的关闭逻辑
        // 这样可以防止在关闭网格GUI时意外清理槽位GUI的数据
    }

    /**
     * 关闭槽位GUI
     */
    public void closeSlotGUI(Player player) {
        // 清理槽位GUI相关数据
        openSlots.remove(player);
        playerSlotMap.remove(player);
    }

    /**
     * 检查是否是网格GUI
     */
    public boolean isGridGUI(Inventory inv) {
        if (inv == null || inv.getSize() != GRID_SIZE) {
            return false;
        }
        // 检查是否在打开的GUI列表中
        for (Inventory openInv : openGrids.values()) {
            if (openInv == inv) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是槽位GUI
     */
    public boolean isSlotGUI(Inventory inv) {
        if (inv == null || inv.getSize() != SLOT_SIZE) {
            return false;
        }
        // 检查是否在打开的GUI列表中
        for (Inventory openInv : openSlots.values()) {
            if (openInv == inv) {
                return true;
            }
        }
        return false;
    }
}
