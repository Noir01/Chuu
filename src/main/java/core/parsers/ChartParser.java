package core.parsers;

import core.parsers.exceptions.InvalidChartValuesException;
import core.parsers.exceptions.InvalidDateException;
import core.parsers.params.ChartParameters;
import core.parsers.utils.CustomTimeFrame;
import dao.ChuuService;
import dao.entities.LastFMData;
import dao.entities.TimeFrameEnum;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;

public class ChartParser extends ChartableParser<ChartParameters> {


    public ChartParser(ChuuService dao) {
        super(dao, TimeFrameEnum.WEEK);
    }

    @Override
    public ChartParameters parseLogic(MessageReceivedEvent e, String[] subMessage) throws InstanceNotFoundException {
        int x = 5;
        int y = 5;


        ParserAux parserAux = new ParserAux(subMessage);
        User oneUser = parserAux.getOneUser(e, dao);
        subMessage = parserAux.getMessage();
        ChartParserAux chartParserAux = new ChartParserAux(subMessage);
        try {
            Point chartSize = chartParserAux.getChartSize();
            if (chartSize != null) {
                x = chartSize.x;
                y = chartSize.y;

            }
        } catch (InvalidChartValuesException ex) {
            this.sendError(getErrorMessage(6), e);
            return null;
        }
        CustomTimeFrame timeFrame;
        try {
            timeFrame = chartParserAux.parseCustomTimeFrame(defaultTFE);
        } catch (InvalidDateException invalidDateException) {
            this.sendError(invalidDateException.getErrorMessage(), e);
            return null;
        }
        subMessage = chartParserAux.getMessage();
        LastFMData data;
        if (!oneUser.equals(e.getAuthor())) {
            data = findLastfmFromID(oneUser, e);
        } else {
            data = atTheEndOneUser(e, subMessage);
        }
        return new ChartParameters(e, data, data.getDiscordId(), data.getChartMode(), data, timeFrame, x, y);
    }

}
