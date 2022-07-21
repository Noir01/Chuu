package core.commands.loved;

import core.apis.last.entities.Scrobble;
import core.commands.Context;
import core.commands.albums.AlbumPlaysCommand;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.parsers.ArtistSongParser;
import core.parsers.Parser;
import core.parsers.params.ArtistAlbumParameters;
import core.services.validators.TrackValidator;
import core.util.ServiceView;
import dao.entities.LastFMData;
import dao.entities.ScrobbledArtist;
import dao.entities.ScrobbledTrack;

import java.util.List;

public class LoveCommand extends AlbumPlaysCommand {
    public LoveCommand(ServiceView dao) {
        super(dao);
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.LOVE;
    }

    @Override
    public Parser<ArtistAlbumParameters> initParser() {
        return new ArtistSongParser(db, lastFM);
    }

    @Override
    public String getDescription() {
        return "Loves a song on last.fm";
    }

    @Override
    public List<String> getAliases() {
        return List.of("love");
    }

    @Override
    public String getName() {
        return "Love";
    }

    protected void doSomethingWithAlbumArtist(ScrobbledArtist artist, String song, Context e, long who, ArtistAlbumParameters params) throws LastFmException {
        LastFMData user = params.getLastFMData();
        if (user.getDiscordId() != e.getAuthor().getIdLong()) {
            sendMessageQueue(e, "Cant love on behalf of other users??");
            return;
        }
        if (user.getSession() == null || user.getSession().isBlank()) {
            sendMessageQueue(e, "Only users that have linked their account via `%slogin` can love/unlove songs!".formatted(CommandUtil.getMessagePrefix(e)));
            return;
        }
        lastFM.love(user.getSession(), new Scrobble(artist.getArtist(), null, song, null, null));

        ScrobbledTrack sT = new TrackValidator(db, lastFM).validate(artist.getArtistId(), artist.getArtist(), song);
        ScrobbledTrack trackInfo = db.getTrackInfo(user.getName(), sT.getTrackId());
        String header;
        if (trackInfo != null && trackInfo.isLoved()) {
            header = "You already had loved";
        } else {
            header = "Successfully loved";
        }
        sendMessageQueue(e, "%s **%s** by **%s**.".formatted(header, song, artist.getArtist()));
        db.loveSong(user.getName(), sT.getTrackId(), true);
    }
}
