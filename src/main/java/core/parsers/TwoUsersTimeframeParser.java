package core.parsers;

import core.commands.Context;
import core.commands.InteracionReceived;
import core.parsers.explanation.TimeframeExplanation;
import core.parsers.explanation.TwoUsersExplanation;
import core.parsers.explanation.util.Explanation;
import core.parsers.interactions.InteractionAux;
import core.parsers.params.TwoUsersTimeframeParamaters;
import core.parsers.utils.OptionalEntity;
import dao.ChuuService;
import dao.entities.LastFMData;
import dao.entities.TimeFrameEnum;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;

import java.util.List;

public class TwoUsersTimeframeParser extends DaoParser<TwoUsersTimeframeParamaters> {
    public TwoUsersTimeframeParser(ChuuService dao) {
        super(dao);
    }

    public TwoUsersTimeframeParser(ChuuService dao, OptionalEntity... opts) {
        super(dao);
        addOptional(opts);
    }

    @Override
    public TwoUsersTimeframeParamaters parseSlashLogic(InteracionReceived<? extends CommandInteraction> ctx) throws InstanceNotFoundException {

        CommandInteraction e = ctx.e();
        User oneUser = InteractionAux.parseUser(e);
        TimeFrameEnum timeFrameEnum = InteractionAux.parseTimeFrame(e, TimeFrameEnum.ALL);


        return new TwoUsersTimeframeParamaters(ctx, findLastfmFromID(ctx.getAuthor(), ctx), findLastfmFromID(oneUser, ctx), timeFrameEnum);
    }

    public TwoUsersTimeframeParamaters parseLogic(Context e, String[] words) throws InstanceNotFoundException {
        String[] message = getSubMessage(e);

        if (message.length == 0) {
            sendError(getErrorMessage(5), e);
            return null;
        }
        ChartParserAux chartParserAux = new ChartParserAux(words);
        TimeFrameEnum timeFrameEnum = chartParserAux.parseTimeframe(TimeFrameEnum.ALL);
        words = chartParserAux.getMessage();

        ParserAux parserAux = new ParserAux(words, isExpensiveSearch());
        LastFMData[] datas = parserAux.getTwoUsers(dao, words, e);
        // words = parserAux.getMessage();
        if (datas == null) {
            sendError("Couldn't get two users", e);
            return null;
        }
        if (!e.isFromGuild() && (datas[0].getDiscordId() != datas[1].getDiscordId())) {
            sendError("Can't get two different users on DM's", e);
            return null;
        }
        return new TwoUsersTimeframeParamaters(e, datas[0], datas[1], timeFrameEnum);
    }

    @Override
    public List<Explanation> getUsages() {
        return List.of(new TwoUsersExplanation(), new TimeframeExplanation(TimeFrameEnum.ALL));
    }


    @Override
    public void setUpErrorMessages() {
        super.setUpErrorMessages();
        errorMessages.put(5, "Need at least one username");
        errorMessages.put(-1, "Mentioned user is not registered");


    }
}
