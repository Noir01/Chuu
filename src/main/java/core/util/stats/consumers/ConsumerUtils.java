package core.util.stats.consumers;

import core.commands.utils.CommandUtil;
import core.util.stats.StatsCtx;
import core.util.stats.generator.GeneratorUtils;
import dao.entities.CountWrapper;
import dao.entities.ScrobbledAlbum;
import dao.entities.ScrobbledArtist;
import dao.entities.ScrobbledTrack;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConsumerUtils {
    private static final DecimalFormat format = new DecimalFormat("#.##");
    private static final DecimalFormat oneDec = new DecimalFormat("#.#");

    private static long days(StatsCtx ctx) {
        return Duration.between(Instant.ofEpochSecond(ctx.timestamp()), Instant.now()).get(ChronoUnit.SECONDS) / (60 * 60 * 24);
    }

    public static String plays(StatsCtx ctx) {
        long days = days(ctx);
        return "**Scrobbles**: %d _(%s/day)_".formatted(ctx.totalPlays(), oneDec.format(ctx.totalPlays() / (float) days));
    }

    public static String artist(CountWrapper<List<ScrobbledArtist>> artists, StatsCtx ctx) {
        int count = artists.getRows();
        return "**Artists**: %d _(%s scrobbles/artist)_".formatted(count, oneDec.format(ctx.totalPlays() / (float) count));
    }

    public static String albums(CountWrapper<List<ScrobbledAlbum>> albums, StatsCtx ctx) {
        int count = albums.getRows();
        return "**Albums**: %d _(%s scrobbles/album)_".formatted(count, oneDec.format(ctx.totalPlays() / (float) count));
    }

    public static String songs(CountWrapper<List<ScrobbledTrack>> songs, StatsCtx ctx) {
        int count = songs.getRows();
        return "**Songs**: %d _(%s scrobbles/songs)_".formatted(count, oneDec.format(ctx.totalPlays() / (float) count));
    }

    public static <T extends ScrobbledArtist> String range(CountWrapper<List<T>> cw, StatsCtx ctx,
                                                           Operation operation, Entity entity
    ) {
        int queried = Optional.ofNullable(ctx.count()).orElse(30);
        List<T> items = cw.getResult();
        long count = items.stream().mapToInt(ScrobbledArtist::getCount).filter(operation.filter(queried)).count();
        return "**%s with %s %d %s**: %s".formatted(entity.format(), operation.phrase(), queried, CommandUtil.singlePlural(queried, "scrobble", "scrobbles"), count);
    }

    public static String sumTop(CountWrapper<List<ScrobbledArtist>> artists, StatsCtx ctx) {
        int limit = Optional.ofNullable(ctx.count()).orElse(10);

        int playCount = ctx.totalPlays();
        long sum = sumTop(limit, artists);
        return "Your top **%s** artists account for **%s%%** of your total scrobbles".formatted(limit, format.format(sum * 100. / playCount));
    }

    public static String top(CountWrapper<List<ScrobbledArtist>> items, StatsCtx ctx) {
        int limit = Optional.ofNullable(ctx.count()).orElse(10);

        long sum = sumTop(limit, items);
        return "Total scrobbles for top **%d** artists: _%d_".formatted(limit, sum);
    }

    public static <T extends ScrobbledArtist> long HIndex(CountWrapper<List<T>> cw) {
        List<T> list = cw.getResult();
        return IntStream.range(0, list.size()).takeWhile(z -> list.get(z).getCount() >= z).count();
    }

    public static <T extends ScrobbledArtist> String entityAt(CountWrapper<List<T>> cw, StatsCtx ctx, Entity entity) {
        int rank = Optional.ofNullable(ctx.count()).orElse(1);
        if (cw.getRows() < rank || rank <= 0) {
            return "You don't have " + rank + " " + entity.format();
        }
        int realRank = rank - 1;
        T item = cw.getResult().get(realRank);
        String rnkStr = CommandUtil.getRank(rank);
        String playsStr = " | %d %s".formatted(item.getCount(), CommandUtil.singlePlural(item.getCount(), "play", "plays"));
        if (item instanceof ScrobbledAlbum s) {
            return "%d%s %s: **%s** by **%s**%s".formatted(rank, rnkStr, entity.formatSingular(), s.getAlbum(), s.getArtist(), playsStr);
        } else if (item instanceof ScrobbledTrack t) {
            return "%d%s %s: **%s** by **%s**%s".formatted(rank, rnkStr, entity.formatSingular(), t.getName(), t.getArtist(), playsStr);
        }
        return "%d%s %s: **%s**%s".formatted(rank, rnkStr, entity.formatSingular(), item.getArtist(), playsStr);
    }

    public static <T extends ScrobbledArtist> long sumTop(int limit, CountWrapper<List<T>> items) {
        return items.getResult().stream().mapToInt(ScrobbledArtist::getCount).limit(limit).sum();
    }

    public static String breadth(GeneratorUtils.AllCached cached, StatsCtx ctx) {
        CountWrapper<List<ScrobbledArtist>> a = cached.a();
        float sumTop = sumTop(10, a);
        long hIndex = HIndex(a);
        long top50 = topPercentage(a, ctx, 50);
        var rating = (Math.log((top50 * Math.pow(hIndex, 1.5)) / sumTop + 1) / Math.log(2)) * 5;

        var ratingString =
                rating > 40
                        ? "what the fuck"
                        : rating > 35
                        ? "really high!"
                        : rating > 30
                        ? "very high"
                        : rating > 20
                        ? "high"
                        : rating > 10
                        ? "medium"
                        : rating > 5
                        ? "low"
                        : rating > 1
                        ? "very low"
                        : ".... really?";

        return "**Breadth rating**: %s (%s)".formatted(format.format(rating), ratingString);
    }

    public static String scrobbleAverages(GeneratorUtils.AllCached cached, StatsCtx ctx) {
        int alSize = cached.al().getRows();
        int trSize = cached.tr().getRows();
        int aSize = cached.a().getRows();
        int sbCount = ctx.totalPlays();
        return """
                Scrobbles per artist: %s
                Scrobbles per album: %s
                Scrobbles per song: %s""".formatted(
                oneDec.format(aSize * 100. / sbCount),
                oneDec.format(alSize * 100. / sbCount),
                oneDec.format(trSize * 100. / sbCount)
        );
    }

    public static String averages(GeneratorUtils.AllCached cached) {
        int alSize = cached.al().getRows();
        int trSize = cached.tr().getRows();
        int aSize = cached.a().getRows();

        return """
                Albums per artist: %s
                Tracks per artist: %s
                Tracks per album: %s""".formatted(
                oneDec.format((float) alSize / aSize),
                oneDec.format((float) trSize / aSize),
                oneDec.format((float) trSize / alSize)
        );
    }

    public static <T extends ScrobbledArtist> String percentage(CountWrapper<List<T>> list, StatsCtx ctx, Entity entity) {
        int per = Optional.ofNullable(ctx.count()).orElse(10);
        long numberToReach = topPercentage(list, ctx, per);
        return "**\# of %s to equal %s%% of scrobbles**: %d".formatted(entity.format().toLowerCase(Locale.ROOT), per, numberToReach);
    }


    private static <T extends ScrobbledArtist> long topPercentage(CountWrapper<List<T>> cw, StatsCtx ctx, int percentage) {
        int playCount = ctx.totalPlays();
        double limit = playCount * (percentage / 100f);
        LongAdder longAdder = new LongAdder();
        List<T> list = cw.getResult();
        long summed = IntStream.range(0, list.size()).takeWhile(i -> {
            int count = list.get(i).getCount();
            if (longAdder.longValue() + count < limit) {
                longAdder.add(count);
                return true;
            }
            return false;
        }).count();
        return summed == 0 ? 1 : summed;
    }

    public static <T extends ScrobbledArtist> String breakdowns(CountWrapper<List<T>> cw, Entity entity, StatsCtx ctx) {

        List<T> list = cw.getResult();
        if (cw.getRows() == 0) {
            return "";
        }
        int start;
        Range[] ranges = new Range[5];

        ScrobbledArtist scrobbledArtist = list.get(0);
        int count = scrobbledArtist.getCount();
        if (count >= 1000) {
            start = (count / 1000) * 1000;
        } else if (count >= 100) {
            start = (count / 100) * 100;
        } else {
            start = 100;
        }
        Range range = new Range(start, start / 2);
        ranges[0] = range;
        for (int i = 1; i < 5; i++) {
            range = new Range(range.end, range.end / 2);
            ranges[i] = range;
        }
        Map<Range, Long> collect = list.stream().map(ScrobbledArtist::getCount)
                .filter(z -> z >= ranges[4].end)
                .collect(Collectors.groupingBy(z -> {
                    for (int j = 0, rangesLength = ranges.length; j < rangesLength; j++) {
                        Range index = ranges[j];
                        if (j == 0) {
                            if (z > index.start) {
                                return index;
                            }
                        }
                        if (z < index.start && z >= index.end) {
                            return index;
                        }
                    }
                    return null;
                }, Collectors.counting()));
        String header = ctx.timeFrameEnum().isAllTime() ? "In your library" : StringUtils.capitalize(ctx.timeFrameEnum().getDisplayString().trim());
        return header + " you have...\n" + collect.entrySet().stream().sorted(Comparator.comparingInt(z -> -z.getKey().start))
                .map(z -> "**%d** %s with %d+ scrobbles".formatted(z.getValue(), entity.format().toLowerCase(Locale.ROOT), z.getKey().end))
                .collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    // TODO use query and database id
    public static <T extends ScrobbledArtist> String concretePercentage(GeneratorUtils.Np<T> genNp, StatsCtx ctx, Entity entity) {
        CountWrapper<List<T>> cw = genNp.entities();
        List<T> entities = cw.getResult();
        ScrobbledTrack np = genNp.np();
        int plays = switch (entity) {
            case ALBUM -> {
                long albumId = np.getAlbumId();
                if (albumId < 1) {
                    yield 0;
                }
                List<ScrobbledAlbum> albums = (List<ScrobbledAlbum>) entities;
                yield albums.stream().filter(a -> a.getArtist().equalsIgnoreCase(np.getArtist()) && np.getAlbum().equalsIgnoreCase(a.getAlbum())).findFirst().map(ScrobbledArtist::getCount).orElse(0);
            }
            case TRACK -> {
                long trackId = np.getTrackId();
                if (trackId < 1) {
                    yield 0;
                }
                List<ScrobbledTrack> songs = (List<ScrobbledTrack>) entities;
                yield songs.stream().filter(a -> a.getArtist().equalsIgnoreCase(np.getArtist()) && np.getName().equalsIgnoreCase(a.getName())).findFirst().map(ScrobbledArtist::getCount).orElse(0);
            }
            case ARTIST -> {
                long artistId = np.getArtistId();
                if (artistId < 1) {
                    yield 0;
                }
                List<ScrobbledArtist> songs = (List<ScrobbledArtist>) entities;
                yield songs.stream().filter(a -> a.getArtist().equalsIgnoreCase(np.getArtist())).findFirst().map(ScrobbledArtist::getCount).orElse(0);
            }
        };
        return "%s percentage of total %s: %s%%".formatted(entity.fromNp(np), entity.format().toLowerCase(Locale.ROOT), format.format(plays * 100. / ctx.totalPlays()));

    }

    @SuppressWarnings("unchecked")

    public static <T extends ScrobbledArtist> String concreteRank(GeneratorUtils.Np<T> genNp, Entity artist) {
        CountWrapper<List<T>> cw = genNp.entities();
        List<T> entities = cw.getResult();
        ScrobbledTrack np = genNp.np();
        int index = switch (artist) {
            case ALBUM -> {
                long albumId = np.getAlbumId();
                if (albumId < 1) {
                    yield -1;
                }
                List<ScrobbledAlbum> albums = (List<ScrobbledAlbum>) entities;
                yield ListUtils.indexOf(albums, (a) -> a.getArtist().equalsIgnoreCase(np.getArtist()) && np.getAlbum().equalsIgnoreCase(a.getAlbum()));
            }
            case TRACK -> {
                long trackId = np.getTrackId();
                if (trackId < 1) {
                    yield -1;
                }
                List<ScrobbledTrack> songs = (List<ScrobbledTrack>) entities;
                yield ListUtils.indexOf(songs, (a) -> a.getArtist().equalsIgnoreCase(np.getArtist()) && np.getName().equalsIgnoreCase(a.getName()));
            }
            case ARTIST -> {
                long artistId = np.getArtistId();
                if (artistId < 1) {
                    yield -1;
                }
                List<ScrobbledArtist> songs = (List<ScrobbledArtist>) entities;
                yield ListUtils.indexOf(songs, (a) -> a.getArtist().equalsIgnoreCase(np.getArtist()));
            }
        };
        String indexStr;
        String playsStr = "";
        if (index == -1) {
            indexStr = "Not found";
        } else {
            indexStr = index + 1 + CommandUtil.getRank(index + 1);
            int count = cw.getResult().get(index).getCount();
            playsStr = " | %d %s".formatted(count, CommandUtil.singlePlural(count, "play", "plays"));

        }
        return "Rank of %s: %s%s".formatted(artist.fromNp(np), indexStr, playsStr);
    }

    public enum Entity {
        ALBUM, TRACK, ARTIST;

        public String format() {
            return switch (this) {
                case ALBUM -> "Albums";
                case TRACK -> "Songs";
                case ARTIST -> "Artists";
            };
        }

        public String formatSingular() {
            return switch (this) {
                case ALBUM -> "album";
                case TRACK -> "song";
                case ARTIST -> "artist";
            };
        }

        public String mapNull(String a) {
            if (a == null) {
                return "_unknown_";
            }
            return a;
        }

        public String mapNull(String a, String replacement) {
            if (a == null) {
                return mapNull(replacement);
            }
            return a;
        }

        public String fromNp(ScrobbledTrack np) {
            return switch (this) {
                case ALBUM ->
                        "**%s** by **%s**".formatted(mapNull(WordUtils.capitalize(np.getAlbum()), WordUtils.capitalize(np.getName())), WordUtils.capitalize(np.getArtist()));
                case TRACK ->
                        "**%s** by **%s**".formatted(mapNull(WordUtils.capitalize(np.getName())), WordUtils.capitalize(np.getArtist()));
                case ARTIST -> "**%s**".formatted(WordUtils.capitalize(np.getArtist()));
            };
        }
    }


    public enum Operation {
        EQUAL, OVER, UNDER;

        public IntPredicate filter(int queried) {
            return switch (this) {
                case EQUAL -> i -> i == queried;
                case OVER -> i -> i >= queried;
                case UNDER -> i -> i < queried;
            };
        }

        public String phrase() {
            return switch (this) {
                case EQUAL -> "";
                case OVER -> "more than ";
                case UNDER -> "less than ";
            };
        }
    }

    private record Range(int start, int end) {
    }
}
