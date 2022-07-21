package core.commands.charts;

import core.Chuu;
import core.apis.discogs.DiscogsApi;
import core.apis.discogs.DiscogsSingleton;
import core.apis.last.entities.chartentities.*;
import core.apis.last.queues.ArtistQueue;
import core.apis.spotify.Spotify;
import core.apis.spotify.SpotifySingleton;
import core.commands.Context;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.imagerenderer.GraphicUtils;
import core.parsers.ChartableParser;
import core.parsers.RainbowParser;
import core.parsers.params.RainbowParams;
import core.util.ServiceView;
import core.util.VirtualParallel;
import dao.entities.CountWrapper;
import dao.entities.DiscordUserDisplay;
import dao.entities.TimeFrameEnum;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

// Credits to http://thechurchofkoen.com/ for the idea and allowing me to do this command
public class RainbowChartCommand extends OnlyChartCommand<RainbowParams> {
    private final AtomicInteger maxConcurrency = new AtomicInteger(4);
    private final DiscogsApi discogsApi;
    private final Spotify spotifyApi;

    public RainbowChartCommand(ServiceView dao) {
        super(dao);
        discogsApi = DiscogsSingleton.getInstanceUsingDoubleLocking();
        spotifyApi = SpotifySingleton.getInstance();

    }

    @Override
    public ChartableParser<RainbowParams> initParser() {
        return new RainbowParser(db, TimeFrameEnum.ALL);

    }

    @Override
    public String getSlashName() {
        return "rainbow";
    }

    @Override
    protected boolean handleCommand(Context e) {
        if (maxConcurrency.decrementAndGet() == 0) {
            sendMessageQueue(e, "There are a lot of people executing this command right now, try again later :(");
            maxConcurrency.incrementAndGet();
            return true;
        } else {
            try {
                super.handleCommand(e);
                return true;
            } catch (Throwable ex) {
                Chuu.getLogger().warn(ex.getMessage(), ex);
                return false;
            } finally {
                maxConcurrency.incrementAndGet();
            }
        }
    }


    @Override
    public String getDescription() {
        return "A artist/album chart shown by colors";
    }

    @Override
    public List<String> getAliases() {
        return List.of("rainbow");
    }

    @Override
    public String getName() {
        return "Rainbow";
    }

    @Override
    public CountWrapper<BlockingQueue<UrlCapsule>> processQueue(RainbowParams param) throws LastFmException {
        BlockingQueue<UrlCapsule> queue;

        int count;
        int y = (int) (param.getY() * 1.4);
        int x = (int) (param.getX() * 1.4);
        if (y * x >= 500) {
            x = 10;
            y = 100;
        }
        if (param.isArtist()) {
            queue = new ArtistQueue(db, discogsApi, spotifyApi, true);
            count = lastFM.getChart(param.getUser(), param.getTimeFrameEnum(), x,
                    y, TopEntity.ARTIST, ChartUtil.getParser(param.getTimeFrameEnum(), TopEntity.ARTIST, param, lastFM, param.getUser()), queue);
        } else {
            queue = new ArrayBlockingQueue<>((x * y));
            count = lastFM.getChart(param.getUser(), param.getTimeFrameEnum(), x, y, TopEntity.ALBUM,
                    ChartUtil.getParser(param.getTimeFrameEnum(), TopEntity.ALBUM, param, lastFM, param.getUser()), queue);
        }
        boolean inverted = param.isInverse();
        boolean isColumn = param.isColumn();
        boolean isLinear = param.isLinear();


        List<UrlCapsule> temp = new ArrayList<>();
        queue.drainTo(temp);
        AtomicInteger coutner = new AtomicInteger(0);
        List<UrlCapsule> toProcess = temp.stream().filter(z -> !z.getUrl().isBlank()).takeWhile(z -> coutner.incrementAndGet() <= param.getX() * param.getY()).toList();
        int rows = param.getX();
        int cols = param.getY();
        if (toProcess.size() < rows * cols) {
            rows = (int) Math.floor(Math.sqrt(toProcess.size()));
            cols = rows;
            param.setX(rows);
            param.setY(cols);

        }
        long maxSize = (long) rows * cols;
        List<PreComputedChartEntity> preComputedItems = VirtualParallel.runIO(toProcess, maxSize, (UrlCapsule z) -> {
            String cover = Chuu.getCoverService().getCover(z.getArtistName(), z.getAlbumName(), z.getUrl(), param.getE());
            z.setUrl(cover);
            BufferedImage image = GraphicUtils.getImage(cover);
            if (image == null) {
                return null;
            }
            if (param.isColor()) {
                return new PreComputedByColor(z, image, inverted);
            } else {
                return new PreComputedByBrightness(z, image, inverted);
            }
        }).stream().sorted().toList();
        if (preComputedItems.size() < maxSize) {
            rows = (int) Math.floor(Math.sqrt(preComputedItems.size()));
            cols = rows;
            param.setX(rows);
            param.setY(cols);
        }
        if (rows == 0 || cols == 0) {
            sendMessageQueue(param.getE(), "Couldn't get enough covers for the rainbow :(");
        }
        if (isColumn) {
            int counter = 0;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    PreComputedChartEntity preComputed = preComputedItems.get(counter++);
                    preComputed.setPos(j * rows + i);
                }
            }
        } else ColorChartCommand.diagonalSort(rows, cols, preComputedItems, isLinear);
        queue = new LinkedBlockingQueue<>(Math.max(1, preComputedItems.size()));
        queue.addAll(preComputedItems);
        return new CountWrapper<>(count, queue);
    }

    @Override
    public EmbedBuilder configEmbed(EmbedBuilder embedBuilder, RainbowParams params, int count) {
        StringBuilder stringBuilder = new StringBuilder("top ").append(params.getX() * params.getY()).append(params.isArtist() ? " artist " : " albums ");
        stringBuilder.append(params.isColor() ? "by color" : "by brightness")
                .append(params.isInverse() ? " inversed" : "")
                .append(" ordered by ").append(params.isColumn() ? "column" : params.isLinear() ? "rows" : "diagonal");
        return params.initEmbed("'s " + stringBuilder, embedBuilder, " has listened to " + count + (params.isArtist() ? " artists" : " albums"), params.getUser().getName());

    }


    @Override
    public void noElementsMessage(RainbowParams parameters) {
        String s = parameters.isArtist() ? "artists" : "albums";
        Context e = parameters.getE();
        DiscordUserDisplay ingo = CommandUtil.getUserInfoEscaped(e, parameters.getDiscordId());
        sendMessageQueue(e, String.format("%s didn't listen to any %s%s!", ingo.username(), s, parameters.getTimeFrameEnum().getDisplayString()));

    }
}
