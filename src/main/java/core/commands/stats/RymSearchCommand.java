package core.commands.stats;

import core.apis.rym.RYMSearch;
import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.parsers.Parser;
import core.parsers.UsernameAndNpQueryParser;
import core.parsers.params.ExtraParameters;
import core.parsers.params.WordParameter;
import core.util.ServiceView;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class RymSearchCommand extends ConcurrentCommand<ExtraParameters<WordParameter, User>> {
    private final RYMSearch rymSearch;

    public RymSearchCommand(ServiceView dao) {
        super(dao);
        rymSearch = new RYMSearch();

    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.NOW_PLAYING;
    }

    @Override
    public Parser<ExtraParameters<WordParameter, User>> initParser() {
        return new UsernameAndNpQueryParser(db, lastFM, (np) -> np.artistName() + " " + np.albumName());
    }

    @Override
    public String getDescription() {
        return "Searches in rateyourmusic inputted query or now playing album";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("rymsearch", "ryms");
    }

    @Override
    public void onCommand(Context e, @NotNull ExtraParameters<WordParameter, User> params) {


        String query = params.getInnerParams().getWord();
        String url = rymSearch.searchUrl(query);
        e.sendMessage(new ChuuEmbedBuilder(e).setAuthor("Rateyourmusic search ➜ " + query, url)
                .setTitle("\t<:rymchuu:896517028129677383> Click here to view the results", url).build()).queue();
    }

    @Override
    public String getName() {
        return "RYM Search";
    }
}
