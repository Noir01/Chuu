package core.commands.albums;

import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.parsers.ArtistAlbumParser;
import core.parsers.Parser;
import core.parsers.params.ArtistAlbumParameters;
import core.services.validators.ArtistValidator;
import core.util.ServiceView;
import dao.entities.LastFMData;
import dao.entities.ScrobbledArtist;
import dao.exceptions.InstanceNotFoundException;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;


public class AlbumPlaysCommand extends ConcurrentCommand<ArtistAlbumParameters> {

    public AlbumPlaysCommand(ServiceView dao) {
        super(dao);
    }


    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.USER_STATS;
    }

    @Override
    public Parser<ArtistAlbumParameters> initParser() {
        return new ArtistAlbumParser(db, lastFM);
    }


    @Override
    public String getDescription() {
        return ("How many times you have heard an album!");
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("album");
    }

    @Override
    public String getName() {
        return "Get album plays";
    }

    @Override
    public void onCommand(Context e, @Nonnull ArtistAlbumParameters params) throws LastFmException, InstanceNotFoundException {

        ScrobbledArtist validable = new ArtistValidator(db, lastFM, e).validate(params.getArtist(), false, !params.isNoredirect());
        params.setScrobbledArtist(validable);
        doSomethingWithAlbumArtist(validable, params.getAlbum(), e, params.getLastFMData().getDiscordId(), params);

    }

    protected void doSomethingWithAlbumArtist(ScrobbledArtist artist, String album, Context e, long who, ArtistAlbumParameters params) throws LastFmException, InstanceNotFoundException {

        LastFMData data = params.getLastFMData();
        int a = lastFM.getPlaysAlbumArtist(data, artist.getArtist(), album).getPlays();
        String usernameString = data.getName();

        usernameString = getUserString(e, who, usernameString);

        String ending = a == 1 ? "time " : "times";

        sendMessageQueue(e, "**" + usernameString + "** has listened **" + CommandUtil.escapeMarkdown(album) + "** " + a + " " + ending);


    }


}
