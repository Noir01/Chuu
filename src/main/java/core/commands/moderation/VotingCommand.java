package core.commands.moderation;

import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.otherlisteners.*;
import core.parsers.ArtistParser;
import core.parsers.Parser;
import core.parsers.params.ArtistParameters;
import core.services.ColorService;
import dao.ServiceView;
import dao.entities.*;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.interactions.ActionRow;
import net.dv8tion.jda.api.interactions.Component;
import net.dv8tion.jda.api.interactions.button.Button;

import javax.validation.constraints.NotNull;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class VotingCommand extends ConcurrentCommand<ArtistParameters> {
    private static final String RIGHT_ARROW = "➡";
    private static final String LEFT_ARROW = "⬅";
    private static final String UP_VOTE = "👍";
    private static final String DOWN_VOTE = "👎";
    private static final String REPORT = "🚫";
    private static final String CANCEL = "🏳️";


    private final TriFunction<JDA, AtomicInteger, AtomicInteger, BiFunction<VotingEntity, EmbedBuilder, EmbedBuilder>> builder = (jda, size, counter) -> (votingEntity, embedBuilder) ->
            embedBuilder.clearFields()
                    .addField("Added by", CommandUtil.getGlobalUsername(jda, votingEntity.getOwner()), true)
                    .addBlankField(true)
                    .addField("Submitted:", votingEntity.getDateTime().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy MM dd HH:mm ")), true)
                    .addField("Points:", String.valueOf(votingEntity.getVotes()), true)
                    .addBlankField(true)
                    .addField("Vote Count:", String.valueOf(votingEntity.getTotalVotes()), true)
                    .setFooter("%d/%d".formatted(counter.get() + 1, size.get()) + "\nUse \uD83D\uDEAB to report this image")
                    .setImage(CommandUtil.noImageUrl(votingEntity.getUrl()))
                    .setColor(CommandUtil.pastelColor());

    public VotingCommand(ServiceView dao) {
        super(dao);
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.ARTIST_IMAGES;
    }

    @Override
    public Parser<ArtistParameters> initParser() {
        return new ArtistParser(db, lastFM);
    }

    @Override
    public String getDescription() {
        return "Vote for an image. The top voted image will be displayed in the bot commands";
    }

    @Override
    public List<String> getAliases() {
        return List.of("vote", "voting", "images", "image", "v");
    }

    @Override
    public String getName() {
        return "Vote";
    }

    @Override
    protected void onCommand(Context e, @NotNull ArtistParameters params) throws LastFmException, InstanceNotFoundException {


        long idLong = e.getAuthor().getIdLong();
        LastFMData lastFMData = db.findLastFMData(idLong);
        if (lastFMData.getRole() == Role.IMAGE_BLOCKED) {
            sendMessageQueue(e, "You don't have enough permissions to vote for images");
            return;
        }
        String preCorrectionArtist = params.getArtist();
        ScrobbledArtist artist = CommandUtil.onlyCorrection(db, preCorrectionArtist, lastFM, !params.isNoredirect());
        List<VotingEntity> allArtistImages = db.getAllArtistImages(artist.getArtistId());
        if (allArtistImages.isEmpty()) {
            sendMessageQueue(e, artist.getArtist() + " doesn't have any image");
            return;
        }
        String correctedArtist = CommandUtil.escapeMarkdown(allArtistImages.get(0).getArtist());

        EmbedBuilder embedBuilder = new ChuuEmbedBuilder(e)
                .setTitle(correctedArtist + " Images");
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger size = new AtomicInteger(allArtistImages.size());


        UnaryOperator<EmbedBuilder> finisher = finalEmbed -> {
            VotingEntity first = allArtistImages.stream().max(Comparator.comparingLong(VotingEntity::getVotes)).orElse(allArtistImages.isEmpty() ? null : allArtistImages.get(0));
            String description;
            String title;
            if (first == null) {
                title = "All images deleted";
                description = "This artist no longer has any image";
            } else {
                title = "Voting timed out";
                description = "Submitted by: " + CommandUtil.getGlobalUsername(e.getJDA(), first.getOwner()) + "\n\n";
                if (first != allArtistImages.get(0)) {
                    description += "The artist image for " + CommandUtil.escapeMarkdown(first.getArtist()) + " has changed to:";
                } else {
                    description += "The top voted image for " + CommandUtil.escapeMarkdown(first.getArtist()) + " is:";
                }
            }
            finalEmbed.setTitle(title)
                    .setImage(first != null ? first.getUrl() : null)
                    .clearFields()
                    .setDescription(description);
            if (first != null) {
                finalEmbed.setFooter(String.format("Has %d %s with %d%s", first.getVotes(), CommandUtil.singlePlural(first.getVotes(), "point", "points"), first.getTotalVotes(),
                        CommandUtil.singlePlural(first.getTotalVotes(), " vote", " votes")))
                        .setColor(ColorService.computeColor(e));
            } else {
                finalEmbed.setFooter(null);
            }
            return finalEmbed;
        };
        Supplier<VotingEntity> fetcher = () -> {
            if (counter.get() >= allArtistImages.size() - 1) {
                counter.set(allArtistImages.size() - 1);
            }
            if (counter.get() < 0) {
                counter.set(0);
            }
            if (allArtistImages.isEmpty()) {
                return null;
            }
            return allArtistImages.get(counter.get());
        };
        if (e.isFromGuild()) {
            var result = processButtonActions(e, allArtistImages, lastFMData, counter, size);
            new ButtonValidator<>(
                    finisher,
                    fetcher,
                    builder.apply(e.getJDA(), size, counter)
                    , embedBuilder, e, e.getAuthor().getIdLong(), result.map, result.rows, true, true);
        } else {
            var result = processReactionActions(e, allArtistImages, lastFMData, counter, size);
            new ReactValidator<>(
                    finisher,
                    fetcher,
                    builder.apply(e.getJDA(), size, counter)
                    , embedBuilder, e, e.getAuthor().getIdLong(), result, true, true);
        }

    }


    private Map<String, Reaction<VotingEntity, MessageReactionAddEvent, ReactionResult>> processReactionActions(Context e, List<VotingEntity> allArtistImages, LastFMData lastFMData, AtomicInteger counter, AtomicInteger size) {
        return generateActions(e,
                GenericMessageReactionEvent::getUser,
                () -> () -> false,
                (MessageReactionAddEvent r, Boolean t) -> ReactValidator.leftMove(allArtistImages.size(), counter, r, t),
                (MessageReactionAddEvent r, Boolean t) -> ReactValidator.rightMove(allArtistImages.size(), counter, r, t),
                allArtistImages, lastFMData, counter, size);
    }

    private Result processButtonActions(Context e, List<VotingEntity> allArtistImages, LastFMData lastFMData, AtomicInteger counter, AtomicInteger size) {
        Map<String, Reaction<VotingEntity, ButtonClickEvent, ButtonResult>> stringReactionMap = generateActions(e,
                GenericInteractionCreateEvent::getUser,
                () -> () -> new ButtonResult.Result(false, null),
                (ButtonClickEvent r, Boolean t) -> ButtonValidator.leftMove(allArtistImages.size(), counter, r, t),
                (ButtonClickEvent r, Boolean t) -> ButtonValidator.rightMove(allArtistImages.size(), counter, r, t),
                allArtistImages, lastFMData, counter, size);

        ActionRow of = ActionRow.of(Button.primary(UP_VOTE, Emoji.ofUnicode(UP_VOTE)),
                Button.primary(DOWN_VOTE, Emoji.ofUnicode(DOWN_VOTE)));
        List<ActionRow> rows = new ArrayList<>();
        rows.add(of);
        List<Component> components = of.getComponents();

        if (allArtistImages.size() > 1) {
            components.add(Button.primary(RIGHT_ARROW, Emoji.ofUnicode(RIGHT_ARROW)));
        }
        if (stringReactionMap.containsKey(CANCEL)) {
            rows.add(ActionRow.of(Button.danger(CANCEL, Emoji.ofUnicode(CANCEL))));
        }
        Button report = Button.danger(REPORT, Emoji.ofUnicode(REPORT));
        if (rows.size() == 2) {
            rows.get(1).getComponents().add(report);
        } else {
            rows.add(ActionRow.of(report));
        }
        return new Result(stringReactionMap, rows);
    }

    private <T extends Event, Y extends ReactionaryResult> Map<String, Reaction<VotingEntity, T, Y>> generateActions(Context e,
                                                                                                                     Function<T, User> owner,
                                                                                                                     Supplier<Y> normal,
                                                                                                                     BiFunction<T, Boolean, Y> left,
                                                                                                                     BiFunction<T, Boolean, Y> right,
                                                                                                                     List<VotingEntity> allArtistImages, LastFMData lastFMData, AtomicInteger counter, AtomicInteger size) {
        Map<String, Reaction<VotingEntity, T, Y>> actionMap = new LinkedHashMap<>();
        List<Long> guildList = e.isFromGuild()
                               ? db.getAll(e.getGuild().getIdLong()).stream().filter(u -> !u.getRole().equals(Role.IMAGE_BLOCKED)).map(UsersWrapper::getDiscordID).toList()
                               : List.of(e.getAuthor().getIdLong());

        actionMap.put(UP_VOTE, (a, r) -> {
            if (guildList.contains(owner.apply(r).getIdLong())) {
                VoteStatus voteStatus = db.castVote(a.getUrlId(), owner.apply(r).getIdLong(), true);
                if (voteStatus.equals(VoteStatus.CHANGE_VALUE)) {
                    a.changeToAPositive();
                } else if (voteStatus.equals(VoteStatus.NEW_VOTE)) {
                    a.add();
                    a.incrementTotalVotes();
                }
            }
            return normal.get();
        });

        actionMap.put(DOWN_VOTE, (a, r) -> {
            if (guildList.contains(owner.apply(r).getIdLong())) {
                VoteStatus voteStatus = db.castVote(a.getUrlId(), owner.apply(r).getIdLong(), false);
                if (voteStatus.equals(VoteStatus.CHANGE_VALUE)) {
                    a.changeToANegative();
                } else if (voteStatus.equals(VoteStatus.NEW_VOTE)) {
                    a.decrement();
                    a.incrementTotalVotes();
                }
            }
            return normal.get();
        });

        if (allArtistImages.size() > 1) {

            actionMap.put(LEFT_ARROW, (aliasEntity, r) -> left.apply(r, true));
            actionMap.put(RIGHT_ARROW, (a, r) -> right.apply(r, true));
        }

        actionMap.put(REPORT, (a, r) -> {
            if (guildList.contains(owner.apply(r).getIdLong())) {
                db.report(a.getUrlId(), owner.apply(r).getIdLong());
            }
            return normal.get();
        });

        if (lastFMData.getRole() == Role.ADMIN) {
            AtomicInteger deletedCounter = new AtomicInteger();
            actionMap.put(CANCEL, (a, r) -> {
                if (owner.apply(r).getIdLong() == e.getAuthor().getIdLong()) {
                    db.removeReportedImage(a.getUrlId(), a.getOwner(), owner.apply(r).getIdLong());
                    counter.decrementAndGet();
                    allArtistImages.removeIf(ve -> ve.getUrlId() == a.getUrlId());
                    size.decrementAndGet();
                }
                int i = allArtistImages.size() - 1;
                if (counter.get() == i && allArtistImages.size() > 0)
                    right.apply(r, false);
                else if (counter.get() < i) {
                    left.apply(r, false);
                }  // Do nothing
                return normal.get();
            });
        }


        return actionMap;
    }

    private record Result(Map<String, Reaction<VotingEntity, ButtonClickEvent, ButtonResult>> map,
                          List<ActionRow> rows) {
    }


}

