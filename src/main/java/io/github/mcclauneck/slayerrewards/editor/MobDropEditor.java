package io.github.mcclauneck.slayerrewards.editor;

import io.github.mcclauneck.slayerrewards.editor.util.EditorUtil;
import io.github.mcengine.mceconomy.api.enums.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Manages the in-game GUI for editing mob drops.
 * <p>
 * This class handles:
 * <ul>
 * <li>Opening a paginated GUI for specific mobs.</li>
 * <li>Saving items placed in the GUI to the mob's YAML config.</li>
 * <li>Handling "Shift+Right Click" to edit drop chances via chat.</li>
 * <li>Toggling default vanilla drops on/off.</li>
 * </ul>
 */
public class MobDropEditor implements Listener {

    private final JavaPlugin plugin;
    private final File mobsFolder;

    // Tracks which player is editing which mob/page
    private final Map<UUID, EditorSession> activeSessions = new HashMap<>();
    // Tracks players who are currently typing a chance value in chat
    private final Map<UUID, Integer> pendingChanceEdit = new HashMap<>();
    // Tracks players switching pages to prevent InventoryCloseEvent from killing the session
    private final Set<UUID> isSwitchingPages = new HashSet<>();
    // Tracks players editing the money amount
    private final Set<UUID> pendingMoneyEdit = new HashSet<>();

    /**
     * Constructs a new MobDropEditor.
     *
     * @param plugin      The host plugin instance.
     * @param mobsFolder The directory containing mob YML files.
     */
    public MobDropEditor(JavaPlugin plugin, File mobsFolder) {
        this.plugin = plugin;
        this.mobsFolder = mobsFolder;
    }

    /**
     * Opens the editor GUI for a specific mob and page.
     *
     * @param player  The player opening the editor.
     * @param mobName The name of the mob file (without .yml).
     * @param page    The page number (starts at 1).
     */
    public void openEditor(Player player, String mobName, int page) {
        File file = new File(mobsFolder, mobName.toLowerCase() + ".yml");
        if (!file.exists()) {
            // Auto-create file if it doesn't exist
            try {
                file.createNewFile();
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.set("currency", "coin");
                config.set("amount", "0");
                config.save(file);
            } catch (Exception ignored) {}
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Inventory gui = Bukkit.createInventory(null, 54, "Edit Drop: " + mobName + " | P" + page);

        // Load Items
        ConfigurationSection section = config.getConfigurationSection("item_drop");
        
        // Calculate max key for pagination logic
        int maxKey = 0;
        if (section != null) {
            for (String k : section.getKeys(false)) {
                try {
                    int key = Integer.parseInt(k);
                    if (key > maxKey) maxKey = key;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Pagination Logic (45 items per page)
        int itemsPerPage = 45;
        int startKey = (page - 1) * itemsPerPage + 1;

        for (int i = 0; i < itemsPerPage; i++) {
            int currentKey = startKey + i;

            if (section != null && section.contains(String.valueOf(currentKey))) {
                ItemStack item = section.getItemStack(currentKey + ".metadata");
                if (item != null) {
                    double chance = section.getDouble(currentKey + ".chance", 100.0);
                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    lore.add(ChatColor.YELLOW + "----------------");
                    lore.add(ChatColor.GOLD + "Chance: " + chance + "%");
                    lore.add(ChatColor.GRAY + "Shift+Right Click to edit chance");
                    meta.setLore(lore);
                    item.setItemMeta(meta);

                    gui.setItem(i, item);
                }
            }
        }

        // Controls Area (Bottom Row)
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.setDisplayName(" ");
        glass.setItemMeta(gMeta);

        for (int i = 45; i < 54; i++) gui.setItem(i, glass);

        // Navigation Buttons
        if (page > 1) {
            gui.setItem(45, EditorUtil.createButton(Material.ARROW, "Previous Page"));
        }
        boolean pageFull = (gui.getItem(44) != null);
        if (maxKey > (page * itemsPerPage) || pageFull) {
            gui.setItem(53, EditorUtil.createButton(Material.ARROW, "Next Page"));
        }

        // Currency Toggle (Slot 48) - Now on every page
        String currencyStr = config.getString("currency", "coin");
        CurrencyType currency = CurrencyType.fromName(currencyStr);
        if (currency == null) currency = CurrencyType.COIN;

        // Updated: Map Enum to Material
        Material curMat = switch (currency) {
            case COPPER -> Material.COPPER_BLOCK;
            case SILVER -> Material.IRON_BLOCK;
            case GOLD -> Material.GOLD_BLOCK;
            default -> Material.SUNFLOWER; // COIN
        };
        gui.setItem(48, EditorUtil.createButton(curMat, "Currency: " + currency.getName().toUpperCase()));

        // Money Amount Editor (Slot 50) - Now on every page
        String amount = config.getString("amount", "0");
        gui.setItem(50, EditorUtil.createButton(Material.PAPER, "Reward: " + amount));

        // Default Drops Toggle (Slot 49)
        boolean cancelDefault = config.getBoolean("cancel_default_drops", false);
        gui.setItem(49, EditorUtil.createButton(cancelDefault ? Material.RED_WOOL : Material.LIME_WOOL,
                cancelDefault ? "Default Drops: OFF" : "Default Drops: ON"));

        activeSessions.put(player.getUniqueId(), new EditorSession(mobName, page));
        player.openInventory(gui);
    }

    /**
     * Handles clicks within the editor GUI.
     *
     * @param event The inventory click event.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!activeSessions.containsKey(player.getUniqueId())) return;

        String title = event.getView().getTitle();
        if (!title.startsWith("Edit Drop:")) return;

        // 1. Check for Shift + Right Click FIRST (Edit Chance)
        if (event.getClick() == ClickType.SHIFT_RIGHT && event.getClickedInventory() == event.getView().getTopInventory() && event.getSlot() < 45) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                event.setCancelled(true);
                player.setItemOnCursor(null);
                
                EditorSession session = activeSessions.get(player.getUniqueId());
                int absoluteIndex = event.getSlot() + ((session.page - 1) * 45);
                
                EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getView().getTopInventory());
                pendingChanceEdit.put(player.getUniqueId(), absoluteIndex);
                
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Enter drop chance (0-100) in chat for " + ChatColor.YELLOW + clickedItem.getType().name() + ChatColor.GREEN + ":");
                return;
            }
        }

        // 2. HARD BLOCK all interaction with the bottom row (45-53) regardless of inventory
        if (event.getRawSlot() >= 45 && event.getRawSlot() <= 53) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                EditorSession session = activeSessions.get(player.getUniqueId());

                if (event.getSlot() == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                    isSwitchingPages.add(player.getUniqueId());
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page - 1));
                } 
                else if (event.getSlot() == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                    isSwitchingPages.add(player.getUniqueId());
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page + 1));
                } 
                else if (event.getSlot() == 49) {
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    EditorUtil.toggleDefaultDrops(mobsFolder, session.mobName());
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page));
                }
                else if (event.getSlot() == 48) {
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    cycleCurrency(session.mobName);
                    player.closeInventory();
                    Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page));
                }
                else if (event.getSlot() == 50) {
                    pendingMoneyEdit.add(player.getUniqueId());
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getView().getTopInventory());
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Enter reward amount (e.g. 10 or 10-20) in chat:");
                }
            }
            return;
        }

        // 3. Block shift-clicking from player's inventory to prevent items moving into control slots
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
        }
    }

    private void cycleCurrency(String mobName) {
        File file = new File(mobsFolder, mobName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        String currentStr = config.getString("currency", "coin");
        CurrencyType current = CurrencyType.fromName(currentStr);
        if (current == null) current = CurrencyType.COIN;
        
        // Updated: Cycle using Enum values to be dynamic and safe
        CurrencyType[] values = CurrencyType.values();
        int nextIndex = (current.ordinal() + 1) % values.length;
        CurrencyType next = values[nextIndex];
        
        config.set("currency", next.getName());
        try { config.save(file); } catch (Exception ignored) {}
    }

    /**
     * Saves the page content when the inventory is closed.
     *
     * @param event The close event.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (isSwitchingPages.contains(player.getUniqueId())) {
                isSwitchingPages.remove(player.getUniqueId());
                return;
            }

            if (activeSessions.containsKey(player.getUniqueId())) {
                if (!pendingChanceEdit.containsKey(player.getUniqueId()) && !pendingMoneyEdit.contains(player.getUniqueId())) {
                    EditorSession session = activeSessions.remove(player.getUniqueId());
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    player.sendMessage(ChatColor.GREEN + "Mob drops saved!");
                }
            }
        }
    }

    /**
     * Captures chat input for setting drop chances or money amounts.
     *
     * @param event The chat event.
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        if (pendingChanceEdit.containsKey(uuid)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            int absoluteIndex = pendingChanceEdit.remove(uuid);
            EditorSession session = activeSessions.get(uuid);

            try {
                double chance = Double.parseDouble(event.getMessage());
                chance = Math.max(0, Math.min(100, chance));
                EditorUtil.updateChance(mobsFolder, session.mobName(), absoluteIndex + 1, chance);
                player.sendMessage(ChatColor.GREEN + "Chance updated to: " + ChatColor.YELLOW + chance + "%");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number. Update cancelled.");
            }
            Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page));
        } 
        else if (pendingMoneyEdit.contains(uuid)) {
            event.setCancelled(true);
            pendingMoneyEdit.remove(uuid);
            Player player = event.getPlayer();
            EditorSession session = activeSessions.get(uuid);

            File file = new File(mobsFolder, session.mobName.toLowerCase() + ".yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("amount", event.getMessage());
            try { 
                config.save(file); 
                player.sendMessage(ChatColor.GREEN + "Reward amount updated!");
            } catch (Exception ignored) {}

            Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page));
        }
    }

    /**
     * Record to hold session data.
     */
    private record EditorSession(String mobName, int page) {}
}
