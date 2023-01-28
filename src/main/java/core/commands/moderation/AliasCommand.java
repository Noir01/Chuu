package core.commands.moderation;

import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmEntityNotFoundException;
import core.exceptions.LastFmException;
import core.parsers.Parser;
import core.parsers.TwoArtistsParser;
import core.parsers.params.TwoArtistParams;
import core.util.ServiceView;
import dao.entities.ArtistSummary;
import dao.entities.LastFMData;
import dao.entities.Role;
import dao.exceptions.DuplicateInstanceException;
import dao.exceptions.InstanceNotFoundException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AliasCommand extends ConcurrentCommand<TwoArtistParams> {

    public AliasCommand(ServiceView dao) {
        super(dao);
    }


    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public Parser<TwoArtistParams> initParser() {
        return new TwoArtistsParser();
    }

    @Override
    public String getDescription() {
        return "Let's you alias an artist to another";
    }

    @Override
    public List<String> getAliases() {
        return List.of("alias");
    }

    @Override
    public String getName() {
        return "Alias";
    }

    @Override
    public void onCommand(Context e, @NotNull TwoArtistParams params) throws LastFmException, InstanceNotFoundException {

        long idLong = e.getAuthor().getIdLong();
        LastFMData lastFMData = db.findLastFMData(idLong);
        String alias = params.getFirstArtist();
        String to = params.getSecondArtist();
        long artistId;
        String corrected = db.findCorrection(alias);
        if (corrected != null) {
            sendMessageQueue(e, "The alias **%s** cannot be used because there is already an artist with that name".formatted(CommandUtil.escapeMarkdown(alias)));
            return;
        }
        try {
            artistId = db.getArtistId(to);
        } catch (InstanceNotFoundException ex) {
            sendMessageQueue(e, "Cannot use the artist **%s** as a target of an alias because it doesn't exist on the bot".formatted(CommandUtil.escapeMarkdown(to)));
            return;
        }
        try {
            db.getArtistId(alias);
            sendMessageQueue(e, "The alias: **%s** cannot be used because there is already an artist with that name".formatted(CommandUtil.escapeMarkdown(alias)));
            return;
        } catch (InstanceNotFoundException ex) {
            try {
                ArtistSummary artistSummary = lastFM.getArtistSummary(alias, lastFMData);
                if (artistSummary.listeners() > 1000) {
                    sendMessageQueue(e, "The alias: **%s** cannot be used because there is already an artist with that name".formatted(CommandUtil.escapeMarkdown(alias)));
                    return;
                }
            } catch (LastFmEntityNotFoundException ignored) {
                //We know it doesnt exists on last
            }
        }

        if (!lastFMData.getRole().equals(Role.ADMIN)) {
            db.enqueAlias(alias, artistId, idLong);
            sendMessageQueue(e, "Your alias will be added to the review queue");

        } else {
            try {
                db.addAlias(alias, artistId);
                sendMessageQueue(e, "Successfully aliased " + alias + " to " + to);
            } catch (DuplicateInstanceException ex) {
                sendMessageQueue(e, "The alias: " + alias + " is an already existing alias within the bot");
            }
        }


    }
}
