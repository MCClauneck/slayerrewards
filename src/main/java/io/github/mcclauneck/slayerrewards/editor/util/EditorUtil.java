package io.github.mcclauneck.slayerrewards.editor.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

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
    public static ItemStack createButton(Material mat, Component name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.colorIfAbsent(NamedTextColor.WHITE));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a player head button with a custom Base64 texture.
     *
     * @param b64  The Base64 texture string.
     * @param name The display name of the button.
     * @return The constructed ItemStack.
     */
    public static ItemStack createSkullButton(String b64, Component name) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        // Use native Bukkit PlayerProfile API (No AuthLib needed)
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();

        try {
            // Decode Base64 to get the URL inside the JSON
            String decoded = new String(Base64.getDecoder().decode(b64));
            // Simple extraction of the URL from the skin JSON
            String urlString = decoded.substring(decoded.indexOf("http"), decoded.lastIndexOf("\""));
            textures.setSkin(new URL(urlString));
            profile.setTextures(textures);
        } catch (MalformedURLException | IllegalArgumentException e) {
            e.printStackTrace();
        }

        meta.setOwnerProfile(profile);
        meta.displayName(name.colorIfAbsent(NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Removes the editor helper lore (Divider, Chance, Edit Hint) from the list.
     * Loops until all instances are removed to clean up existing duplicates.
     *
     * @param lore The lore list to clean.
     */
    public static void cleanLore(List<Component> lore) {
        if (lore == null || lore.isEmpty()) return;

        // Loop to strip multiple stacks if they exist
        while (lore.size() >= 3) {
            int i = lore.size();
            Component line3 = lore.get(i - 1); // Edit Hint
            // We check the last line. If it matches our hint, we assume the previous 2 are also ours.
            
            boolean match = false;
            // Check by Key (Reliable for Component based items)
            if (line3 instanceof TranslatableComponent tc && tc.key().equals("slayerrewards.editor.lore.edit_hint")) {
                match = true;
            }
            // Check by Text (Fallback if serialized/deserialized differently)
            else {
                String plain = PlainTextComponentSerializer.plainText().serialize(line3);
                if (plain.contains("Shift+Right Click")) {
                    match = true;
                }
            }

            if (match) {
                lore.remove(i - 1); // Hint
                lore.remove(i - 2); // Chance
                lore.remove(i - 3); // Divider
            } else {
                break; // Stop if the last line isn't ours
            }
        }
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
                // Create a FRESH item copy to modify lore without affecting the GUI item (optional safety)
                ItemStack toSave = new ItemStack(item); 
                ItemMeta meta = toSave.getItemMeta();
                List<Component> lore = meta.lore();

                if (lore != null) {
                    cleanLore(lore); // Robust cleanup using shared logic
                    meta.lore(lore);
                    toSave.setItemMeta(meta);
                }

                config.set("item_drop." + key + ".metadata", itemStackToBase64(toSave));
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

    /**
     * Serializes an ItemStack to a Base64 string using YAML serialization.
     *
     * @param item The item to serialize.
     * @return The Base64 encoded string.
     */
    public static String itemStackToBase64(ItemStack item) {
        YamlConfiguration tempConfig = new YamlConfiguration();
        tempConfig.set("i", item);
        String yamlString = tempConfig.saveToString();
        return Base64.getEncoder().encodeToString(yamlString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes a Base64 string back into an ItemStack using YAML deserialization.
     *
     * @param data The Base64 encoded string.
     * @return The deserialized ItemStack, or null if invalid.
     */
    public static ItemStack itemStackFromBase64(String data) {
        try {
            String yamlString = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            YamlConfiguration tempConfig = new YamlConfiguration();
            tempConfig.loadFromString(yamlString);
            return tempConfig.getItemStack("i");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
