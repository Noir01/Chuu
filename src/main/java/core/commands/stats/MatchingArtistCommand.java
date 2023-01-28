package core.commands.stats;

import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.commands.utils.PrivacyUtils;
import core.otherlisteners.util.PaginatorBuilder;
import core.parsers.NumberParser;
import core.parsers.OnlyUsernameParser;
import core.parsers.Parser;
import core.parsers.params.ChuuDataParams;
import core.parsers.params.NumberParameters;
import core.util.ServiceView;
import dao.entities.DiscordUserDisplay;
import dao.entities.LbEntry;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static core.parsers.ExtraParser.LIMIT_ERROR;

public class MatchingArtistCommand extends ConcurrentCommand<NumberParameters<ChuuDataParams>> {


    public MatchingArtistCommand(ServiceView dao) {
        super(dao);
        this.respondInPrivate = false;
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.USER_STATS;
    }

    @Override
    public Parser<NumberParameters<ChuuDataParams>> initParser() {
        Map<Integer, String> map = new HashMap<>(2);
        map.put(LIMIT_ERROR, "The number introduced must be positive and not very big");
        String s = "You can also introduce a number to vary the number of plays needed to award a match, " +
                "defaults to 1";
        return new NumberParser<>(new OnlyUsernameParser(db),
                null,
                Integer.MAX_VALUE,
                map, s, false, true);
    }

    @Override
    public String getDescription() {
        return "Users ordered by matching number of artists";
    }

    @Override
    public List<String> getAliases() {
        return List.of("matching");
    }

    @Override
    public String getName() {
        return "Matching artists";
    }

    @Override
    public void onCommand(Context e, @NotNull NumberParameters<ChuuDataParams> params) {

        ChuuDataParams innerParams = params.getInnerParams();

        long discordId = innerParams.getLastFMData().getDiscordId();
        int threshold = params.getExtraParam() == null ? 1 : Math.toIntExact(params.getExtraParam());
        List<LbEntry<Integer>> list = db.matchingArtistsCount(innerParams.getLastFMData().getName(), e.getGuild().getIdLong(), threshold);


        if (list.isEmpty()) {
            sendMessageQueue(e, "No one has any matching artist with you :(");
            return;
        }

        list.forEach(cl -> cl.setDiscordName(getUserString(e, cl.getDiscordId(), cl.getLastFmId())));

        List<String> strings = list.stream().map(PrivacyUtils::toString).toList();

        DiscordUserDisplay userInformation = CommandUtil.getUserInfoEscaped(e, discordId);
        String url = userInformation.urlImage();
        String usableName = userInformation.username();

        int count = db.getUserArtistCount(innerParams.getLastFMData().getName(), 0);
        EmbedBuilder embedBuilder = new ChuuEmbedBuilder(e)
                .setThumbnail(url)
                .setTitle("Matching artists with " + usableName)
                .setFooter(String.format("%s has %d total %s!%n", CommandUtil.unescapedUser(usableName, discordId, e), count, CommandUtil.singlePlural(count, "artist", "artists")), null);
        new PaginatorBuilder<>(e, embedBuilder, strings).build().queue();
    }
}
