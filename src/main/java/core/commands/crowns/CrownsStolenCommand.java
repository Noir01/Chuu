package core.commands.crowns;

import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.commands.utils.CommandUtil;
import core.otherlisteners.util.PaginatorBuilder;
import core.parsers.Parser;
import core.parsers.TwoUsersParser;
import core.parsers.params.NumberParameters;
import core.parsers.params.TwoUsersParamaters;
import core.util.ServiceView;
import dao.entities.DiscordUserDisplay;
import dao.entities.StolenCrownWrapper;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static core.parsers.NumberParser.generateThresholdParser;

public class CrownsStolenCommand extends ConcurrentCommand<NumberParameters<TwoUsersParamaters>> {
    public CrownsStolenCommand(ServiceView dao) {
        super(dao, true);
        this.respondInPrivate = false;
    }


    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.CROWNS;
    }

    @Override
    public Parser<NumberParameters<TwoUsersParamaters>> initParser() {
        return generateThresholdParser(new TwoUsersParser(db));

    }

    @Override
    public String slashName() {
        return "stolen";
    }

    @Override
    public String getDescription() {
        return ("List of crowns you would have if the other would concedes their crowns");
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("stolen");
    }

    @Override
    public String getName() {
        return "List of stolen crowns";
    }

    @Override
    public void onCommand(Context e, @NotNull NumberParameters<TwoUsersParamaters> params) {


        TwoUsersParamaters innerParams = params.getInnerParams();
        long ogDiscordID = innerParams.getFirstUser().getDiscordId();
        String ogLastFmId = innerParams.getFirstUser().getName();
        long secondDiscordId = innerParams.getSecondUser().getDiscordId();
        String secondlastFmId = innerParams.getSecondUser().getName();

        if (ogLastFmId.equals(secondlastFmId) || ogDiscordID == secondDiscordId) {
            sendMessageQueue(e, "Sis, dont use the same person twice");
            return;
        }

        Long threshold = params.getExtraParam();
        long idLong = innerParams.getE().getGuild().getIdLong();

        if (threshold == null) {
            threshold = (long) db.getGuildCrownThreshold(idLong);
        }
        StolenCrownWrapper resultWrapper = db
                .getCrownsStolenBy(ogLastFmId, secondlastFmId, e.getGuild().getIdLong(), Math.toIntExact(threshold));

        int rows = resultWrapper.list().size();

        DiscordUserDisplay userInformation = CommandUtil.getUserInfoEscaped(e, ogDiscordID);
        String userName = userInformation.username();

        DiscordUserDisplay userInformation2 = CommandUtil.getUserInfoEscaped(e, secondDiscordId);
        String userName2 = userInformation2.username();
        String userUrl2 = userInformation2.urlImage();
        if (rows == 0) {
            sendMessageQueue(e, userName2 + " hasn't stolen anything from " + userName);
            return;
        }
        EmbedBuilder embedBuilder = new ChuuEmbedBuilder(e).setThumbnail(e.getGuild().getIconUrl());


        // Footer doesnt allow markdown characters
        embedBuilder.setTitle(userName + "'s stolen crowns by " + userName2, CommandUtil
                        .getLastFmUser(ogLastFmId))
                .setThumbnail(userUrl2)
                .setFooter(CommandUtil.unescapedUser(userName2, resultWrapper.quriedId(), e) + " has stolen " + rows + " crowns!\n", null);

        new PaginatorBuilder<>(e, embedBuilder, resultWrapper.list()).build().queue();

    }


}
