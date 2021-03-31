package core.parsers;

import core.parsers.explanation.NaturalTimeframeExplanation;
import core.parsers.explanation.PermissiveUserExplanation;
import core.parsers.explanation.util.Explanation;
import core.parsers.params.NaturalTimeParams;
import dao.ChuuService;
import dao.entities.LastFMData;
import dao.entities.NaturalTimeFrameEnum;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class NaturalTimeFrameParser extends DaoParser<NaturalTimeParams> {
    private final NaturalTimeFrameEnum defaultTFE;

    public NaturalTimeFrameParser(ChuuService dao, NaturalTimeFrameEnum defaultTFE) {
        super(dao);
        this.defaultTFE = defaultTFE;
    }

    public NaturalTimeParams parseLogic(MessageReceivedEvent e, String[] subMessage) throws InstanceNotFoundException {

        String[] message = getSubMessage(e.getMessage());
        NaturalTimeFrameEnum timeFrame = defaultTFE;

        ChartParserAux auxiliar = new ChartParserAux(message);
        timeFrame = auxiliar.parseNaturalTimeFrame(timeFrame);
        message = auxiliar.getMessage();
        LastFMData data = atTheEndOneUser(e, message);

        return new NaturalTimeParams(e, data, timeFrame);
    }

    @Override
    public List<Explanation> getUsages() {
        return List.of(new NaturalTimeframeExplanation(defaultTFE), new PermissiveUserExplanation());
    }

}
