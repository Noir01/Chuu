package core.commands.uniques;

import core.commands.abstracts.LeaderboardCommand;
import core.commands.utils.CommandCategory;
import core.parsers.NoOpParser;
import core.parsers.Parser;
import core.parsers.params.CommandParameters;
import core.util.ServiceView;
import dao.entities.LbEntry;

import java.util.List;

public class UniqueSongsLeaderboardCommand extends LeaderboardCommand<CommandParameters, Integer> {
    public UniqueSongsLeaderboardCommand(ServiceView dao) {
        super(dao, true);
    }

    @Override
    public String slashName() {
        return "tracks-leaderboard";
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.UNIQUES;
    }

    @Override
    public Parser<CommandParameters> initParser() {
        return NoOpParser.INSTANCE;
    }

    @Override
    public String getEntryName(CommandParameters params) {
        return "unique songs";
    }

    @Override
    public String getDescription() {
        return ("Unique songs leaderboard in guild");
    }

    @Override
    public List<String> getAliases() {
        return List.of("uniquesongslb", "uniquetrlb");
    }

    @Override
    public List<LbEntry<Integer>> getList(CommandParameters parameters) {
        return db.getUniqueSongLeaderboard(parameters.getE().getGuild().getIdLong());
    }

    @Override
    public String getName() {
        return "Unique songs leaderboard";
    }

}
