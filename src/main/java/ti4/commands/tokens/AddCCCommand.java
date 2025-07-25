package ti4.commands.tokens;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.commandcounter.RemoveCommandCounterService;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Tile;

public class AddCCCommand extends AddRemoveTokenCommand {

    @Override
    void doAction(SlashCommandInteractionEvent event, List<String> colors, Tile tile, Game game) {
        boolean usedTactics = false;
        for (String color : colors) {
            OptionMapping option = event.getOption(Constants.CC_USE);
            if (option != null && !usedTactics) {
                usedTactics = true;
                String value = option.getAsString().toLowerCase();
                switch (value) {
                    case "t/tactic", "t", "tactic", "tac", "tact" -> RemoveCommandCounterService.fromTacticsPool(event, color, tile, game);
                }
            }
            CommandCounterHelper.addCC(event, game, color, tile);
            Helper.isCCCountCorrect(game, color);
        }
    }

    @Override
    public String getDescription() {
        return "Add command tokens to tile/system";
    }

    @Override
    public String getName() {
        return Constants.ADD_CC;
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.CC_USE, "\"t\"/\"tactic\" to add a token from tactic pool, \"r\"/\"retreat\" to add a token from reinforcements").setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }
}
