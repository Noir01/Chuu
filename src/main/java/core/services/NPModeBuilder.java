package core.services;

import com.neovisionaries.i18n.CountryCode;
import core.Chuu;
import core.apis.discogs.DiscogsApi;
import core.apis.discogs.DiscogsSingleton;
import core.apis.last.ConcurrentLastFM;
import core.apis.rym.RYMSearch;
import core.apis.spotify.Spotify;
import core.apis.spotify.SpotifySingleton;
import core.commands.Context;
import core.commands.utils.CommandUtil;
import core.commands.utils.PrivacyUtils;
import core.exceptions.LastFmException;
import core.services.tags.TagCleaner;
import core.services.tags.TagStorer;
import core.services.validators.TrackValidator;
import core.util.ChuuVirtualPool;
import dao.ChuuService;
import dao.entities.*;
import dao.exceptions.InstanceNotFoundException;
import dao.musicbrainz.MusicBrainzService;
import net.dv8tion.jda.api.EmbedBuilder;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static core.commands.rym.AlbumRatings.getStartsFromScore;

public class NPModeBuilder {
    static final Map<NPMode, Integer> footerIndexes;

    private static final EnumSet<NPMode> albumModes = EnumSet.of(NPMode.ALBUM_RYM,
            NPMode.SERVER_ALBUM_RYM, NPMode.BOT_ALBUM_RYM, NPMode.ALBUM_CROWN, NPMode.ALBUM_RANK, NPMode.ALBUM_PLAYS, NPMode.GLOBAL_ALBUM_CROWN, NPMode.GLOBAL_ALBUM_RANK);
    private static final EnumSet<NPMode> trackModes = EnumSet.of(
            NPMode.ALBUM_RYM,
            NPMode.SERVER_ALBUM_RYM,
            NPMode.BOT_ALBUM_RYM,
            NPMode.GLOBAL_TRACK_CROWN,
            NPMode.GLOBAL_TRACK_RANK,
            NPMode.POPULARITY,
            NPMode.ALBUM_CROWN, NPMode.ALBUM_RANK, NPMode.ALBUM_PLAYS, NPMode.TRACK_CROWN, NPMode.TRACK_RANK);
    private static final ExecutorService executor = ChuuVirtualPool.of("np-mode");

    static {
        try {
            List<NPMode> tags = List.of(NPMode.TAGS,
                    NPMode.EXTENDED_TAGS,
                    NPMode.LOVED,
                    NPMode.ARTIST_PLAYS,
                    NPMode.ALBUM_PLAYS,
                    NPMode.SONG_PLAYS,
                    NPMode.POPULARITY,
                    NPMode.CROWN,
                    NPMode.ARTIST_RANK,
                    NPMode.GLOBAL_CROWN,
                    NPMode.GLOBAL_RANK,
                    NPMode.ALBUM_CROWN,
                    NPMode.ALBUM_RANK,
                    NPMode.GLOBAL_ALBUM_CROWN,
                    NPMode.GLOBAL_ALBUM_RANK,
                    NPMode.TRACK_RANK,
                    NPMode.TRACK_CROWN,
                    NPMode.GLOBAL_TRACK_CROWN,
                    NPMode.GLOBAL_TRACK_RANK,
                    NPMode.LFM_LISTENERS,
                    NPMode.LFM_SCROBBLES,
                    NPMode.BOT_LISTENERS,
                    NPMode.BOT_SCROBBLES,
                    NPMode.SERVER_LISTENERS,
                    NPMode.SERVER_SCROBBLES,
                    NPMode.GENDER,
                    NPMode.COUNTRY,
                    NPMode.BOT_ALBUM_RYM,
                    NPMode.SERVER_ALBUM_RYM,
                    NPMode.ALBUM_RYM,
                    NPMode.SONG_DURATION,
                    NPMode.HIGHEST_STREAK,
                    NPMode.HIGHEST_SERVER_STREAK,
                    NPMode.HIGHEST_BOT_STREAK,
                    NPMode.CURRENT_COMBO,
                    NPMode.SCROBBLE_COUNT
            );

            Map<NPMode, Integer> temp = new EnumMap<>(NPMode.class);
            for (int i = 0; i < tags.size(); i++) {
                NPMode npMode = tags.get(i);
                temp.put(npMode, i);
            }
            footerIndexes = temp;
        } catch (Exception e) {
            throw new RuntimeException("Could not init class.", e);
        }


    }

    private final Spotify spotifyApi;
    private final NowPlayingArtist np;
    private final Context e;
    private final String[] footerSpaces;
    private final long discordId;
    private final String userName;
    private final EnumSet<NPMode> npModes;
    private final LastFMData lastFMName;
    private final EmbedBuilder embedBuilder;
    private final ScrobbledArtist scrobbledArtist;
    private final ChuuService service;
    private final ConcurrentLastFM lastFM;
    private final String serverName;
    private final MusicBrainzService mb;
    private final List<String> outputList;
    private final CompletableFuture<List<TrackWithArtistId>> comboData;
    private final DiscogsApi discogsApi;
    private final RYMSearch rymSearch;

    public NPModeBuilder(NowPlayingArtist np, Context e, String[] footerSpaces, long discordId, String userName, EnumSet<NPMode> npModes, LastFMData lastFMName, EmbedBuilder embedBuilder, ScrobbledArtist scrobbledArtist, ChuuService service, ConcurrentLastFM lastFM, String serverName, MusicBrainzService mb, List<String> outputList, CompletableFuture<List<TrackWithArtistId>> data) {
        this.np = np;
        this.e = e;
        this.footerSpaces = footerSpaces;
        this.discordId = discordId;
        this.userName = userName;
        this.npModes = npModes;
        this.lastFMName = lastFMName;
        this.embedBuilder = embedBuilder;
        this.scrobbledArtist = scrobbledArtist;
        this.service = service;
        this.lastFM = lastFM;
        this.serverName = serverName;
        this.mb = mb;
        this.outputList = outputList;
        comboData = data;
        this.spotifyApi = SpotifySingleton.getInstance();
        this.discogsApi = DiscogsSingleton.getInstanceUsingDoubleLocking();
        this.rymSearch = new RYMSearch();
    }

    public static int getSize() {
        return footerIndexes.size();
    }

    public CompletableFuture<?> buildNp() {
        Set<Integer> previousNewLinesToAdd = new HashSet<>();
        List<CompletableFuture<?>> completableFutures = new ArrayList<>();
        UnaryOperator<CompletableFuture<?>> logger = (x) -> x.whenComplete((u, ex) -> {
            if (ex != null) {
                Chuu.getLogger().warn(ex.getMessage(), ex);
            }
        });

        AtomicBoolean whoKnowsLock = new AtomicBoolean(false);
        AtomicBoolean whoKnowsAlbumLock = new AtomicBoolean(false);
        AtomicBoolean globalWhoKnowsLock = new AtomicBoolean(false);
        AtomicBoolean globalWhoKnowsAlbumLock = new AtomicBoolean(false);
        AtomicBoolean ratingLock = new AtomicBoolean(false);
        AtomicBoolean serverStats = new AtomicBoolean(false);
        AtomicBoolean botStats = new AtomicBoolean(false);
        AtomicBoolean lfmStats = new AtomicBoolean(false);
        AtomicBoolean mbLock = new AtomicBoolean(false);
        AtomicBoolean trackLock = new AtomicBoolean(false);
        AtomicBoolean embedLock = new AtomicBoolean(false);
        AtomicBoolean tagsLock = new AtomicBoolean(false);
        AtomicBoolean trackCrownsLock = new AtomicBoolean(false);
        AtomicBoolean globalTrackCrownsLock = new AtomicBoolean(false);
        Long preAlbumId = null;
        if (npModes.stream().anyMatch(albumModes::contains)) {
            try {
                CommandUtil.validate(service, scrobbledArtist, lastFM, discogsApi, spotifyApi);
                if (np.albumName() != null && !np.albumName().isBlank())
                    preAlbumId = CommandUtil.albumvalidate(service, scrobbledArtist, lastFM, np.albumName()).id();
            } catch (LastFmException ignored) {
            }
        }
        Long albumId = preAlbumId;

        Long preTrackId = null;
        if (npModes.stream().anyMatch(trackModes::contains)) {
            try {
                if (np.songName() != null && !np.songName().isBlank())
                    preTrackId = new TrackValidator(service, lastFM).validate(scrobbledArtist.getArtistId(), scrobbledArtist.getArtist(), np.songName()).getTrackId();
            } catch (LastFmException ignored) {
            }
        }
        Long trackId = preTrackId;
        for (
                NPMode npMode : npModes) {
            Integer index = footerIndexes.get(npMode);
            assert index != null;
            long guildId = e.isFromGuild() ? e.getGuild().getIdLong() : -1L;
            switch (npMode) {
                case LOVED:
                    if (np.loved()) {
                        footerSpaces[index] = "❤️";
                    }
                    break;
                case CURRENT_COMBO:
                    completableFutures.add(logger.apply(comboData.whenComplete((u, e) -> {
                        if (e == null) {
                            String previousArtist = np.artistName();
                            int comboCounter = 1;
                            boolean couldCheck = false;
                            for (TrackWithArtistId datum : u) {
                                if (datum.getArtist().equals(previousArtist)) {
                                    comboCounter++;
                                } else {
                                    couldCheck = true;
                                    break;
                                }
                            }
                            if (!couldCheck) {
                                comboCounter = service.getCurrentCombo(scrobbledArtist.getArtistId(), lastFMName.getName());
                            }
                            if (comboCounter > 1) {
                                footerSpaces[index] =
                                        String.format("%s is on a 🔥 of %d %s", userName, comboCounter, CommandUtil.singlePlural(comboCounter, "play", "plays"));
                            }
                        } else {
                            try {
                                StreakEntity combo = lastFM.getCombo(lastFMName);
                                if (combo.artistCount() > 1) {
                                    footerSpaces[footerIndexes.get(NPMode.CURRENT_COMBO)] =
                                            String.format("%s's is on a 🔥 of %d %s", userName, combo.artistCount(), CommandUtil.singlePlural(combo.artistCount(), "play", "plays"));
                                }
                            } catch (LastFmException exception) {
                                Chuu.getLogger().info(e.getMessage(), e);
                            }
                        }
                    })));
                    break;
                case SCROBBLE_COUNT:
                    completableFutures.add(CommandUtil.runLog(() -> {
                        int playCount = new UserInfoService(service).getUserInfo(lastFMName).getPlayCount();
                        footerSpaces[index] =
                                "%d total scrobbles".formatted(playCount);
                    }));
                    break;
                case POPULARITY:
                    if (trackId != null) {
                        completableFutures.add(CommandUtil.runLog(() -> {
                            ScrobbledTrack trackInfo = service.getTrackInfo(lastFMName.getName(), trackId);
                            if (trackInfo != null && trackInfo.getPopularity() != 0) {
                                footerSpaces[index] =
                                        "%d%% popular".formatted(trackInfo.getPopularity());
                            }
                        }));
                    }
                    break;
                case NORMAL:
                case ARTIST_PIC:
                case RANDOM:
                case UNKNOWN:
                    break;
                case PREVIOUS:
                case SPOTIFY_LINK:
                case RYM_LINK:
                    if (!embedLock.compareAndSet(false, true)) {
                        break;
                    }
                    completableFutures.add(CommandUtil.runLog(() -> {
                        if (npModes.contains(NPMode.PREVIOUS)) {
                            try {
                                NowPlayingArtist recent = lastFM.getRecent(lastFMName, 2).get(1);
                                String album = CommandUtil.escapeMarkdown(recent.albumName());
                                String artist = CommandUtil.escapeMarkdown(recent.artistName());
                                String song = CommandUtil.escapeMarkdown(recent.songName());
                                if (npModes.contains(NPMode.SPOTIFY_LINK)) {
                                    String uri = spotifyApi.searchItems(recent.songName(), recent.artistName(), recent.albumName());
                                    if (!uri.isBlank())
                                        song = "<:spochuu:896516103197569044>\t %s [%s](%s)".formatted(EmbedBuilder.ZERO_WIDTH_SPACE, song, uri);
                                }
                                if (npModes.contains(NPMode.RYM_LINK)) {
                                    String url = rymSearch.searchUrl(recent.artistName(), recent.albumName());
                                    album = "\t<:rymchuu:896517028129677383> %s [%s](%s)".formatted(EmbedBuilder.ZERO_WIDTH_SPACE, album, url);
                                }

                                String t = "**" + artist +
                                           "** | " + album + "\n";
                                embedBuilder.getDescriptionBuilder().append("\n**Previous:** ***").append(song).append("***\n").append(t);

                            } catch (LastFmException ignored) {
                            }
                        }
                        if (npModes.contains(NPMode.SPOTIFY_LINK)) {
                            String uri = spotifyApi.searchItems(np.songName(), np.artistName(), np.albumName());
                            if (!uri.isBlank())
                                embedBuilder.setTitle("\t<:spochuu:896516103197569044> " + embedBuilder.build().getTitle(), uri);
                        }
                        if (npModes.contains(NPMode.RYM_LINK) && np.albumName() != null && !np.albumName().isBlank()) {
                            String url = rymSearch.searchUrl(np.artistName(), np.albumName());

                            String a = "**" + CommandUtil.escapeMarkdown(np.artistName()) +
                                       "** | " + CommandUtil.escapeMarkdown(np.albumName());

                            String b = "**%s** | \t<:rymchuu:896517028129677383> %s [%s](%s)".formatted(CommandUtil.escapeMarkdown(np.artistName()), EmbedBuilder.ZERO_WIDTH_SPACE, CommandUtil.escapeMarkdown(np.albumName()), url);
                            embedBuilder.setDescription(StringUtils.replaceOnceIgnoreCase(embedBuilder.build().getDescription(), a, b));
                        }

                    }));
                    break;
                case TAGS:
                case EXTENDED_TAGS:
                    if (!tagsLock.compareAndSet(false, true)) {
                        break;
                    }
                    completableFutures.add(CommandUtil.runLog(() -> {
                        boolean extended = npModes.contains(NPMode.EXTENDED_TAGS);
                        int limit = extended ? 12 : 5;
                        Set<String> tags = new LinkedHashSet<>();
                        if (lastFMName.getSession() != null && lastFMName.useOwnTags()) {
                            tags.addAll(lastFM.getUserArtistTags(limit, scrobbledArtist.getArtist(), lastFMName, lastFMName.getSession()));
                        }
                        if (tags.size() < limit) {
                            tags.addAll(new HashSet<>(new TagStorer(service, lastFM, executor, np).findTags(limit)));
                        }
                        var filteredTags = new TagCleaner(service).cleanTags(tags);
                        if (filteredTags.isEmpty()) {
                            return;
                        }
                        String tagsField = EmbedBuilder.ZERO_WIDTH_SPACE + " • " + String.join(" - ", filteredTags);
                        tagsField += '\n';
                        footerSpaces[index] = tagsField;
                    }));
                    break;
                case CROWN:
                case ARTIST_RANK:
                    if (e.isFromGuild()) {
                        if (!whoKnowsLock.compareAndSet(false, true)) {
                            break;
                        }
                        completableFutures.add(CommandUtil.runLog(() -> {
                            WrapperReturnNowPlaying wrapperReturnNowPlaying = service.whoKnows(scrobbledArtist.getArtistId(), guildId, 10_000);
                            List<ReturnNowPlaying> returnNowPlayings = wrapperReturnNowPlaying.getReturnNowPlayings();
                            if (!returnNowPlayings.isEmpty()) {

                                ReturnNowPlaying returnNowPlaying = returnNowPlayings.get(0);
                                String userString = CommandUtil.getUserInfoUnescaped(e, returnNowPlaying.getDiscordId()).username();
                                if (npModes.contains(NPMode.CROWN))
                                    footerSpaces[footerIndexes.get(NPMode.CROWN)] =
                                            "👑 " + returnNowPlaying.getPlayNumber() + " (" + userString + ")";
                                if (npModes.contains(NPMode.ARTIST_RANK)) {
                                    for (int i = 0; i < returnNowPlayings.size(); i++) {
                                        ReturnNowPlaying searching = returnNowPlayings.get(i);
                                        if (searching.getDiscordId() == discordId) {
                                            footerSpaces[footerIndexes.get(NPMode.ARTIST_RANK)] = serverName + " Rank: " + (i + 1) + CommandUtil.getDayNumberSuffix(i + 1) + "/" + returnNowPlayings.size();
                                            if (npModes.contains(NPMode.CROWN))
                                                addNewLineToPrevious(footerIndexes.get(NPMode.CROWN));
                                            break;
                                        }
                                    }
                                }
                            }
                        }));
                    }
                    break;
                case ALBUM_CROWN:
                case ALBUM_RANK:
                    if (e.isFromGuild() && preAlbumId != null) {
                        if (!whoKnowsAlbumLock.compareAndSet(false, true)) {
                            break;
                        }
                        completableFutures.add(CommandUtil.runLog(() -> {
                            WrapperReturnNowPlaying wrapperReturnNowPlaying = service.getWhoKnowsAlbums(10_000, albumId, guildId);
                            List<ReturnNowPlaying> returnNowPlayings = wrapperReturnNowPlaying.getReturnNowPlayings();
                            if (!returnNowPlayings.isEmpty()) {

                                ReturnNowPlaying returnNowPlaying = returnNowPlayings.get(0);
                                String userString = CommandUtil.getUserInfoUnescaped(e, returnNowPlaying.getDiscordId()).username();
                                if (npModes.contains(NPMode.ALBUM_CROWN))
                                    footerSpaces[footerIndexes.get(NPMode.ALBUM_CROWN)] =
                                            "Album 👑 " + returnNowPlaying.getPlayNumber() + " (" + userString + ")";
                                if (npModes.contains(NPMode.ALBUM_RANK)) {
                                    for (int i = 0; i < returnNowPlayings.size(); i++) {
                                        ReturnNowPlaying searching = returnNowPlayings.get(i);
                                        if (searching.getDiscordId() == discordId) {
                                            footerSpaces[footerIndexes.get(NPMode.ALBUM_RANK)] = serverName + " Album Rank: " + (i + 1) + CommandUtil.getDayNumberSuffix(i + 1) + "/" + returnNowPlayings.size();
                                            if (npModes.contains(NPMode.ALBUM_CROWN))
                                                addNewLineToPrevious(footerIndexes.get(NPMode.ALBUM_CROWN));
                                            break;
                                        }

                                    }
                                }
                            }
                        }));
                    }
                case TRACK_CROWN:
                case TRACK_RANK:
                    if (e.isFromGuild() && trackId != null) {
                        if (!trackCrownsLock.compareAndSet(false, true)) {
                            break;
                        }
                        completableFutures.add(CommandUtil.runLog(() -> {
                            WrapperReturnNowPlaying wrapperReturnNowPlaying = service.getWhoKnowsTrack(10_000, trackId, guildId);
                            List<ReturnNowPlaying> returnNowPlayings = wrapperReturnNowPlaying.getReturnNowPlayings();
                            if (!returnNowPlayings.isEmpty()) {

                                ReturnNowPlaying returnNowPlaying = returnNowPlayings.get(0);
                                String userString = CommandUtil.getUserInfoUnescaped(e, returnNowPlaying.getDiscordId()).username();
                                if (npModes.contains(NPMode.TRACK_CROWN))
                                    footerSpaces[footerIndexes.get(NPMode.TRACK_CROWN)] =
                                            "Track 👑 " + returnNowPlaying.getPlayNumber() + " (" + userString + ")";
                                if (npModes.contains(NPMode.TRACK_RANK)) {
                                    for (int i = 0; i < returnNowPlayings.size(); i++) {
                                        ReturnNowPlaying searching = returnNowPlayings.get(i);
                                        if (searching.getDiscordId() == discordId) {
                                            footerSpaces[footerIndexes.get(NPMode.TRACK_RANK)] = serverName + " Track Rank: " + (i + 1) + CommandUtil.getDayNumberSuffix(i + 1) + "/" + returnNowPlayings.size();
                                            if (npModes.contains(NPMode.TRACK_CROWN))
                                                addNewLineToPrevious(footerIndexes.get(NPMode.TRACK_CROWN));
                                            break;
                                        }

                                    }
                                }
                            }
                        }));
                    }
                case SERVER_LISTENERS:
                case SERVER_SCROBBLES:
                    if (e.isFromGuild()) {
                        if (!serverStats.compareAndSet(false, true)) {
                            break;
                        }
                        completableFutures.add(CommandUtil.runLog(() -> {
                            if (npModes.contains(NPMode.SERVER_LISTENERS)) {
                                long artistFrequencies = service.getArtistFrequencies(guildId, scrobbledArtist.getArtistId());
                                footerSpaces[footerIndexes.get(NPMode.SERVER_LISTENERS)] =
                                        (String.format("%d %s listeners", artistFrequencies, serverName));
                            }
                            if (npModes.contains(NPMode.SERVER_SCROBBLES)) {
                                long serverArtistPlays = service.getServerArtistPlays(guildId, scrobbledArtist.getArtistId());
                                footerSpaces[footerIndexes.get(NPMode.SERVER_SCROBBLES)] =
                                        (String.format("%d %s plays", serverArtistPlays, serverName));
                                if (npModes.contains(NPMode.SERVER_LISTENERS)) {
                                    addNewLineToPrevious(footerIndexes.get(NPMode.SERVER_LISTENERS));
                                }
                            }
                        }));
                    }
                    break;
                case LFM_LISTENERS:
                case LFM_SCROBBLES:
                case ARTIST_PLAYS:
                    if (!lfmStats.compareAndSet(false, true)) {
                        break;
                    }
                    completableFutures.add(CommandUtil.runLog(() -> {
                        try {
                            ArtistSummary summary = lastFM.getArtistSummary(scrobbledArtist.getArtist(), lastFMName);
                            long artistFrequencies = summary.listeners();
                            long serverArtistPlays = summary.playcount();
                            if (npModes.contains(NPMode.LFM_LISTENERS))
                                footerSpaces[footerIndexes.get(NPMode.LFM_LISTENERS)] =
                                        (String.format("%d %s listeners", artistFrequencies, "Last.fm"));
                            if (npModes.contains(NPMode.ARTIST_PLAYS))
                                footerSpaces[footerIndexes.get(NPMode.ARTIST_PLAYS)] =
                                        (String.format("%d artist %s", summary.userPlayCount(), CommandUtil.singlePlural(summary.userPlayCount(), "play", "plays")));
                            if (npModes.contains(NPMode.LFM_SCROBBLES)) {
                                footerSpaces[footerIndexes.get(NPMode.LFM_SCROBBLES)] =
                                        (String.format("%d %s plays", serverArtistPlays, "Last.fm"));
                                if (npModes.contains(NPMode.LFM_LISTENERS)) {
                                    addNewLineToPrevious(footerIndexes.get(NPMode.LFM_SCROBBLES));
                                }
                            }
                        } catch (LastFmException ignored) {
                        }


                    }));
                    break;
                case ALBUM_RYM:
                case SERVER_ALBUM_RYM:
                case BOT_ALBUM_RYM:

                    if (!ratingLock.compareAndSet(false, true)) {
                        break;
                    }
                    if (preAlbumId != null) {
                        completableFutures.add(CommandUtil.runLog(() -> {
                            NumberFormat average = new DecimalFormat("#0.##");

                            if ((npModes.contains(NPMode.SERVER_ALBUM_RYM) || npModes.contains(NPMode.BOT_ALBUM_RYM) || npModes.contains(NPMode.ALBUM_RYM) && (npModes.contains(NPMode.SERVER_ALBUM_RYM) || npModes.contains(NPMode.BOT_ALBUM_RYM)))) {

                                AlbumRatings albumRatings = service.getRatingsByName(e.isFromGuild() ? guildId : -1L, np.albumName(), scrobbledArtist.getArtistId());
                                List<Rating> userRatings = albumRatings.userRatings();
                                if (npModes.contains(NPMode.SERVER_ALBUM_RYM)) {
                                    List<Rating> serverList = userRatings.stream().filter(Rating::isSameGuild).toList();
                                    if (!serverList.isEmpty()) {
                                        footerSpaces[footerIndexes.get(NPMode.SERVER_ALBUM_RYM)] =
                                                (String.format("%s Average: %s | Ratings: %d", serverName, average.format(serverList.stream().mapToDouble(rating -> rating.rating() / 2f).average().orElse(0)), serverList.size()));
                                    }
                                }
                                if (npModes.contains(NPMode.BOT_ALBUM_RYM)) {
                                    if (!userRatings.isEmpty()) {
                                        footerSpaces[footerIndexes.get(NPMode.BOT_ALBUM_RYM)] =
                                                (String.format("%s Average: %s | Ratings: %d", e.getJDA().getSelfUser().getName()
                                                        , average.format(userRatings.stream().mapToDouble(rating -> rating.rating() / 2f).average().orElse(0)), userRatings.size()));
                                    }

                                }
                                if (npModes.contains(NPMode.ALBUM_RYM)) {
                                    Optional<Rating> first = userRatings.stream().filter(x -> x.discordId() == discordId).findFirst();
                                    first.ifPresent(rating -> {
                                        previousNewLinesToAdd.add(footerIndexes.get(NPMode.ALBUM_RYM));
                                        footerSpaces[footerIndexes.get(NPMode.ALBUM_RYM)] = userName + ": " + getStartsFromScore().apply(rating.rating());
                                    });
                                }
                            } else if (npModes.contains(NPMode.ALBUM_RYM) && !npModes.contains(NPMode.BOT_ALBUM_RYM) && !npModes.contains(NPMode.SERVER_ALBUM_RYM)) {
                                Rating rating = service.getUserAlbumRating(discordId, albumId, scrobbledArtist.getArtistId());
                                if (rating != null) {
                                    footerSpaces[footerIndexes.get(NPMode.ALBUM_RYM)] = userName + ": " + getStartsFromScore().apply(rating.rating());
                                    previousNewLinesToAdd.add(footerIndexes.get(NPMode.ALBUM_RYM));
                                }
                            }
                        }));
                    }
                    break;
                case GLOBAL_CROWN:
                case GLOBAL_RANK:
                    if (!globalWhoKnowsLock.compareAndSet(false, true)) {
                        break;
                    }
                    completableFutures.add(CommandUtil.runLog(() -> {
                        List<GlobalCrown> globalArtistRanking = service.getGlobalArtistRanking(scrobbledArtist.getArtistId(), false, discordId);
                        if (!globalArtistRanking.isEmpty()) {

                            GlobalCrown returnNowPlaying = globalArtistRanking.get(0);


                            if (npModes.contains(NPMode.GLOBAL_CROWN)) {
                                String holder = getPrivateString(returnNowPlaying.discordId());
                                footerSpaces[footerIndexes.get(NPMode.GLOBAL_CROWN)] =
                                        "Global 👑 " + returnNowPlaying.playcount() + " (" + holder + ")";
                            }
                            if (npModes.contains(NPMode.GLOBAL_RANK)) {
                                Optional<GlobalCrown> yourPosition = globalArtistRanking.stream().filter(x -> x.discordId() == discordId).findFirst();
                                yourPosition.
                                        map(gc -> "Global Rank: " + gc.ranking() + CommandUtil.getDayNumberSuffix(gc.ranking()) + "/" + globalArtistRanking.size())
                                        .map(x -> {
                                            if (npModes.contains(NPMode.GLOBAL_CROWN)) {
                                                previousNewLinesToAdd.add(footerIndexes.get(NPMode.GLOBAL_CROWN));
                                            }
                                            return x;
                                        })
                                        .ifPresent(s -> footerSpaces[footerIndexes.get(NPMode.GLOBAL_RANK)] = s);

                            }
                        }
                    }));
                    break;
                case GLOBAL_ALBUM_CROWN:
                case GLOBAL_ALBUM_RANK:
                    if (preAlbumId != null) {
                        if (!globalWhoKnowsAlbumLock.compareAndSet(false, true)) {
                            break;
                        }
                        completableFutures.add(CommandUtil.runLog(() -> {
                            WrapperReturnNowPlaying wrapperReturnNowPlaying = service.getGlobalWhoKnowsAlbum(10_000, albumId, discordId, false, false);
                            List<ReturnNowPlaying> returnNowPlayings = wrapperReturnNowPlaying.getReturnNowPlayings();
                            if (!returnNowPlayings.isEmpty()) {

                                ReturnNowPlaying returnNowPlaying = returnNowPlayings.get(0);
                                String holder = getPrivateString(returnNowPlaying.getDiscordId());
                                if (npModes.contains(NPMode.GLOBAL_ALBUM_CROWN))
                                    footerSpaces[footerIndexes.get(NPMode.GLOBAL_ALBUM_CROWN)] =
                                            "Global Album 👑 " + returnNowPlaying.getPlayNumber() + " (" + holder + ")";
                                if (npModes.contains(NPMode.GLOBAL_ALBUM_RANK)) {
                                    for (int i = 0; i < returnNowPlayings.size(); i++) {
                                        ReturnNowPlaying searching = returnNowPlayings.get(i);
                                        if (searching.getDiscordId() == discordId) {
                                            footerSpaces[footerIndexes.get(NPMode.GLOBAL_ALBUM_RANK)] = "Global Album Rank: " + (i + 1) + CommandUtil.getDayNumberSuffix(i + 1) + "/" + returnNowPlayings.size();
                                            if (npModes.contains(NPMode.GLOBAL_ALBUM_RANK)) {
                                                previousNewLinesToAdd.add(footerIndexes.get(NPMode.GLOBAL_ALBUM_CROWN));
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }));
                    }
                    break;
                case GLOBAL_TRACK_CROWN:
                case GLOBAL_TRACK_RANK:
                    if (trackId != null) {
                        if (!globalTrackCrownsLock.compareAndSet(false, true)) {
                            break;
                        }
                        completableFutures.add(CommandUtil.runLog(() -> {
                            WrapperReturnNowPlaying wrapperReturnNowPlaying = service.getGlobalWhoKnowsTrack(10_000, trackId, discordId, false, false);
                            List<ReturnNowPlaying> returnNowPlayings = wrapperReturnNowPlaying.getReturnNowPlayings();
                            if (!returnNowPlayings.isEmpty()) {
                                ReturnNowPlaying returnNowPlaying = returnNowPlayings.get(0);
                                String holder = getPrivateString(returnNowPlaying.getDiscordId());
                                if (npModes.contains(NPMode.GLOBAL_TRACK_CROWN))
                                    footerSpaces[footerIndexes.get(NPMode.GLOBAL_TRACK_CROWN)] =
                                            "Global Track 👑 " + returnNowPlaying.getPlayNumber() + " (" + holder + ")";
                                if (npModes.contains(NPMode.GLOBAL_TRACK_RANK)) {
                                    for (int i = 0; i < returnNowPlayings.size(); i++) {
                                        ReturnNowPlaying searching = returnNowPlayings.get(i);
                                        if (searching.getDiscordId() == discordId) {
                                            footerSpaces[footerIndexes.get(NPMode.GLOBAL_TRACK_RANK)] = "Global Track Rank: " + (i + 1) + CommandUtil.getDayNumberSuffix(i + 1) + "/" + returnNowPlayings.size();
                                            if (npModes.contains(NPMode.GLOBAL_TRACK_RANK)) {
                                                previousNewLinesToAdd.add(footerIndexes.get(NPMode.GLOBAL_TRACK_CROWN));
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }));
                    }
                    break;
                case BOT_LISTENERS:
                case BOT_SCROBBLES:
                    if (!botStats.compareAndSet(false, true)) {
                        break;
                    }
                    completableFutures.add(CommandUtil.runLog(() -> {
                        String name = e.getJDA().getSelfUser().getName();
                        if (npModes.contains(NPMode.BOT_LISTENERS)) {
                            long artistFrequencies = service.getGlobalArtistFrequencies(scrobbledArtist.getArtistId());
                            footerSpaces[footerIndexes.get(NPMode.BOT_LISTENERS)] =
                                    (String.format("%d %s listeners", artistFrequencies, name));
                        }
                        if (npModes.contains(NPMode.BOT_SCROBBLES)) {
                            long artistFrequencies = service.getGlobalArtistPlays(scrobbledArtist.getArtistId());
                            footerSpaces[footerIndexes.get(NPMode.BOT_SCROBBLES)] =
                                    (String.format("%d %s plays", artistFrequencies, name));
                        }
                    }));

                    break;
                case GENDER:
                case COUNTRY:
                    if (!mbLock.compareAndSet(false, true)) {
                        break;
                    }
                    completableFutures.add(CommandUtil.runLog(() -> {
                        ArtistMusicBrainzDetails artistDetails = mb.getArtistDetails(new ArtistInfo(null, np.artistName(), np.artistMbid()));
                        if (npModes.contains(NPMode.GENDER) && artistDetails != null && artistDetails.gender() != null) {
                            footerSpaces[footerIndexes.get(NPMode.GENDER)] =
                                    artistDetails.gender();
                        }
                        if (npModes.contains(NPMode.COUNTRY) && artistDetails != null && artistDetails.countryCode() != null) {
                            footerSpaces[footerIndexes.get(NPMode.BOT_SCROBBLES)] =
                                    CountryCode.getByAlpha2Code(artistDetails.countryCode()).getName();
                        }
                    }));

                    break;
                case ALBUM_PLAYS:

                    if (preAlbumId != null) {

                        completableFutures.add(CommandUtil.runLog(() -> {
                            try {
                                AlbumUserPlays playsAlbumArtist = lastFM.getPlaysAlbumArtist(lastFMName, scrobbledArtist.getArtist(), np.albumName());
                                int plays = playsAlbumArtist.getPlays();
                                if (plays != 0) {
                                    footerSpaces[index] =
                                            (String.format("%d album %s", plays, CommandUtil.singlePlural(plays, "play", "plays")));
                                }
                            } catch (LastFmException ignored) {
                            }
                        }));
                    }
                    break;
                case SONG_PLAYS:
                case SONG_DURATION:

                    if (!trackLock.compareAndSet(false, true)) {
                        break;
                    }
                    completableFutures.add(CommandUtil.runLog(() -> {
                        try {
                            Track trackInfo = lastFM.getTrackInfo(lastFMName, scrobbledArtist.getArtist(), np.songName());
                            int plays = trackInfo.getPlays();
                            if (plays != 0) {
                                if (npModes.contains(NPMode.SONG_PLAYS))
                                    footerSpaces[footerIndexes.get(NPMode.SONG_PLAYS)] =
                                            (String.format("%d song %s", plays, CommandUtil.singlePlural(plays, "play", "plays")));
                            }
                            if (trackInfo.getDuration() != 0)
                                if (npModes.contains(NPMode.SONG_DURATION))
                                    footerSpaces[footerIndexes.get(NPMode.SONG_DURATION)] =
                                            (String.format("%02d:%02d minutes", trackInfo.getDuration() / 60000, trackInfo.getDuration() / 1000 % 60));
                        } catch (LastFmException ignored) {
                        }
                    }));
                    break;
                case HIGHEST_STREAK:
                    completableFutures.add(CommandUtil.runLog(() -> {
                        List<StreakEntity> userArtistTopStreaks = service.getUserArtistTopStreaks(discordId, scrobbledArtist.getArtistId(), 1);
                        if (!userArtistTopStreaks.isEmpty()) {
                            StreakEntity globalStreakEntities = userArtistTopStreaks.get(0);
                            footerSpaces[footerIndexes.get(NPMode.HIGHEST_STREAK)] =
                                    (String.format("%s 🔥 %d %s", userName, globalStreakEntities.artistCount(), CommandUtil.singlePlural(globalStreakEntities.artistCount(), "play", "plays")));
                        }
                    }));
                    break;
                case HIGHEST_SERVER_STREAK:
                    if (e.isFromGuild()) {
                        completableFutures.add(CommandUtil.runLog(() -> {
                            List<GlobalStreakEntities> artistTopStreaks = service.getArtistTopStreaks(null, e.getGuild().getIdLong(), scrobbledArtist.getArtistId(), 1);
                            if (!artistTopStreaks.isEmpty()) {
                                Consumer<GlobalStreakEntities> consumer = PrivacyUtils.consumer.apply(e, new AtomicInteger(0), x -> true);
                                GlobalStreakEntities globalStreakEntities = artistTopStreaks.get(0);
                                PrivacyUtils.PrivateString publicString = PrivacyUtils.getPublicString(globalStreakEntities.getPrivacyMode(), globalStreakEntities.getDiscordId(), globalStreakEntities.getLastfmId(), new AtomicInteger(0), e, Set.of(e.getAuthor().getIdLong()));
                                globalStreakEntities.setDisplayer(consumer);
                                String name = globalStreakEntities.getName().replace("*", "").substring(2).trim();
                                footerSpaces[index] =
                                        (String.format("%s 🔥 %d %s (%s)", e.getGuild().getName(), globalStreakEntities.artistCount(), CommandUtil.singlePlural(globalStreakEntities.artistCount(), "play", "plays"), name));
                            }
                        }));
                    }
                    break;
                case HIGHEST_BOT_STREAK:
                    completableFutures.add(CommandUtil.runLog(() -> {
                        List<GlobalStreakEntities> artistTopStreaks = service.getArtistTopStreaks(null, null, scrobbledArtist.getArtistId(), 1);
                        if (!artistTopStreaks.isEmpty()) {
                            Consumer<GlobalStreakEntities> consumer = PrivacyUtils.consumer.apply(e, new AtomicInteger(0), x -> false);
                            GlobalStreakEntities globalStreakEntities = artistTopStreaks.get(0);
                            globalStreakEntities.setDisplayer(consumer);
                            String name = globalStreakEntities.getName().replace("*", "").substring(2).trim();
                            footerSpaces[index] =
                                    (String.format("Global 🔥 %d %s (%s)", globalStreakEntities.artistCount(), CommandUtil.singlePlural(globalStreakEntities.artistCount(), "play", "plays"), name));
                        }
                    }));
                    break;
            }
        }

        return CompletableFuture.allOf(completableFutures.toArray(CompletableFuture<?>[]::new)).

                exceptionally(x -> null).

                thenAccept((x) ->

                {
                    previousNewLinesToAdd.forEach(this::addNewLineToPrevious);
                    List<String> footerLines = Arrays.stream(footerSpaces).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));

                    for (int i = 0; i < footerLines.size(); i++) {
                        if (npModes.contains(NPMode.TAGS) || npModes.contains(NPMode.EXTENDED_TAGS) && (i == 0)) {
                            continue;
                        }
                        Predicate<Integer> tester = (npModes.contains(NPMode.TAGS) ? (j) -> j % 2 != 0 : (j) -> j % 2 == 0);
                        String current = footerLines.get(i);
                        if (tester.test(i)) {
                            if (current.endsWith("\n")) {
                                footerLines.set(i, current.substring(0, current.length() - 1));
                            }
                        } else {
                            if (!current.endsWith("\n")) {
                                footerLines.set(i, current + "\n");
                            }
                        }
                    }
                    outputList.addAll(footerLines);
                    EnumSet<NPMode> checker = EnumSet.copyOf(this.npModes);
                    checker.remove(NPMode.TAGS);
                    checker.remove(NPMode.EXTENDED_TAGS);
                    if (

                            Sets.difference(checker, EnumSet.of(NPMode.HIGHEST_BOT_STREAK, NPMode.HIGHEST_SERVER_STREAK, NPMode.HIGHEST_STREAK)).isEmpty()
                            || Sets.difference(checker, EnumSet.of(NPMode.CROWN, NPMode.GLOBAL_CROWN)).isEmpty()
                            || Sets.difference(checker, EnumSet.of(NPMode.BOT_LISTENERS, NPMode.BOT_SCROBBLES)).isEmpty()
                            || Sets.difference(checker, EnumSet.of(NPMode.SERVER_LISTENERS, NPMode.SERVER_SCROBBLES)).isEmpty()
                            || Sets.difference(checker, EnumSet.of(NPMode.LFM_LISTENERS, NPMode.LFM_SCROBBLES)).isEmpty()
                            || Sets.difference(checker, EnumSet.of(NPMode.CROWN, NPMode.ALBUM_CROWN, NPMode.GLOBAL_CROWN, NPMode.GLOBAL_ALBUM_CROWN)).isEmpty()
                            || Sets.difference(checker, EnumSet.of(NPMode.SONG_DURATION, NPMode.SONG_PLAYS)).isEmpty()


                    ) {
                        for (int i = 0, outputListSize = outputList.size(); i < outputListSize; i++) {
                            String s = outputList.get(i);
                            if (!s.endsWith("\n")) {
                                outputList.set(i, s + "\n");
                            }
                        }
                    }
                });
    }

    public void addNewLineToPrevious(int currentIndex) {
        while (--currentIndex >= 0) {
            String footerSpace = footerSpaces[currentIndex];
            if (footerSpace == null) {
                continue;
            }
            if (!footerSpace.endsWith("\n")) {
                footerSpaces[currentIndex] += "\n";
                break;
            }
        }
    }

    private String getPrivateString(long discordId) {
        String holder;
        try {
            LastFMData lastFMData = service.findLastFMData(discordId);

            if (EnumSet.of(PrivacyMode.LAST_NAME, PrivacyMode.TAG, PrivacyMode.DISCORD_NAME).contains(lastFMData.getPrivacyMode())) {

                holder = switch (lastFMData.getPrivacyMode()) {
                    case DISCORD_NAME -> CommandUtil.getUserInfoUnescaped(e, discordId).username();
                    case TAG -> e.getJDA().retrieveUserById(lastFMData.getDiscordId()).complete().getAsTag();
                    case LAST_NAME -> lastFMData.getName() + " (lastfm)";
                    default -> "Private User #1";
                };
            } else holder = "Private User #1";
        } catch (InstanceNotFoundException exception) {
            holder = "Private User #1";
        }
        return holder;
    }

    public static class Sets {
        public static <T> Set<T> difference(Set<T> a, Set<T> b) {
            Set<T> copy = new HashSet<>(a);
            copy.removeIf(b::contains);
            return copy;
        }
    }
}
