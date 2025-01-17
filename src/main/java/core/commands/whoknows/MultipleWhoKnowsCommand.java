package core.commands.whoknows;

import core.commands.Context;
import core.commands.utils.CommandUtil;
import core.imagerenderer.WhoKnowsMaker;
import core.parsers.MultipleArtistsParser;
import core.parsers.Parser;
import core.parsers.params.MultiArtistParameters;
import dao.ServiceView;
import dao.entities.*;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

public class MultipleWhoKnowsCommand extends WhoKnowsBaseCommand<MultiArtistParameters> {
    public MultipleWhoKnowsCommand(ServiceView dao) {
        super(dao);
    }


    private String join(Set<String> a) {
        return String.join(",", a);
    }

    @Override
    BufferedImage doImage(MultiArtistParameters ap, WrapperReturnNowPlaying wrapperReturnNowPlaying) {
        Context e = ap.getE();
        BufferedImage logo = null;
        String title;
        if (e.isFromGuild()) {
            logo = CommandUtil.getLogo(db, e);
            title = e.getGuild().getName();
        } else {
            title = e.getJDA().getSelfUser().getName();
        }
        handleWkMode(ap, wrapperReturnNowPlaying, WhoKnowsMode.IMAGE);
        BufferedImage image = WhoKnowsMaker.generateWhoKnows(wrapperReturnNowPlaying, EnumSet.allOf(WKMode.class), title, logo, ap.getE().getAuthor().getIdLong());
        sendImage(image, e);

        return logo;
    }

    @Override
    LastFMData obtainLastFmData(MultiArtistParameters ap) {
        return ap.getLastFMData();
    }

    @Override
    public Optional<Rank<ReturnNowPlaying>> fetchNotInList(MultiArtistParameters ap, WrapperReturnNowPlaying wr) {
        return Optional.empty();
    }

    @Override
    WrapperReturnNowPlaying generateWrapper(MultiArtistParameters params, WhoKnowsMode whoKnowsMode) {
        int i = whoKnowsMode.equals(WhoKnowsMode.IMAGE) ? 10 : Integer.MAX_VALUE;
        List<WrapperReturnNowPlaying> whoKnowsArtistSet = db.getWhoKnowsArtistSet(params.getArtists(), params.getE().getGuild().getIdLong(), i, null);
        if (whoKnowsArtistSet.isEmpty()) {
            sendMessageQueue(params.getE(), "No one knows " + CommandUtil.escapeMarkdown(join(params.getArtists())));
            return null;
        }
        WrapperReturnNowPlaying first = whoKnowsArtistSet.get(0);

        Map<String, ReturnNowPlaying> stringReturnNowPlayingMap = WhoKnowsLoonasCommand.groupByUser(whoKnowsArtistSet);
        WrapperReturnNowPlaying wrapperReturnNowPlaying = new WrapperReturnNowPlaying(
                stringReturnNowPlayingMap.
                        values().stream().sorted(Comparator.comparingInt(ReturnNowPlaying::getPlayNumber).reversed())
                        .toList(),
                0, first.getUrl(), ""
        );
        wrapperReturnNowPlaying.getReturnNowPlayings()
                .forEach(x -> x.setDiscordName(CommandUtil.getUserInfoUnescaped(params.getE(), x.getDiscordId()).username()));
        wrapperReturnNowPlaying.setUrl((first.getUrl()));
        wrapperReturnNowPlaying.setArtist(whoKnowsArtistSet.stream().map(WrapperReturnNowPlaying::getArtist).collect(Collectors.joining(",")));
        return wrapperReturnNowPlaying;
    }

    @Override
    public String getTitle(MultiArtistParameters params, String baseTitle) {
        return "Who knows " + CommandUtil.escapeMarkdown(join(params.getArtists())) + " in " + baseTitle + "?";

    }


    @Override
    public Parser<MultiArtistParameters> initParser() {
        return new MultipleArtistsParser(db, lastFM);
    }

    @Override
    public String getDescription() {
        return "Like whoknows but for a lot of artists at the same time";
    }

    @Override
    public List<String> getAliases() {
        return List.of("multiwhoknows", "mwk", "multiwk", "mw", "multiw");
    }

    @Override
    public String getName() {
        return "Multi Who Knows";
    }
}
