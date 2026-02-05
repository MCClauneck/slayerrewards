package io.github.mcclauneck.slayerrewards.command;

import io.github.mcclauneck.slayerrewards.editor.MobDropEditor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the execution of the main /slayerrewards command.
 * <p>
 * This executor serves as the entry point for administrative tasks,
 * specifically opening the drop editor GUI.
 * </p>
 */
public class SlayerRewardsCommand implements CommandExecutor {

    /**
     * Reference to the editor logic for opening GUIs.
     */
    private final MobDropEditor editor;

    /**
     * Constructs a new command executor.
     *
     * @param editor The MobDropEditor instance used to open the management interface.
     */
    public SlayerRewardsCommand(MobDropEditor editor) {
        this.editor = editor;
    }

    /**
     * Executes the command logic.
     * <p>
     * <b>Usage:</b> /slayerrewards edit &lt;mob&gt; [page]
     * </p>
     *
     * @param sender  The source of the command (must be a Player).
     * @param command The command executed.
     * @param label   The alias used.
     * @param args    The command arguments.
     * @return true if valid, false otherwise.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.translatable("msg.only_players", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("slayerrewards.admin")) {
            player.sendMessage(Component.translatable("msg.permission.denied", NamedTextColor.RED));
            return true;
        }

        // Usage: /slayerrewards edit <mob> [page]
        if (args.length >= 2 && args[0].equalsIgnoreCase("edit")) {
            String mobName = args[1];
            int page = 1;
            
            if (args.length >= 3) {
                try {
                    page = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {}
            }

            editor.openEditor(player, mobName, page);
            return true;
        }

        player.sendMessage(Component.translatable("slayerrewards.command.usage", NamedTextColor.RED));
        return true;
    }
}
