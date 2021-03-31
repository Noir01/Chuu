package core.parsers;

import core.parsers.explanation.util.Explanation;
import core.parsers.explanation.util.ExplanationLine;
import core.parsers.params.ChuuDataParams;
import dao.ChuuService;
import dao.entities.LastFMData;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;

public class UserModerationParser extends DaoParser<ChuuDataParams> {
    public UserModerationParser(ChuuService dao, OptionalEntity... opts) {
        super(dao, opts);
    }

    @Override
    protected ChuuDataParams parseLogic(MessageReceivedEvent e, String[] words) throws InstanceNotFoundException {
        if (words.length != 1) {
            sendError("Need a Discord Id or a lastfm name", e);
            return null;
        }
        if (ParserAux.discordId.matcher(words[0]).matches()) {
            long id = Long.parseLong(words[0]);
            LastFMData lastFMData = dao.findLastFMData(id);
            return new ChuuDataParams(e, lastFMData);
        } else {
            LastFMData byLastfmName = dao.findByLastfmName(words[0]);
            return new ChuuDataParams(e, byLastfmName);
        }
    }

    @Override
    public List<Explanation> getUsages() {
        return Collections.singletonList(() -> new ExplanationLine("discord-id|lastfm-id", null));
    }

}
