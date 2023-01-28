package core.parsers;

import core.commands.Context;
import core.commands.InteracionReceived;
import core.parsers.explanation.PermissiveUserExplanation;
import core.parsers.explanation.util.Explanation;
import core.parsers.explanation.util.ExplanationLine;
import core.parsers.explanation.util.ExplanationLineType;
import core.parsers.interactions.InteractionAux;
import core.parsers.params.AffinityParameters;
import dao.ChuuService;
import dao.entities.LastFMData;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class AffinityParser extends DaoParser<AffinityParameters> {

    public AffinityParser(ChuuService dao) {
        super(dao);

    }

    @Override
    public void setUpErrorMessages() {
        super.setUpErrorMessages();
    }


    @Override
    public AffinityParameters parseSlashLogic(InteracionReceived<? extends CommandInteraction> ctx) throws InstanceNotFoundException {
        CommandInteraction e = ctx.e();
        OptionMapping option = e.getOption("affinity-threshold");

        Long threshold = null;
        if (option != null)
            threshold = option.getAsLong();
        User user = InteractionAux.parseUser(e);
        boolean doServer = user == e.getUser();
        if (!doServer) {
            LastFMData first = findLastfmFromID(e.getUser(), ctx);
            LastFMData second = findLastfmFromID(user, ctx);
            return new AffinityParameters(ctx, false, first, second, first.getDiscordId(), second.getDiscordId(), threshold);
        } else {
            return new AffinityParameters(ctx, true, null, null, null, null, threshold);
        }
    }

    @Override
    public AffinityParameters parseLogic(Context e, String[] words) throws InstanceNotFoundException {
        Stream<String> secondStream = Arrays.stream(words).filter(s -> s.matches("\\d+"));
        Optional<String> opt2 = secondStream.findAny();
        Long threshold = null;

        if (opt2.isPresent()) {
            threshold = Long.valueOf(opt2.get());
            if (threshold < 1) {
                sendError("The threshold must be 1 or bigger", e);
                return null;
            }
            words = Arrays.stream(words).filter(s -> !s.matches("\\d+")).toArray(String[]::new);
        }

        ParserAux parserAux = new ParserAux(words);
        LastFMData[] datas = parserAux.getTwoUsers(dao, words, e);

        boolean doServer = false;
        if (datas == null) {
            doServer = true;
        } else {
            if (datas[0].getDiscordId() == (datas[1].getDiscordId())) {
                e.sendMessage("Don't use the same person twice\n").queue();
                return null;
            }
        }
        if (doServer) {
            return new AffinityParameters(e, true, null, null, null, null, threshold);
        } else {
            return new AffinityParameters(e, false, datas[0], datas[1], datas[0].getDiscordId(), datas[1].getDiscordId(), threshold);
        }
    }

    @Override
    public List<Explanation> getUsages() {
        return List.of(
                new PermissiveUserExplanation() {
                    @Override
                    public ExplanationLine explanation() {
                        return new ExplanationLine(super.explanation().header(),
                                "If an user is not specified it will display your affinity with all users from this server, otherwise your affinity with that user\n Alternatively you could also mention two different users",
                                super.explanation().options());
                    }
                }, () -> new ExplanationLineType("affinity-threshold", "\t If a threshold is set it means that the artists below that threshold will be discarded for the comparison", OptionType.INTEGER)
        );
    }


}
