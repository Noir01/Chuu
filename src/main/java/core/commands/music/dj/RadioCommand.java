package core.commands.music.dj;

import core.Chuu;
import core.commands.Context;
import core.commands.abstracts.MusicCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.GenreDisambiguator;
import core.commands.utils.PrivacyUtils;
import core.music.MusicManager;
import core.music.radio.*;
import core.parsers.EnumParser;
import core.parsers.Parser;
import core.parsers.params.EnumParameters;
import core.util.ServiceView;
import core.util.StringUtils;
import dao.everynoise.NoiseGenre;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.text.WordUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RadioCommand extends MusicCommand<EnumParameters<Station>> {
    public RadioCommand(ServiceView dao) {
        super(dao);
        respondInPrivate = false;
        requireManager = false;
        requireVoiceState = true;
    }


    @Override
    public Parser<EnumParameters<Station>> initParser() {
        return new EnumParser<>(Station.class, true, true, true);
    }

    @Override
    public String getDescription() {
        return "Plays music using random songs from the random pool";
    }

    @Override
    public List<String> getAliases() {
        return List.of("radio");
    }

    @Override
    public String getName() {
        return "Random Radio";
    }

    @Override
    public void onCommand(Context e, @Nonnull EnumParameters<Station> params) {
        Station element = params.getElement();
        String input = params.getParams();
        if (element == null) {
            EnumSet<Station> stations = EnumSet.allOf(Station.class);

            boolean falltrough = false;
            if (input != null) {
                String finalInput = StringUtils.WORD_SPLITTER.split(input)[0];
                if (finalInput.equalsIgnoreCase("stop")) {
                    MusicManager existing = Chuu.playerRegistry.getExisting(e.getGuild());
                    if (existing == null) {
                        sendMessageQueue(e, "There's no music playing in this server!");
                        return;
                    }
                    RadioTrackContext radio = existing.getRadio();
                    if (radio == null) {
                        sendMessageQueue(e, "There's no radio playing in this server!");
                        return;
                    }
                    e.sendMessage(new ChuuEmbedBuilder(e).setTitle("Radio stopped").setDescription("No longer streaming random songs from " + radio.getSource().getName()).build()).queue();
                    return;
                } else {
                    Optional<Station> found = stations.stream().filter(z -> z.getAliases().stream().anyMatch(w -> w.equalsIgnoreCase(finalInput))).findFirst();
                    falltrough = found.isEmpty();
                    if (!falltrough) {
                        element = found.get();
                        input = input.replaceFirst(finalInput, "").strip();
                    }
                }
            }

            if (input == null || falltrough) {

                String str = stations.stream().filter(Station::isActive).map(z -> "__**%s**__  ➜ %s".formatted(WordUtils.capitalizeFully(z.name()), z.getDescription())).collect(Collectors.joining("\n"));
                var eb = new ChuuEmbedBuilder(e).setDescription(str)
                        .setAuthor(e.getJDA().getSelfUser().getName() + "'s Radio stations", PrivacyUtils.getLastFmUser(Chuu.DEFAULT_LASTFM_ID), e.getJDA().getSelfUser().getAvatarUrl())
                        .setFooter("Do `" + e.getPrefix() + "radio stop` to cancel the radio\nExample: " + e.getPrefix() + "radio random");
                if (falltrough) {
                    eb.setTitle("Didn't find any station with that name");
                }
                e.sendMessage(eb.build()).queue();
                return;
            }
        }
        MusicManager musicManager = Chuu.playerRegistry.get(e.getGuild());

        RadioTrackContext a = switch (element) {
            case RANDOM -> new RandomRadioTrackContext(e.getAuthor().getIdLong(), e.getChannel().getIdLong(), new RandomRadio(params.hasOptional("server") ? e.getGuild().getIdLong() : -1, params.hasOptional("server")), -1, null);
            case RELEASES, GENRE -> {
                if (input == null) {
                    parser.sendError("Pls introduce the name of a genre to search for", e);
                    yield null;
                }
                Function<NoiseGenre, RadioTrackContext> factory;
                if (element == Station.RELEASES) {
                    factory = (s) -> new ReleaseRadioTrackContext(e.getAuthor().getIdLong(), e.getChannel().getIdLong(), new ReleaseRadio(s.name(), s.uri()), null, null, null, s.name(), s.uri());
                } else {
                    factory = (s) -> new GenreRadioTrackContext(e.getAuthor().getIdLong(), e.getChannel().getIdLong(), new GenreRadio(s.name(), s.uri()), s.name(), s.uri(), -1, 1);
                }
                new GenreDisambiguator(db).disambiguate(e, input, z -> new Params(musicManager, factory.apply(z)), this::buildEmbed);
                yield null;
            }
            case CURATED -> null;
        };
        if (a == null) {
            return;
        }
        buildEmbed(e, null, new Params(musicManager, a), input);


    }

    private void buildEmbed(Context e, @Nullable Message message, Params params, String input) {
        MusicManager musicManager = params.manager;
        musicManager.setRadio(params.context);


        MessageEmbed radio = new ChuuEmbedBuilder(e)
                .setTitle("Radio")
                .setDescription("Radio set to `%s`. The radio will be played when there's nothing else queued".formatted(params.context.getSource().getName())).build();

        RestAction<Message> messageRestAction;
        if (message == null) {
            messageRestAction = e.sendMessage(radio);
        } else {
            messageRestAction = e.editMessage(message, radio, Collections.emptyList());
        }
        messageRestAction.queue();
        if (musicManager.isIdle()) {
            musicManager.nextTrack();
        }
        if (e.getGuild().getSelfMember().getVoiceState() == null || !e.getGuild().getSelfMember().getVoiceState().inAudioChannel()) {
            if (e.getMember() != null && e.getMember().getVoiceState() != null && e.getMember().getVoiceState().inAudioChannel()) {
                AudioChannel channel = e.getMember().getVoiceState().getChannel();

                if (e.getGuild().getAudioManager().getConnectedChannel() != null) {
                    musicManager.moveAudioConnection(channel, e.getChannel());
                } else {
                    musicManager.openAudioConnection(channel, e);
                }
                musicManager.nextTrack();
            }
        }

    }

    record Params(MusicManager manager, RadioTrackContext context) {
    }

}
