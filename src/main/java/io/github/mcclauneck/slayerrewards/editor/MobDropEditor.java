package io.github.mcclauneck.slayerrewards.editor;

import io.github.mcclauneck.slayerrewards.editor.util.EditorUtil;
import io.github.mcengine.mceconomy.api.enums.CurrencyType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
        
        // Translatable Title: Edit Drop: %s | P%s
        Component title = Component.translatable("mcclauneck.slayerrewards.editor.title", 
            Component.text(mobName), 
            Component.text(page));

        Inventory gui = Bukkit.createInventory(null, 54, title);

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

            // Load item from Base64 string
            String base64 = (section != null) ? section.getString(currentKey + ".metadata") : null;
            ItemStack item = null;
            
            if (base64 != null && !base64.isEmpty()) {
                item = EditorUtil.itemStackFromBase64(base64);
            }
            
            if (item != null) {
                // Ensure amount is synchronized if stored separately
                if (section != null) {
                    item.setAmount(section.getInt(currentKey + ".amount", item.getAmount()));
                }

                double chance = section != null ? section.getDouble(currentKey + ".chance", 100.0) : 100.0;
                ItemMeta meta = item.getItemMeta();
                List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                
                // Clean existing editor lore to prevent duplication (fixes the stacking issue visually)
                EditorUtil.cleanLore(lore);

                lore.add(Component.text("----------------", NamedTextColor.YELLOW));
                lore.add(Component.translatable("mcclauneck.slayerrewards.editor.lore.chance", NamedTextColor.GOLD, 
                    Component.text(chance + "%")));
                lore.add(Component.translatable("mcclauneck.slayerrewards.editor.lore.edit_hint", NamedTextColor.GRAY));
                
                meta.lore(lore);
                item.setItemMeta(meta);

                gui.setItem(i, item);
            }
        }

        // Controls Area (Bottom Row)
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.displayName(Component.empty());
        glass.setItemMeta(gMeta);

        for (int i = 45; i < 54; i++) gui.setItem(i, glass);

        // Navigation Buttons
        if (page > 1) {
            gui.setItem(45, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGNlYzgwN2RjYzE0MzYzMzRmZDRkYzlhYjM0OTM0MmY2YzUyYzllN2IyYmYzNDY3MTJkYjcyYTBkNmQ3YTQifX19", 
                Component.translatable("mcclauneck.slayerrewards.editor.btn.previous")));
        }
        boolean pageFull = (gui.getItem(44) != null);
        if (maxKey > (page * itemsPerPage) || pageFull) {
            gui.setItem(53, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTAxYzdiNTcyNjE3ODk3NGIzYjNhMDFiNDJhNTkwZTU0MzY2MDI2ZmQ0MzgwOGYyYTc4NzY0ODg0M2E3ZjVhIn19fQ==", 
                Component.translatable("mcclauneck.slayerrewards.editor.btn.next")));
        }

        // Currency Toggle (Slot 48) - Now using custom skulls
        String currencyStr = config.getString("currency", "coin");
        CurrencyType currency = CurrencyType.fromName(currencyStr);
        if (currency == null) currency = CurrencyType.COIN;

        String configTexture = plugin.getConfig().getString(currency.name().toLowerCase() + ".texture");
        String curB64 = configTexture != null ? configTexture : switch (currency) {
            case COPPER -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDJlZWM1YzVkNTAzMDJmZjEwZDBiZGI2MmQ3OWU2N2EwYWIxMTAxNjk2YWUyN2VmOWQ4MmIzNzk0M2MyYTY1YyJ9fX0=";
            case SILVER -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTM0YjI3YmZjYzhmOWI5NjQ1OTRiNjE4YjExNDZhZjY5ZGUyNzhjZTVlMmUzMDEyY2I0NzFhOWEzY2YzODcxIn19fQ==";
            case GOLD -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjBhN2I5NGM0ZTU4MWI2OTkxNTlkNDg4NDZlYzA5MTM5MjUwNjIzN2M4OWE5N2M5MzI0OGEwZDhhYmM5MTZkNSJ9fX0=";
            default -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWFmMGQ4ZDc5NGEzYTRhNWUyMGE1MjkyZWQyNTUxMzRmNzZkNGYzYTU1NTZmYzdmNDI2ZDI3YjI0NzQ3NGQ2NyJ9fX0=";
        };
        gui.setItem(48, EditorUtil.createSkullButton(curB64, 
            Component.translatable("mcclauneck.slayerrewards.editor.btn.currency", Component.text(currency.getName().toUpperCase()))));

        // Money Amount Editor (Slot 50)
        String amount = config.getString("amount", "0");
        gui.setItem(50, EditorUtil.createButton(Material.PAPER, 
            Component.translatable("mcclauneck.slayerrewards.editor.btn.reward", Component.text(amount))));

        // Default Drops Toggle (Slot 49)
        boolean cancelDefault = config.getBoolean("cancel_default_drops", false);
        String toggleB64 = cancelDefault ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWFmMjU4ZGI3MjEzMGJmZDk3ZDIxOGM4OTRiYTA4MTQ5NmQyNGQ4NTZkYzYwNDFkMTk2MDZmZmZiNGFiZjJhYyJ9fX0=" : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzBkOTY5Y2Q4YzhiMjkxNmIyNmExOTcyNTNlM2FkZmU5ODUzNzIwNDk0ZjIyYmUxOWEwODNiZjE4NGY5YzJiYyJ9fX0=";
        gui.setItem(49, EditorUtil.createSkullButton(toggleB64, 
            Component.translatable(cancelDefault ? "mcclauneck.slayerrewards.editor.btn.defaults.off" : "mcclauneck.slayerrewards.editor.btn.defaults.on")));

        // Save & Reload (Slot 52)
        gui.setItem(52, EditorUtil.createSkullButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTc0MjgxZjk2NjlmMmNkY2Y3ODQ4NDQ4YTViYjYyODIzMmVlYTJiZmJkZmM3ZDRmMjBiZGE1MDMzZDAzMzY2YSJ9fX0=", 
            Component.translatable("mcclauneck.slayerrewards.editor.btn.save")));

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

        // Verify it's an editor session before proceeding
        EditorSession session = activeSessions.get(player.getUniqueId());

        if (event.getView().getTopInventory() != event.getClickedInventory() 
            && event.getClickedInventory() != event.getView().getBottomInventory()) {
             // Clicked outside or irrelevant
        }

        // 1. Check for Shift + Right Click FIRST (Edit Chance)
        if (event.getClick() == ClickType.SHIFT_RIGHT && event.getClickedInventory() == event.getView().getTopInventory() && event.getSlot() < 45) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                event.setCancelled(true);
                player.setItemOnCursor(null);
                
                int absoluteIndex = event.getSlot() + ((session.page - 1) * 45);
                
                EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getView().getTopInventory());
                pendingChanceEdit.put(player.getUniqueId(), absoluteIndex);
                
                player.closeInventory();
                player.sendMessage(Component.translatable("mcclauneck.slayerrewards.editor.chat.enter_chance", NamedTextColor.GREEN, 
                    Component.text(clickedItem.getType().name(), NamedTextColor.YELLOW)));
                return;
            }
        }

        // 2. HARD BLOCK all interaction with the bottom row (45-53) regardless of inventory
        if (event.getRawSlot() >= 45 && event.getRawSlot() <= 53) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;

            // Optimization: Switch logic to reduce repetitive save/runTask calls
            int targetPage = session.page;
            boolean shouldSaveAndReopen = false;
            boolean requiresChatInput = false;

            switch (event.getSlot()) {
                case 45 -> { // Prev Page
                    if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                        targetPage = session.page - 1;
                        shouldSaveAndReopen = true;
                    }
                }
                case 53 -> { // Next Page
                    if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                        targetPage = session.page + 1;
                        shouldSaveAndReopen = true;
                    }
                }
                case 49 -> { // Toggle Defaults
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    EditorUtil.toggleDefaultDrops(mobsFolder, session.mobName());
                    shouldSaveAndReopen = true; // Already saved, but logic flow requires reopening
                }
                case 48 -> { // Cycle Currency
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    cycleCurrency(session.mobName);
                    shouldSaveAndReopen = true;
                }
                case 50 -> { // Edit Reward
                    pendingMoneyEdit.add(player.getUniqueId());
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getView().getTopInventory());
                    player.closeInventory();
                    player.sendMessage(Component.translatable("mcclauneck.slayerrewards.editor.chat.enter_reward", NamedTextColor.GREEN));
                    requiresChatInput = true;
                }
                case 52 -> { // Save & Reload
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                    shouldSaveAndReopen = true;
                }
            }

            if (shouldSaveAndReopen && !requiresChatInput) {
                if (targetPage != session.page) isSwitchingPages.add(player.getUniqueId());
                // For cases 48/49/52, we saved inside the case, but redundancy here is safe or can be optimized out.
                // To be strictly safe and robust:
                if (event.getSlot() == 45 || event.getSlot() == 53) {
                    EditorUtil.savePage(mobsFolder, session.mobName(), session.page(), event.getInventory());
                }
                
                player.closeInventory();
                int finalTargetPage = targetPage;
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, finalTargetPage));
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
        
        CurrencyType[] values = CurrencyType.values();
        int nextIndex = (current.ordinal() + 1) % values.length;
        
        config.set("currency", values[nextIndex].getName());
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
                    player.sendMessage(Component.translatable("mcclauneck.slayerrewards.editor.chat.saved", NamedTextColor.GREEN));
                }
            }
        }
    }

    /**
     * Captures chat input for setting drop chances or money amounts.
     * <p>
     * Replaces the deprecated AsyncPlayerChatEvent with AsyncChatEvent (Paper API).
     * </p>
     *
     * @param event The chat event.
     */
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        if (!pendingChanceEdit.containsKey(uuid) && !pendingMoneyEdit.contains(uuid)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        EditorSession session = activeSessions.get(uuid);

        if (session == null) return; // Safety check

        // Convert Component message to plain text for parsing numbers
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (pendingChanceEdit.containsKey(uuid)) {
            int absoluteIndex = pendingChanceEdit.remove(uuid);
            try {
                double chance = Double.parseDouble(message);
                chance = Math.max(0, Math.min(100, chance));
                EditorUtil.updateChance(mobsFolder, session.mobName(), absoluteIndex + 1, chance);
                player.sendMessage(Component.translatable("mcclauneck.slayerrewards.editor.chat.updated_chance", NamedTextColor.GREEN, 
                    Component.text(chance + "%", NamedTextColor.YELLOW)));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.translatable("mcclauneck.slayerrewards.editor.chat.invalid_number", NamedTextColor.RED));
            }
        } else if (pendingMoneyEdit.contains(uuid)) {
            pendingMoneyEdit.remove(uuid);
            File file = new File(mobsFolder, session.mobName.toLowerCase() + ".yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("amount", message);
            try { 
                config.save(file); 
                player.sendMessage(Component.translatable("mcclauneck.slayerrewards.editor.chat.updated_reward", NamedTextColor.GREEN));
            } catch (Exception ignored) {}
        }

        Bukkit.getScheduler().runTask(plugin, () -> openEditor(player, session.mobName, session.page));
    }

    /**
     * Record to hold session data.
     */
    private record EditorSession(String mobName, int page) {}
}
