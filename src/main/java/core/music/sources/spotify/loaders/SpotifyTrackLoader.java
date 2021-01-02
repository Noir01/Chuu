package core.music.sources.spotify.loaders;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Track;
import dao.exceptions.ChuuServiceException;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SpotifyTrackLoader extends Loader {
    private final Pattern PLAYLIST_PATTERN = Pattern.compile("^(?:https?://(?:open\\.)?spotify\\.com|spotify)([/:])track\\1([a-zA-Z0-9]+)");

    public SpotifyTrackLoader(YoutubeAudioSourceManager youtubeAudioSourceManager) {
        super(youtubeAudioSourceManager);
    }


    @Override
    public Pattern pattern() {
        return PLAYLIST_PATTERN;
    }

    @Nullable
    @Override
    public AudioItem load(DefaultAudioPlayerManager manager, SpotifyApi spotifyApi, Matcher matcher) {
        var albumId = matcher.group(2);

        Track execute;
        try {
            execute = spotifyApi.getTrack(albumId).build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException exception) {
            throw new ChuuServiceException(exception);
        }
        String song = execute.getName();
        String artist = execute.getArtists()[0].getName();
        return doYoutubeSearch(manager, "ytsearch:" + song + " " + artist);
    }

}
