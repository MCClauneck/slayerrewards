package io.github.mcclauneck.slayerrewards.editor.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Utility class for MobDropEditor operations.
 * <p>
 * Handles file I/O and item manipulation logic separated from the event listener.
 * </p>
 */
public class EditorUtil {

    private EditorUtil() {
        // Prevent instantiation
    }

    /**
     * Helper to create simple control buttons.
     *
     * @param mat  The material of the button.
     * @param name The display name of the button.
     * @return The constructed ItemStack.
     */
    public static ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Saves the items in the current page to the YAML file.
     *
     * @param mobsFolder The directory containing mob files.
     * @param mobName    The name of the mob.
     * @param page       The current page number.
     * @param inv        The inventory being saved.
     */
    public static void savePage(File mobsFolder, String mobName, int page, Inventory inv) {
        File file = new File(mobsFolder, mobName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        int itemsPerPage = 45;
        int startIndex = (page - 1) * itemsPerPage;

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
     *
     * @param mobsFolder The directory containing mob files.
     * @param mobName    The name of the mob.
     */
    public static void toggleDefaultDrops(File mobsFolder, String mobName) {
        File file = new File(mobsFolder, mobName.toLowerCase() + ".yml");
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
     *
     * @param mobsFolder The directory containing mob files.
     * @param mobName    The name of the mob.
     * @param key        The item key index.
     * @param chance     The new chance value.
     */
    public static void updateChance(File mobsFolder, String mobName, int key, double chance) {
        File file = new File(mobsFolder, mobName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("item_drop." + key + ".chance", chance);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
