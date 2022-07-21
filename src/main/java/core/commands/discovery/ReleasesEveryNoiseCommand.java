package core.commands.discovery;

import core.apis.spotify.SpotifyUtils;
import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.commands.utils.GenreDisambiguator;
import core.exceptions.LastFmException;
import core.otherlisteners.util.Paginator;
import core.otherlisteners.util.PaginatorBuilder;
import core.parsers.Parser;
import core.parsers.UserStringParser;
import core.parsers.params.UserStringParameters;
import core.util.ServiceView;
import dao.everynoise.NoiseGenreReleases;
import dao.everynoise.Release;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.text.WordUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReleasesEveryNoiseCommand extends ConcurrentCommand<UserStringParameters> {
    public static final String RELEASES_URL = "https://everynoise.com/new_releases_by_genre.cgi?genre=%s&region=GB&albumsonly=true&hidedupes=on";

    public ReleasesEveryNoiseCommand(ServiceView dao) {
        super(dao);
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.DISCOVERY;
    }

    @Override
    public Parser<UserStringParameters> initParser() {
        return new UserStringParser(db, true);
    }

    @Override
    public String getDescription() {
        return "List all of the genres supplied in everynoise.com or find one by name";
    }

    @Override
    public List<String> getAliases() {
        return List.of("releases", "newralbums", "releasegenre", "releasenoise", "everynoiserelease");
    }

    @Override
    public String getName() {
        return "Everynoise releases";
    }

    @Override
    public void onCommand(Context e, @Nonnull UserStringParameters params) throws LastFmException, InstanceNotFoundException {
        String input = params.getInput();
        boolean isSearchResult = !input.isBlank();
        if (!isSearchResult) {
            handleList(e);
        } else {
            handleSearch(e, input);
        }

    }


    private void handleList(Context e) {
        Map<NoiseGenreReleases, Integer> counts = db.releasesByCount();
        String tab = EmbedBuilder.ZERO_WIDTH_SPACE + "\t\t";


        var strings = counts.entrySet().stream().toList();

        EmbedBuilder eb = new ChuuEmbedBuilder(e)
                .setAuthor("Genres with releases", "https://everynoise.com/", "https://pbs.twimg.com/profile_images/3736544396/e0d7d0c8f2781c40b5f870df441e670c_400x400.png")
                .setFooter("There are  " + strings.size() + " unique genres with releases");


        Function<Map.Entry<NoiseGenreReleases, Integer>, String> mapper = z -> {
            NoiseGenreReleases key = z.getKey();
            Integer count = z.getValue();
            String releases = z.getKey().releases().stream().limit(2).map(l -> "%s[%s - %s](%s)".formatted(tab, l.artist(), l.release(), SpotifyUtils.getAlbumLink(l.uri()))).collect(Collectors.joining("\n"));
            if (releases.isBlank()) {
                releases += tab;
            }
            if (key.releases().size() > 2) {
                releases += "\n" + tab + "...";
            }
            return "**[%s](%s)**: %d %s\n%s\n\n".formatted(key.name(),
                    RELEASES_URL.formatted(URLEncoder.encode(key.name(), StandardCharsets.UTF_8)),
                    count,
                    CommandUtil.singlePlural(count, "release", "releases"), releases);
        };

        new PaginatorBuilder<>(e, eb, strings).mapper(mapper).pageSize(5).unnumered().build().queue();

    }

    private void handleSearch(Context e, String input) {
        new GenreDisambiguator(db)
                .disambiguate(e, input, db::releasesOfGenre, this::buildEmbed);

    }

    private void buildEmbed(Context e, @Nullable Message message, List<Release> releases, String input) {
        if (releases.isEmpty()) {
            if (message == null) {
                sendMessageQueue(e, "Couldn't find any release searching by `%s`".formatted(input));
            } else {
                e.editMessage(message, new ChuuEmbedBuilder(e).setTitle("Didn't find any new release by " + input).build(), Collections.emptyList()).queue();
            }
            return;
        }
        List<String> strings = releases.stream().map(z -> "**[%s - %s](%s)**\n".formatted(z.artist(), z.release(), SpotifyUtils.getAlbumLink(z.uri()))).toList();
        StringBuilder a = new StringBuilder();
        for (int i = 0; i < 10 && i < strings.size(); i++) {
            a.append(strings.get(i));
        }
        ChuuEmbedBuilder eb = new ChuuEmbedBuilder(e);
        String title = WordUtils.capitalize(input) + " releases";
        String footer = "";
        if (releases.size() > 10) {
            eb.setFooter("Found " + releases.size() + " " + input + " releases ");
        }
        eb.setDescription(a)
                .setAuthor(title, "https://everynoise.com/", "https://pbs.twimg.com/profile_images/3736544396/e0d7d0c8f2781c40b5f870df441e670c_400x400.png");
        BiFunction<Context, EmbedBuilder, RestAction<Message>> sender, senderWithButtons;
        if (message == null) {
            sender = (context, embedBuilder) -> context.sendMessage(embedBuilder.build());
            senderWithButtons = (context, embedBuilder) -> context.sendMessage(embedBuilder.build(), Paginator.defaultActions());
        } else {
            sender = (context, embedBuilder) -> context.editMessage(message, embedBuilder.build(), Collections.emptyList());
            senderWithButtons = (context, embedBuilder) -> context.editMessage(message, embedBuilder.build(), List.of(Paginator.defaultActions()));
        }

        new PaginatorBuilder<>(e, eb, strings).unnumered()
                .creator(sender)
                .creatorWithButtons(senderWithButtons)
                .build().queue();

    }
}
