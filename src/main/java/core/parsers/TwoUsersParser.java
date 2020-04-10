package core.parsers;

import core.exceptions.InstanceNotFoundException;
import dao.ChuuService;
import dao.entities.LastFMData;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;

public class TwoUsersParser extends DaoParser {
    public TwoUsersParser(ChuuService dao) {
        super(dao);
    }

    public TwoUsersParser(ChuuService dao, OptionalEntity... opts) {
        super(dao);
        this.opts.addAll(Arrays.asList(opts));
    }

    public String[] parseLogic(MessageReceivedEvent e, String[] words) throws InstanceNotFoundException {
        String[] message = getSubMessage(e.getMessage());
        if (!e.isFromGuild()) {
            sendError("Can't get two different users on DM's", e);
            return null;
        }
        if (message.length == 0) {
            sendError(getErrorMessage(5), e);
            return null;
        }
        ParserAux parserAux = new ParserAux(words);
        LastFMData[] datas = parserAux.getTwoUsers(dao, words, e);
        // words = parserAux.getMessage();
        if (datas == null) {
            sendError("Couldn't get two users", e);
            return null;
        }
        return new String[]{String.valueOf(datas[0].getDiscordId()), datas[0].getName(), String.valueOf(datas[1].getDiscordId()), datas[1].getName()};
    }


    @Override
    public String getUsageLogic(String commandName) {
        return "**" + commandName + " *userName* *userName***\n" +
               "\tIf the second user is missing it gets replaced by the owner of the message\n";

    }

    @Override
    public void setUpErrorMessages() {
        super.setUpErrorMessages();
        errorMessages.put(5, "Need at least one username");
        errorMessages.put(-1, "Mentioned user is not registered");


    }
}
