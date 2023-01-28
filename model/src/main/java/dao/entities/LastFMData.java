package dao.entities;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.TimeZone;

public class LastFMData {

    private final boolean privateUpdate;
    private final boolean imageNotify;
    private final WhoKnowsDisplayMode whoKnowsDisplayMode;
    private final ChartMode ChartMode;
    private final RemainingImagesMode remainingImagesMode;
    private final int defaultX;
    private final int defaultY;
    private final PrivacyMode privacyMode;
    private final boolean ratingNotify;
    private final boolean privateLastfmId;
    private final boolean showBotted;
    private final TimeZone timeZone;
    private final String token;
    private final boolean scrobbling;
    private final EmbedColor embedColor;
    private final boolean ownTags;
    private final int artistThreshold;
    private final EnumSet<ChartOptions> chartOptions;
    private final EnumSet<WKMode> wkModes;
    private Long discordId;
    private String name;
    private long guildID;
    private Role role;
    private String session;

    public LastFMData(String name, Long discordId, long guildID, boolean privateUpdate, boolean imageNotify, WhoKnowsDisplayMode whoKnowsDisplayMode, dao.entities.ChartMode chartMode, RemainingImagesMode remainingImagesMode, int defaultX, int defaultY, PrivacyMode privacyMode, boolean ratingNotify, boolean privateLastfmId, boolean showBotted, TimeZone timeZone, String token, String session, boolean scrobbling, EmbedColor embedColor, boolean ownTags, int artistThreshold, EnumSet<ChartOptions> chartOptions, EnumSet<WKMode> wkModes) {
        this.discordId = discordId;
        this.name = name;
        this.guildID = guildID;
        this.privateUpdate = privateUpdate;
        this.imageNotify = imageNotify;
        this.whoKnowsDisplayMode = whoKnowsDisplayMode;
        ChartMode = chartMode;
        this.remainingImagesMode = remainingImagesMode;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.privacyMode = privacyMode;
        this.ratingNotify = ratingNotify;
        this.privateLastfmId = privateLastfmId;
        this.showBotted = showBotted;
        this.timeZone = timeZone;
        this.token = token;
        this.session = session;
        this.scrobbling = scrobbling;
        this.embedColor = embedColor;
        this.ownTags = ownTags;
        this.artistThreshold = artistThreshold;
        this.chartOptions = chartOptions;
        this.wkModes = wkModes;
    }

    public LastFMData(String lastFmID, long resDiscordID, Role role, boolean privateUpdate, boolean notifyImage, WhoKnowsDisplayMode whoKnowsDisplayMode, dao.entities.ChartMode chartMode, RemainingImagesMode remainingImagesMode, int defaultX, int defaultY, PrivacyMode privacyMode, boolean ratingNotify, boolean privateLastfmId, boolean showBotted, TimeZone timeZone, String token, String session, boolean scrobbling, EmbedColor embedColor, boolean ownTags, int artistThreshold, EnumSet<ChartOptions> chartOptions, EnumSet<WKMode> wkModes) {
        this.name = lastFmID;
        this.discordId = resDiscordID;
        this.role = role;
        this.privateUpdate = privateUpdate;
        this.imageNotify = notifyImage;
        this.whoKnowsDisplayMode = whoKnowsDisplayMode;
        ChartMode = chartMode;
        this.remainingImagesMode = remainingImagesMode;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.privacyMode = privacyMode;
        this.ratingNotify = ratingNotify;
        this.privateLastfmId = privateLastfmId;
        this.showBotted = showBotted;
        this.timeZone = timeZone;
        this.token = token;
        this.session = session;
        this.scrobbling = scrobbling;
        this.embedColor = embedColor;
        this.ownTags = ownTags;
        this.artistThreshold = artistThreshold;
        this.chartOptions = chartOptions;
        this.wkModes = wkModes;
    }

    public static LastFMData ofUserWrapper(UsersWrapper usersWrapper) {
        return new LastFMData(usersWrapper.getLastFMName(), usersWrapper.getDiscordID(), usersWrapper.getRole(), false, false, WhoKnowsDisplayMode.IMAGE, dao.entities.ChartMode.IMAGE, RemainingImagesMode.IMAGE, 5, 5, PrivacyMode.NORMAL, false, false, true, usersWrapper.getTimeZone(), null, null, true, EmbedColor.defaultColor(), false, 0, ChartOptions.defaultMode(), EnumSet.noneOf(WKMode.class));

    }

    public static LastFMData ofDefault() {
        return new LastFMData("chuubot", -1L, Role.USER, false, false, WhoKnowsDisplayMode.IMAGE, dao.entities.ChartMode.IMAGE, RemainingImagesMode.IMAGE, 5, 5, PrivacyMode.NORMAL, false, false, true, TimeZone.getDefault(), null, null, true, EmbedColor.defaultColor(), false, 0, ChartOptions.defaultMode(), EnumSet.noneOf(WKMode.class));

    }

    public static LastFMData ofUser(@NotNull String user) {
        return ofUser(user, -1);
    }

    public static LastFMData ofUser(@NotNull String user, long id) {
        return new LastFMData(user, id, Role.USER, false, false, WhoKnowsDisplayMode.IMAGE, dao.entities.ChartMode.IMAGE, RemainingImagesMode.IMAGE, 5, 5, PrivacyMode.NORMAL, false, false, true, TimeZone.getDefault(), null, null, true, EmbedColor.defaultColor(), false, 0, ChartOptions.defaultMode(), EnumSet.noneOf(WKMode.class));
    }

    public EmbedColor getEmbedColor() {
        return embedColor;
    }


    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public long getDiscordId() {
        return discordId;
    }


    public void setDiscordId(Long discordId) {
        this.discordId = discordId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getGuildID() {
        return guildID;
    }

    public void setGuildID(long guildID) {
        this.guildID = guildID;
    }

    public boolean isPrivateUpdate() {
        return privateUpdate;
    }

    public boolean isImageNotify() {
        return imageNotify;
    }

    public WhoKnowsDisplayMode getWhoKnowsMode() {
        return whoKnowsDisplayMode;
    }

    public dao.entities.ChartMode getChartMode() {
        return ChartMode;
    }

    public RemainingImagesMode getRemainingImagesMode() {
        return remainingImagesMode;
    }

    public int getDefaultX() {
        return defaultX;
    }

    public int getDefaultY() {
        return defaultY;
    }

    public PrivacyMode getPrivacyMode() {
        return privacyMode;
    }

    public boolean isRatingNotify() {
        return ratingNotify;
    }

    public boolean isPrivateLastfmId() {
        return privateLastfmId;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public boolean isShowBotted() {
        return showBotted;
    }

    public String getToken() {
        return token;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public boolean isScrobbling() {
        return scrobbling;
    }

    public boolean useOwnTags() {
        return ownTags;
    }

    public int getArtistThreshold() {
        return artistThreshold;
    }

    public EnumSet<ChartOptions> getChartOptions() {
        return chartOptions;
    }

    public EnumSet<WKMode> getWkModes() {
        return wkModes;
    }
}
