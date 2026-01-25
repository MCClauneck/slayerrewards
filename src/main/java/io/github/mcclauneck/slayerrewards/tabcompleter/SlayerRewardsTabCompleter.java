package io.github.mcclauneck.slayerrewards.tabcompleter;

import io.github.mcclauneck.slayerrewards.common.SlayerRewardsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles tab completion for the /slayerrewards command.
 * <p>
 * Provides suggestions for subcommands ("edit") and dynamic mob names
 * based on the configuration files present in the mobs folder.
 * </p>
 */
public class SlayerRewardsTabCompleter implements TabCompleter {

    private final SlayerRewardsProvider provider;

    /**
     * Constructs a new tab completer.
     *
     * @param provider The provider instance used to access the mobs folder.
     */
    public SlayerRewardsTabCompleter(SlayerRewardsProvider provider) {
        this.provider = provider;
    }

    /**
     * Generates tab completion suggestions.
     *
     * @param sender  The source of the command.
     * @param command The command being completed.
     * @param alias   The alias used.
     * @param args    The arguments provided so far.
     * @return A list of suggestions, or an empty list if none.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // Arg 1: Subcommand
        if (args.length == 1) {
            List<String> subcommands = Collections.singletonList("edit");
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
        }
        // Arg 2: Mob Name (only if arg 1 is "edit")
        else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            List<String> mobs = new ArrayList<>();
            File folder = provider.getMobsFolder();
            
            if (folder.exists()) {
                File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
                if (files != null) {
                    for (File file : files) {
                        mobs.add(file.getName().replace(".yml", ""));
                    }
                }
            }
            StringUtil.copyPartialMatches(args[1], mobs, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}
