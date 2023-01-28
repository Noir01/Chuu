package core.commands.stats;

import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.imagerenderer.GraphicUtils;
import core.imagerenderer.util.bubble.StringFrequency;
import core.imagerenderer.util.pie.IPieableMap;
import core.otherlisteners.util.PaginatorBuilder;
import core.parsers.NumberParser;
import core.parsers.Parser;
import core.parsers.TimerFrameParser;
import core.parsers.params.NumberParameters;
import core.parsers.params.TimeFrameParameters;
import core.parsers.utils.CustomTimeFrame;
import core.parsers.utils.OptionalEntity;
import core.parsers.utils.Optionals;
import core.services.tags.TagAlbumService;
import core.services.tags.TagArtistService;
import core.util.ServiceView;
import dao.entities.*;
import dao.musicbrainz.MusicBrainzService;
import dao.musicbrainz.MusicBrainzServiceSingleton;
import net.dv8tion.jda.api.EmbedBuilder;
import org.apache.commons.text.WordUtils;
import org.jetbrains.annotations.NotNull;
import org.knowm.xchart.PieChart;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static core.parsers.ExtraParser.LIMIT_ERROR;

public class GenreCommand extends ConcurrentCommand<NumberParameters<TimeFrameParameters>> {
    private final MusicBrainzService musicBrainz;


    private final IPieableMap<Genre, Integer, NumberParameters<TimeFrameParameters>> pieable;

    public GenreCommand(ServiceView dao) {
        super(dao);
        this.musicBrainz = MusicBrainzServiceSingleton.getInstance();
        pieable = new IPieableMap<>() {
            @Override
            public List<StringFrequency> obtainFrequencies(Map<Genre, Integer> data, NumberParameters<TimeFrameParameters> params) {
                return data.entrySet().stream().map(t -> new StringFrequency(t.getKey().getName(), t.getValue())).toList();
            }

            @Override
            public PieChart fillPie(PieChart chart, NumberParameters<TimeFrameParameters> params, Map<Genre, Integer> data) {
                data.entrySet().stream().sorted(((o1, o2) -> o2.getValue().compareTo(o1.getValue()))).sequential().limit(params.getExtraParam())
                        .forEach(entry -> {
                            Genre genre = entry.getKey();
                            int plays = entry.getValue();
                            chart.addSeries(genre.getName() + "​", plays);
                        });
                return chart;
            }
        };
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.GENRES;
    }

    @Override
    public Parser<NumberParameters<TimeFrameParameters>> initParser() {
        Map<Integer, String> map = new HashMap<>(2);
        map.put(LIMIT_ERROR, "The number introduced must be between 1 and a big number");
        String s = "You can also introduce a number to vary the number of genres shown in the pie. Defaults to 10";

        TimerFrameParser timerFrameParser = new TimerFrameParser(db, TimeFrameEnum.ALL);
        timerFrameParser.addOptional(new OptionalEntity("albums", "use albums"));
        timerFrameParser.addOptional(new OptionalEntity("lastfm", "use lastfm tags instead of musicbrainz"));
        timerFrameParser.addOptional(new OptionalEntity("mb", "use only musicbrainz tags"));


        timerFrameParser.addOptional(Optionals.LIST.opt);


        return new NumberParser<>(timerFrameParser,
                10L,
                Integer.MAX_VALUE,
                map, s, false, true, false, "count");
    }

    @Override
    public String getDescription() {
        return "Top genres from an user";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("genre");
    }

    @Override
    public String slashName() {
        return "top";
    }

    @Override
    public String getName() {
        return "Top Genres";
    }

    @Override
    public void onCommand(Context e, @NotNull NumberParameters<TimeFrameParameters> params) throws LastFmException {


        TimeFrameParameters innerParams = params.getInnerParams();
        LastFMData user = innerParams.getLastFMData();
        String username = user.getName();
        long discordId = user.getDiscordId();

        TimeFrameEnum timeframe = innerParams.getTime();
        DiscordUserDisplay userInfo = CommandUtil.getUserInfoUnescaped(e, discordId);
        String usableString = userInfo.username();
        String urlImage = userInfo.urlImage();
        boolean mb = params.hasOptional("mb");
        boolean lastfm = params.hasOptional("lastfm");
        String service = mb ? "Musicbrainz" : lastfm ? "Last.fm" : "Last.fm and Musicbrainz";
        Map<Genre, Integer> map = new HashMap<>();
        boolean doAlbums = params.hasOptional("albums");
        if (!doAlbums) {
            List<ArtistInfo> artistInfos;
            if (timeframe == TimeFrameEnum.ALL && !mb) {
                artistInfos = db.getAllUserArtist(user.getDiscordId()).stream().map(t -> new ArtistInfo(t.getUrl(), t.getArtist(), t.getArtistMbid())).toList();
            } else {
                artistInfos = lastFM.getTopArtists(user, CustomTimeFrame.ofTimeFrameEnum(timeframe), 3000);
            }

            if (artistInfos.isEmpty()) {
                sendMessageQueue(e, "Was not able to find any genre in " + usableString + "'s top 3000 artists" + innerParams.getTime().getDisplayString() + " on " + service);
                return;
            }
            if (lastfm || !mb) {
                map.putAll(db.genreCountsByArtist(artistInfos));
            }
            if (mb || !lastfm) {
                List<ArtistInfo> mbidArtists = artistInfos.stream().filter(u -> u.getMbid() != null && !u.getMbid().isEmpty())
                        .toList();
                Map<Genre, List<ArtistInfo>> genreListMap = musicBrainz.genreCountByartist(mbidArtists);
                CommandUtil.runLog(new TagArtistService(db, lastFM, genreListMap));
                map = Stream.concat(map.entrySet().stream(), genreListMap.entrySet().stream().map(t -> Map.entry(t.getKey(), t.getValue().size())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));
            }

        } else {
            List<AlbumInfo> albumInfos;
            List<AlbumInfo> albumMbids;

            if (timeframe == TimeFrameEnum.ALL) {
                albumInfos = db.getUserAlbums(username).stream().map(x ->
                        new AlbumInfo(x.getAlbumMbid(), x.getAlbum(), x.getArtist())).toList();
                albumMbids = albumInfos.stream().filter(u -> u.getMbid() != null && !u.getMbid().isEmpty()).toList();
            } else {
                albumInfos = lastFM.getTopAlbums(user, CustomTimeFrame.ofTimeFrameEnum(timeframe), 3000);
                albumMbids = albumInfos.stream().filter(u -> u.getMbid() != null && !u.getMbid().isEmpty()).toList();
            }
            if (albumInfos.isEmpty()) {
                sendMessageQueue(e, String.format("Was not able to find any genre in %s's top 3000 albums%s on %s", usableString, innerParams.getTime().getDisplayString(), service));
                return;
            }
            if (lastfm || !mb) {
                map.putAll(db.genreCountsByAlbum(albumInfos));
            }
            if (mb || !lastfm) {
                Map<Genre, List<AlbumInfo>> genreListMap = musicBrainz.genreCount(albumMbids);
                CommandUtil.runLog(new TagAlbumService(db, lastFM, genreListMap));
                map = genreListMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, x -> x.getValue().size()));
            }

        }
        if (map.isEmpty()) {
            sendMessageQueue(e, String.format("Was not able to find any genre in %s's top 3000 %s%s on %s", usableString, doAlbums ? "artists" : "albums", innerParams.getTime().getDisplayString(), service));
            return;
        }

        if (params.hasOptional("list")) {
            List<String> lines = map.entrySet()
                    .stream().sorted(((o1, o2) -> o2.getValue().compareTo(o1.getValue()))).map(x -> ". **" + WordUtils.capitalizeFully(CommandUtil.escapeMarkdown(x.getKey().getName())) + "** - " + x.getValue() + "\n").toList();
            if (lines.isEmpty()) {
                sendMessageQueue(e, String.format("Was not able to find any genre in %s's top 3000 %s%s on%s", usableString, doAlbums ? "artists" : "albums", innerParams.getTime().getDisplayString(), service));
                return;
            }

            EmbedBuilder embedBuilder = new ChuuEmbedBuilder(e)
                    .setTitle(usableString + "'s genres")
                    .setFooter(usableString + " has " + lines.size() + " found genres" + timeframe.getDisplayString(), null)
                    .setThumbnail(urlImage);
            new PaginatorBuilder<>(e, embedBuilder, lines).build().queue();


        } else {
            Long extraParam = params.getExtraParam();
            PieChart pieChart = pieable.doPie(params, map);
            pieChart.setTitle("Top " + extraParam + " Genres from " + usableString + timeframe.getDisplayString());
            BufferedImage bufferedImage = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bufferedImage.createGraphics();
            GraphicUtils.setQuality(g);
            pieChart.paint(g, 1000, 750);
            GraphicUtils.drawImageInCorner(urlImage, g);
            sendImage(bufferedImage, e);
        }
    }


}
