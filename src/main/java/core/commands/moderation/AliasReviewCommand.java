package core.commands.moderation;

import core.Chuu;
import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.otherlisteners.ReactValidator;
import core.otherlisteners.Reaction;
import core.otherlisteners.ReactionResult;
import core.otherlisteners.Reactions;
import core.parsers.NoOpParser;
import core.parsers.Parser;
import core.parsers.params.CommandParameters;
import core.util.ServiceView;
import dao.entities.AliasEntity;
import dao.entities.LastFMData;
import dao.entities.Role;
import dao.exceptions.DuplicateInstanceException;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

import javax.annotation.Nonnull;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class AliasReviewCommand extends ConcurrentCommand<CommandParameters> {
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final BiFunction<AliasEntity, EmbedBuilder, EmbedBuilder> builder = (aliasEntity, embedBuilder) ->
            embedBuilder.clearFields()
                    .addField("Alias:", aliasEntity.getAlias(), false)
                    .addField("Artist to be aliased:", aliasEntity.getArtistName(), false)
                    .addField("Added:", aliasEntity.getDateTime().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-dd-mm HH:mm 'UTC'")), false)
                    .setColor(CommandUtil.pastelColor());

    public AliasReviewCommand(ServiceView dao) {
        super(dao);
    }


    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public Parser<CommandParameters> initParser() {
        return NoOpParser.INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Aliases review";
    }

    @Override
    public List<String> getAliases() {
        return List.of("aliasreview", "alrev");
    }

    @Override
    public String getName() {
        return "Aliases Review";
    }

    @Override
    public void onCommand(Context e, @Nonnull CommandParameters params) throws InstanceNotFoundException {
        long idLong = e.getAuthor().getIdLong();
        LastFMData lastFMData = db.findLastFMData(idLong);
        if (lastFMData.getRole() != Role.ADMIN) {
            sendMessageQueue(e, "Only bot admins can review the alias queue!");
            return;
        }
        if (!this.isActive.compareAndSet(false, true)) {
            sendMessageQueue(e, "Other admin is reviewing the aliases, pls wait till they have finished!");
            return;
        }

        EmbedBuilder embedBuilder = new ChuuEmbedBuilder(e);

        try {

            HashMap<String, Reaction<AliasEntity, MessageReactionAddEvent, ReactionResult>> actionMap = new HashMap<>();
            actionMap.put(Reactions.ACCEPT, (aliasEntity, r) -> {
                try {
                    db.addAlias(aliasEntity.getAlias(), aliasEntity.getArtistId());
                    db.deleteAliasById(aliasEntity.getId());
                    r.getJDA().retrieveUserById(aliasEntity.getDiscorId(), false)
                            .queue(user -> user.openPrivateChannel()
                                    .flatMap(privateChannel -> privateChannel.sendMessage("Your alias: " + aliasEntity.getAlias() + " has been approved!"))
                                    .queue(), throwable -> Chuu.getLogger().warn(throwable.getMessage(), throwable));
                } catch (DuplicateInstanceException | InstanceNotFoundException ignored) {
                    try {
                        db.deleteAliasById(aliasEntity.getId());
                    } catch (InstanceNotFoundException ignored1) {
                        //Doesnt exists on the server
                    }
                }
                return () -> true;

            });


            actionMap.put(Reactions.REJECT, (a, r) -> {
                try {
                    db.deleteAliasById(a.getId());
                } catch (InstanceNotFoundException e1) {
                    Chuu.getLogger().error(e1.getMessage());
                }
                return () -> true;
            });
            new ReactValidator<>(
                    embedBuilder1 -> embedBuilder.setTitle("No more  Aliases to Review").clearFields(),
                    db::getNextInAliasQueue,
                    builder
                    , embedBuilder, e, e.getAuthor().getIdLong(), actionMap, false, false, e.getChannel().getIdLong());
        } catch (Exception ex) {
            Chuu.getLogger().warn(ex.getMessage());
        } finally {
            this.isActive.set(false);
        }


    }
}
