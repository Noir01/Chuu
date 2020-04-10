package core.parsers;

import core.exceptions.InstanceNotFoundException;
import dao.ChuuService;
import dao.entities.LastFMData;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

public class AffinityParser extends DaoParser {

    public AffinityParser(ChuuService dao) {
        super(dao);

    }

    @Override
    public void setUpErrorMessages() {
        super.setUpErrorMessages();
    }

    @Override
    public String[] parseLogic(MessageReceivedEvent e, String[] words) throws InstanceNotFoundException {
        Stream<String> secondStream = Arrays.stream(words).filter(s -> s.matches("\\d+"));
        Optional<String> opt2 = secondStream.findAny();
        Integer threshold = null;

        if (opt2.isPresent()) {
            threshold = Integer.valueOf(opt2.get());
            if (threshold < 1) {
                sendError("The threshold must be 1 or bigger", e);
                return new String[]{};
            }
            words = Arrays.stream(words).filter(s -> !s.matches("\\d+")).toArray(String[]::new);
        }

        ParserAux parserAux = new ParserAux(words);
        LastFMData[] datas = parserAux.getTwoUsers(dao, words, e);

        boolean doServer = false;
        if (datas == null) {
            doServer = true;
        } else {
            if (datas[0].getDiscordId().equals(datas[1].getDiscordId())) {
                e.getChannel().sendMessage("Dont't use the same person twice\n").queue();
                return new String[]{};
            }
        }
        String s = threshold == null ? null : String.valueOf(threshold);
        if (doServer) {
            return new String[]{
                    null, null, null, null, "true", s};
        } else {
            return new String[]{
                    String.valueOf(datas[0].getDiscordId()), datas[0].getName(), String.valueOf(datas[1].getDiscordId()), datas[1].getName(), "false", s};
        }

    }


    @Override
    public String getUsageLogic(String commandName) {
        return "**" + commandName + " *user1* *threshold***\n" +
               "\t If user is not specified if will display your affinity with all users from this server, otherwise your affinity with that user" +
               "\t Alternatively you could also mention two different users" +
               "\t If a threshold is set it means that the artists below that threshold will be discarded for the comparison\n\n";

    }
}
