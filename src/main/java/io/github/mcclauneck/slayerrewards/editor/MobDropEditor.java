package io.github.mcclauneck.slayerrewards.editor;

import io.github.mcclauneck.slayerrewards.editor.util.EditorUtil;
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
            gui.setItem(48, EditorUtil.createButton(Material.ARROW, "Previous Page"));
        }

        // Next Page Button (Slot 50)
        // Show if we have more items than this page can hold OR if the page is full (allows creating new page)
        if (keys.size() >= (startIndex + itemsPerPage)) {
            gui.setItem(50, EditorUtil.createButton(Material.ARROW, "Next Page"));
        }

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

        EditorSession session = activeSessions.get(player.getUniqueId());

        // Handle Toolbar Clicks (Bottom Row)
        if (event.getClickedInventory() == event.getView().getTopInventory() && event.getSlot() >= 45) {
            event.setCancelled(true);

            if (event.getSlot() == 48 && event.getCurrentItem().getType() == Material.ARROW) {
                // Page Switch Logic
                isSwitchingPages.add(player.getUniqueId()); // Prevent session kill
                EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                openEditor(player, session.mobName, session.page - 1);
            } 
            else if (event.getSlot() == 50 && event.getCurrentItem().getType() == Material.ARROW) {
                // Page Switch Logic
                isSwitchingPages.add(player.getUniqueId()); // Prevent session kill
                EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                openEditor(player, session.mobName, session.page + 1);
            } 
            else if (event.getSlot() == 49) {
                EditorUtil.toggleDefaultDrops(mobsFolder, session.mobName());

                File file = new File(mobsFolder, session.mobName.toLowerCase() + ".yml");
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                boolean cancelDefault = config.getBoolean("cancel_default_drops", false);

                event.getClickedInventory().setItem(49, EditorUtil.createButton(cancelDefault ? Material.RED_WOOL : Material.LIME_WOOL,
                        cancelDefault ? "Default Drops: OFF" : "Default Drops: ON"));
            }
            return;
        }

        // Handle Chance Editing (Shift + Right Click on an item)
        if (event.isShiftClick() && event.isRightClick() && event.getSlot() < 45 && event.getClickedInventory() == event.getView().getTopInventory()) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                event.setCancelled(true);

                // Calculate absolute index (Page Offset + Slot)
                int absoluteIndex = event.getSlot() + ((session.page - 1) * 45);
                pendingChanceEdit.put(player.getUniqueId(), absoluteIndex);

                // Save page state before closing so the item exists in YAML
                EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getView().getTopInventory());

                player.closeInventory();
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
        if (event.getPlayer() instanceof Player player) {
            // If switching pages, ignore this close event (do not remove session)
            if (isSwitchingPages.contains(player.getUniqueId())) {
                isSwitchingPages.remove(player.getUniqueId());
                return;
            }

            if (activeSessions.containsKey(player.getUniqueId())) {
                // Check if we are closing to edit chat; if so, don't remove session/save yet
                if (!pendingChanceEdit.containsKey(player.getUniqueId())) {
                    EditorSession session = activeSessions.remove(player.getUniqueId());
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    player.sendMessage(ChatColor.GREEN + "Mob drops saved!");
                }
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
                EditorUtil.updateChance(mobsFolder, session.mobName(), absoluteIndex + 1, chance); // +1 because YAML keys start at 1

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
     * Record to hold session data.
     */
    private record EditorSession(String mobName, int page) {}
}
