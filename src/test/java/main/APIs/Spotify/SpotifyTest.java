package main.APIs.Spotify;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SpotifyTest {
	private static Spotify spotifyApi;

	@BeforeClass
	public static void init() {
		Properties properties = new Properties();
		try (InputStream in = SpotifyTest.class.getResourceAsStream("/" + "all.properties")) {
			properties.load(in);
		} catch (IOException e) {
			e.printStackTrace();
		}
		new SpotifySingleton(properties.getProperty("client_ID"), properties.getProperty("client_Secret"));
		spotifyApi = SpotifySingleton.getInstanceUsingDoubleLocking();
	}

	@Test
	public void search() {
		spotifyApi.search("asda", 0);
	}
}