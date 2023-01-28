package core.commands.stats;

import core.Chuu;
import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.imagerenderer.TasteRenderer;
import core.otherlisteners.util.PaginatorBuilder;
import core.parsers.params.CommandParameters;
import core.services.UserInfoService;
import core.util.ServiceView;
import dao.entities.*;
import dao.exceptions.InstanceNotFoundException;
import dao.utils.LinkUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

public abstract class BaseTasteCommand<T extends CommandParameters> extends ConcurrentCommand<T> {
    boolean thumbnailPerRow = false;


    public BaseTasteCommand(ServiceView dao) {
        super(dao);
        respondInPrivate = true;
    }

    private static Optional<Color> getColor(Context e, long discordId) {
        return Optional.ofNullable(e.getGuild()).map(g -> g.getMemberById(discordId)).flatMap(t -> t.getRoles().stream().filter(z -> z.getColor() != null).findFirst()).map(Role::getColor);
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.USER_STATS;
    }

    public abstract String getEntity(T params);

    public abstract @Nullable
    String hasCustomUrl(T params);

    @Override
    public void onCommand(Context e, @NotNull T params) throws LastFmException, InstanceNotFoundException {
        Pair<LastFMData, LastFMData> userDatas = getUserDatas(e, params);
        if (userDatas == null) {
            return;
        }
        ResultWrapper<UserArtistComparison> result = getResult(userDatas.getKey(), userDatas.getRight(), params);
        handleResult(e, result, userDatas.getKey(), userDatas.getValue(), params);
    }

    public abstract ResultWrapper<UserArtistComparison> getResult(LastFMData og, LastFMData second, T params) throws LastFmException;

    public abstract Pair<Integer, Integer> getTasteBar(ResultWrapper<UserArtistComparison> resultWrapper, UserInfo og, UserInfo second, T params);

    public abstract @Nullable
    Pair<LastFMData, LastFMData> getUserDatas(Context e, T params) throws InstanceNotFoundException;

    public void handleResult(Context e, ResultWrapper<UserArtistComparison> resultWrapper, LastFMData og, LastFMData second, T params) {
        if (resultWrapper.getRows() == 0) {
            sendMessageQueue(e, String.format("You don't share any %s :(", getEntity(params)));
            return;
        }
        switch (CommandUtil.getEffectiveMode(og.getRemainingImagesMode(), params)) {
            case PIE, IMAGE -> doImage(e, resultWrapper, og.getDiscordId(), second.getDiscordId(), params);
            case LIST -> doList(e, og.getDiscordId(), second.getDiscordId(), resultWrapper, params);
        }
    }

    private void doImage(Context e, ResultWrapper<UserArtistComparison> resultWrapper, long firstId, long secondId, T params) {
        String userA = resultWrapper.getResultList().get(0).getUserA();
        String userB = resultWrapper.getResultList().get(0).getUserB();
        UserInfoService userInfoService = new UserInfoService(db);
        UserInfo userInfo = userInfoService.getUserInfo(LastFMData.ofUser(userA));
        UserInfo userInfo1 = userInfoService.getUserInfo(LastFMData.ofUser(userB));
        if (Chuu.getLastFmId(userInfo.getUsername()).equals(Chuu.DEFAULT_LASTFM_ID)) {
            userInfo.setUsername(CommandUtil.getUserInfoUnescaped(e, firstId).username());
        }
        if (Chuu.getLastFmId(userInfo1.getUsername()).equals(Chuu.DEFAULT_LASTFM_ID)) {
            userInfo1.setUsername(CommandUtil.getUserInfoUnescaped(e, secondId).username());
        }
        Pair<Integer, Integer> tasteBar = getTasteBar(resultWrapper, userInfo, userInfo1, params);
        var palette = getColor(e, firstId).flatMap(t -> getColor(e, secondId).map(z -> Pair.of(t, z))).orElse(null);
        BufferedImage image = TasteRenderer.generateTasteImage(resultWrapper, List.of(userInfo, userInfo1), getEntity(params), hasCustomUrl(params), this.thumbnailPerRow, tasteBar, palette);
        sendImage(image, e);
    }

    private void doList(Context e, long ogDiscordID, long secondDiscordId, ResultWrapper<UserArtistComparison> resultWrapper, T params) {
        List<String> strings = resultWrapper.getResultList().stream().map(x -> String.format(". [%s](%s) - %d vs %d plays%n", x.getArtistID(), LinkUtils.getLastFmArtistUrl(x.getArtistID()), x.getCountA(), x.getCountB())).toList();

        DiscordUserDisplay uinfo = CommandUtil.getUserInfoEscaped(e, ogDiscordID);
        DiscordUserDisplay uinfo1 = CommandUtil.getUserInfoEscaped(e, secondDiscordId);
        EmbedBuilder embedBuilder = new ChuuEmbedBuilder(e).setTitle(String.format("%s vs %s", uinfo.username(), uinfo1.username())).setFooter(String.format("Both user have %d common %s", resultWrapper.getRows(), getEntity(params)), null).setThumbnail(uinfo1.urlImage());
        new PaginatorBuilder<>(e, embedBuilder, strings).build().queue();
    }

    @Override
    public String getName() {
        return "Taste";
    }

}
