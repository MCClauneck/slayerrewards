package io.github.mcclauneck.slayerrewards.editor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
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

    /**
     * Constructs a new MobDropEditor.
     *
     * @param plugin     The host plugin instance.
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
            player.sendMessage(ChatColor.RED + "Mob file not found: " + mobName);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Inventory gui = Bukkit.createInventory(null, 54, "Edit Drop: " + mobName + " | P" + page);

        // Load Items
        ConfigurationSection section = config.getConfigurationSection("item_drop");
        List<Integer> keys = new ArrayList<>();
        if (section != null) {
            section.getKeys(false).forEach(k -> keys.add(Integer.parseInt(k)));
            Collections.sort(keys); // Ensure numeric order
        }

        // Pagination Logic (45 items per page)
        int itemsPerPage = 45;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, keys.size());

        for (int i = startIndex; i < endIndex; i++) {
            int key = keys.get(i);
            ItemStack item = section.getItemStack(key + ".metadata");
            if (item != null) {
                // Inject Chance into Lore for visibility
                double chance = section.getDouble(key + ".chance", 100.0);
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add(ChatColor.YELLOW + "----------------");
                lore.add(ChatColor.GOLD + "Chance: " + chance + "%");
                lore.add(ChatColor.GRAY + "Shift+Right Click to edit chance");
                meta.setLore(lore);
                item.setItemMeta(meta);

                gui.setItem(i - startIndex, item);
            }
        }

        // Controls Area (Bottom Row)
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.setDisplayName(" ");
        glass.setItemMeta(gMeta);

        for (int i = 45; i < 54; i++) gui.setItem(i, glass); // Fill toolbar background

        // Previous Page Button (Slot 48)
        if (page > 1) {
            gui.setItem(48, createButton(Material.ARROW, "Previous Page"));
        }

        // Next Page Button (Slot 50)
        // Show if we have more items than this page can hold
        if (keys.size() > (startIndex + itemsPerPage)) {
            gui.setItem(50, createButton(Material.ARROW, "Next Page"));
        }

        // Default Drops Toggle (Slot 49)
        boolean cancelDefault = config.getBoolean("cancel_default_drops", false);
        gui.setItem(49, createButton(cancelDefault ? Material.RED_WOOL : Material.LIME_WOOL,
                cancelDefault ? "Default Drops: OFF" : "Default Drops: ON"));

        activeSessions.put(player.getUniqueId(), new EditorSession(mobName, page));
        player.openInventory(gui);
    }

    /**
     * Helper to create simple control buttons.
     */
    private ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + name);
        item.setItemMeta(meta);
        return item;
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

        EditorSession session = activeSessions.get(player.getUniqueId());

        // Handle Toolbar Clicks (Bottom Row)
        if (event.getClickedInventory() == event.getView().getTopInventory() && event.getSlot() >= 45) {
            event.setCancelled(true);

            if (event.getSlot() == 48 && event.getCurrentItem().getType() == Material.ARROW) {
                savePage(player, session, event.getInventory()); // Save before switch
                openEditor(player, session.mobName, session.page - 1);
            } else if (event.getSlot() == 50 && event.getCurrentItem().getType() == Material.ARROW) {
                savePage(player, session, event.getInventory()); // Save before switch
                openEditor(player, session.mobName, session.page + 1);
            } else if (event.getSlot() == 49) {
                toggleDefaultDrops(player, session);
                openEditor(player, session.mobName, session.page); // Refresh UI
            }
            return;
        }

        // Handle Chance Editing (Shift + Right Click on an item)
        if (event.isShiftClick() && event.isRightClick() && event.getSlot() < 45 && event.getClickedInventory() == event.getView().getTopInventory()) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                event.setCancelled(true);
                player.closeInventory();
                
                // Calculate absolute index (Page Offset + Slot)
                int absoluteIndex = event.getSlot() + ((session.page - 1) * 45);
                pendingChanceEdit.put(player.getUniqueId(), absoluteIndex);
                
                player.sendMessage(ChatColor.GREEN + "Enter drop chance (0-100) in chat:");
            }
        }
    }

    /**
     * Saves the page content when the inventory is closed.
     *
     * @param event The close event.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && activeSessions.containsKey(player.getUniqueId())) {
            // Check if we are closing to edit chat; if so, don't remove session/save yet
            if (!pendingChanceEdit.containsKey(player.getUniqueId())) {
                EditorSession session = activeSessions.remove(player.getUniqueId());
                savePage(player, session, event.getInventory());
                player.sendMessage(ChatColor.GREEN + "Mob drops saved!");
            }
        }
    }

    /**
     * Captures chat input for setting drop chances.
     *
     * @param event The chat event.
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (pendingChanceEdit.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            int absoluteIndex = pendingChanceEdit.remove(player.getUniqueId());

            try {
                double chance = Double.parseDouble(event.getMessage());
                if (chance < 0) chance = 0;
                if (chance > 100) chance = 100;

                // Save chance to file directly
                EditorSession session = activeSessions.get(player.getUniqueId());
                updateChance(session.mobName, absoluteIndex + 1, chance); // +1 because YAML keys start at 1

                // Re-open editor
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page));

            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number. Edit cancelled.");
                EditorSession session = activeSessions.get(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page));
            }
        }
    }

    /**
     * Saves the items in the current page to the YAML file.
     *
     * @param player  The player saving.
     * @param session The active session data.
     * @param inv     The inventory being saved.
     */
    private void savePage(Player player, EditorSession session, Inventory inv) {
        File file = new File(mobsFolder, session.mobName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        int itemsPerPage = 45;
        int startIndex = (session.page - 1) * itemsPerPage;

        for (int i = 0; i < itemsPerPage; i++) {
            ItemStack item = inv.getItem(i);
            int key = startIndex + i + 1; // YAML keys 1...N

            if (item != null && item.getType() != Material.AIR) {
                // Strip the helper lore (Chance/Divider) before saving to disk
                ItemStack toSave = item.clone();
                ItemMeta meta = toSave.getItemMeta();
                List<String> lore = meta.getLore();
                
                if (lore != null && lore.size() >= 3) {
                    // Remove last 3 lines injected by openEditor
                    // Logic assumes user didn't modify these exact lines manually
                    int size = lore.size();
                    if (lore.get(size - 2).contains("Chance:")) {
                         lore.remove(size - 1); // Help text
                         lore.remove(size - 2); // Chance text
                         lore.remove(size - 3); // Divider
                    }
                    meta.setLore(lore);
                    toSave.setItemMeta(meta);
                }

                config.set("item_drop." + key + ".metadata", toSave);
                config.set("item_drop." + key + ".amount", item.getAmount());
                
                // Preserve existing chance if present, else default to 100.0
                if (!config.contains("item_drop." + key + ".chance")) {
                    config.set("item_drop." + key + ".chance", 100.0);
                }
            } else {
                config.set("item_drop." + key, null); // Remove if empty slot
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Toggles the 'cancel_default_drops' boolean in the config.
     */
    private void toggleDefaultDrops(Player player, EditorSession session) {
        File file = new File(mobsFolder, session.mobName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean current = config.getBoolean("cancel_default_drops", false);
        config.set("cancel_default_drops", !current);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the chance value for a specific item index.
     */
    private void updateChance(String mobName, int key, double chance) {
        File file = new File(mobsFolder, mobName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("item_drop." + key + ".chance", chance);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Record to hold session data.
     */
    private record EditorSession(String mobName, int page) {}
}
