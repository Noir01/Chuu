package core.commands.charts;

import core.apis.last.entities.chartentities.ArtistChart;
import core.apis.last.entities.chartentities.UrlCapsule;
import core.commands.Context;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.commands.utils.PieDoer;
import core.otherlisteners.util.PaginatorBuilder;
import core.parsers.ChartableParser;
import core.parsers.OnlyChartSizeParser;
import core.parsers.params.ChartSizeParameters;
import core.parsers.utils.Optionals;
import core.util.ServiceView;
import dao.entities.ChartMode;
import dao.entities.CountWrapper;
import dao.entities.ResultWrapper;
import dao.entities.ScrobbledArtist;
import net.dv8tion.jda.api.EmbedBuilder;
import org.knowm.xchart.PieChart;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GuildTopCommand extends ChartableCommand<ChartSizeParameters> {

    public GuildTopCommand(ServiceView dao) {
        super(dao);
        this.respondInPrivate = false;
    }


    @Override
    public ChartableParser<ChartSizeParameters> initParser() {
        OnlyChartSizeParser onlyChartSizeParser = new OnlyChartSizeParser(db,
                Optionals.GLOBAL.opt.withDescription(" shows artist from all bot users instead of only from this server"));
        onlyChartSizeParser.replaceOptional("plays", Optionals.NOPLAYS.opt);
        onlyChartSizeParser.addOptional(Optionals.PLAYS.opt.withBlockedBy("noplays"));
        onlyChartSizeParser.setAllowUnaothorizedUsers(true);
        return onlyChartSizeParser;
    }

    @Override
    public String getSlashName() {
        return "artists";
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.SERVER_STATS;
    }

    @Override
    public String getDescription() {
        return ("Chart of a server most listened artist");
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("guild", "server", "general");
    }


    @Override
    public CountWrapper<BlockingQueue<UrlCapsule>> processQueue(ChartSizeParameters gp) {
        ChartMode effectiveMode = getEffectiveMode(gp);
        ResultWrapper<ScrobbledArtist> guildTop = db.getGuildTop(gp.hasOptional("global") ? null : gp.getE().getGuild().getIdLong(),
                gp.getX() * gp.getY(),
                !(effectiveMode.equals(ChartMode.IMAGE) && gp.chartMode().equals(ChartMode.IMAGE) || gp.chartMode().equals(ChartMode.IMAGE_ASIDE)));
        AtomicInteger counter = new AtomicInteger(0);
        BlockingQueue<UrlCapsule> guildTopQ = guildTop.getResultList().stream().sorted(Comparator.comparingInt(ScrobbledArtist::getCount).reversed()).
                map(x ->
                        new ArtistChart(x.getUrl(), counter.getAndIncrement(), x.getArtist(), null, x.getCount(), gp.isWriteTitles(), gp.isWritePlays(), gp.isAside())
                ).collect(Collectors.toCollection(LinkedBlockingDeque::new));
        return new CountWrapper<>(guildTop.getRows(), guildTopQ);
    }

    @Override
    public void doList(List<UrlCapsule> urlCapsules, ChartSizeParameters params, int count) {

        EmbedBuilder embedBuilder = configEmbed(new ChuuEmbedBuilder(params.getE()), params, count);
        new PaginatorBuilder<>(params.getE(), embedBuilder, urlCapsules).build().queue();
    }

    @Override
    public EmbedBuilder configEmbed(EmbedBuilder embedBuilder, ChartSizeParameters params, int count) {
        String titleInit = "'s top artists";
        String footerText = " has listened to " + count + " artists";
        String name = params.getE().getGuild().getName();
        return embedBuilder.setAuthor(name + titleInit,
                        null, params.getE().getGuild().getIconUrl())
                .setFooter(CommandUtil.stripEscapedMarkdown(name) + footerText);
    }

    @Override
    public void doPie(PieChart pieChart, ChartSizeParameters gp, int count) {
        String urlImage;
        String subtitle;
        if (gp.hasOptional("global")) {
            subtitle = configPieChart(pieChart, gp, count, gp.getE().getJDA().getSelfUser().getName());
            urlImage = gp.getE().getJDA().getSelfUser().getAvatarUrl();

        } else {
            subtitle = configPieChart(pieChart, gp, count, gp.getE().getGuild().getName());
            urlImage = gp.getE().getGuild().getIconUrl();
        }

        sendImage(new PieDoer(subtitle, urlImage, pieChart).fill(), gp.getE());
    }


    @Override
    public String configPieChart(PieChart pieChart, ChartSizeParameters params, int count, String initTitle) {
        pieChart.setTitle(initTitle + "'s top artists");
        return String.format("%s has listened to %d artists (showing top %d)", initTitle, count, params.getX() * params.getY());
    }

    @Override
    public void noElementsMessage(ChartSizeParameters gp) {
        Context e = gp.getE();
        if (gp.hasOptional("global")) {
            sendMessageQueue(e, "No one has listened a single artist in the whole bot");
        } else {
            sendMessageQueue(e, "No one has listened a single artist in this server");
        }
    }

    @Override
    public String getName() {
        return "Server Top Artists";
    }

}



