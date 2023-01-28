package core.apis.last.queues;

import core.Chuu;
import core.apis.discogs.DiscogsApi;
import core.apis.last.entities.chartentities.TrackDurationChart;
import core.apis.last.entities.chartentities.UrlCapsule;
import core.apis.spotify.Spotify;
import core.commands.utils.CommandUtil;
import dao.ChuuService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class TrackQueue extends ArtistQueue {

    public TrackQueue(ChuuService dao, DiscogsApi discogsApi, Spotify spotify, boolean doImages) {
        super(dao, discogsApi, spotify, doImages);
    }

    @Override
    public boolean offer(@NotNull UrlCapsule item) {
        if (item.getUrl() == null || item.getUrl().isBlank() || item.getUrl().equals(TrackGroupAlbumQueue.defaultTrackImage)) {
            return super.offer(item);
        }
        return wrapper.offer(CompletableFuture.completedFuture(item));
    }

    public int setUp(int limit) {
        ArrayList<CompletableFuture<UrlCapsule>> temporalFutures = new ArrayList<>();
        wrapper.drainTo(temporalFutures);
        List<UrlCapsule> awaitedCapsules = temporalFutures.stream().map(x -> {
            try {
                return x.get();
            } catch (InterruptedException | ExecutionException e) {
                Chuu.getLogger().info(e.getMessage(), e);
                return null;
            }
        }).filter(Objects::nonNull).toList();
        wrapper.clear();

        AtomicInteger ranker = new AtomicInteger(0);
        AtomicInteger secondsCounter = new AtomicInteger(0);

        wrapper.addAll(
                awaitedCapsules.stream().sorted(Comparator.comparingInt(o -> ((TrackDurationChart) o).getSeconds()).reversed()).peek(x -> {
                    secondsCounter.addAndGet(((TrackDurationChart) x).getSeconds());
                    x.setPos(ranker.getAndIncrement());
                }).takeWhile(y ->
                        y.getPos() < limit
                ).map(z -> CommandUtil.supplyLog(() -> z)
                ).toList());
        return secondsCounter.get();
    }
}
