package core.commands.config;

import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.utils.CommandCategory;
import core.parsers.EmojeParser;
import core.parsers.Parser;
import core.parsers.params.EmotiParameters;
import core.parsers.utils.OptionalEntity;
import core.util.ServiceView;
import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static core.commands.utils.ReactValidation.validateEmotes;

public class NpReactionsCommand extends ConcurrentCommand<EmotiParameters> {
    public NpReactionsCommand(ServiceView dao) {
        super(dao);
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public Parser<EmotiParameters> initParser() {
        EmojeParser emojeParser = new EmojeParser();
        emojeParser.addOptional(new OptionalEntity("check", "check the current reactions"));
        emojeParser.addOptional(new OptionalEntity("append", "add emotes to the currently existing list"));
        return emojeParser;
    }

    @Override
    public String getDescription() {
        return "Sets reactions for your nps";
    }

    @Override
    public List<String> getAliases() {
        return List.of("reactions", "reacts", "react");
    }

    @Override
    public String getName() {
        return "Np reactions";
    }

    @Override
    public void onCommand(Context e, @Nonnull EmotiParameters params) {
        boolean append = params.hasOptional("append");

        if (params.hasOptional("check")) {
            List<String> serverReactions = db.getUserReacts(e.getAuthor().getIdLong());
            if (serverReactions.isEmpty()) {
                sendMessageQueue(e, "Don't have any reaction set");
                return;
            }
            List<String> displaying = serverReactions.stream().map(EmotiParameters.Emotable::toDisplay).toList();
            sendMessageQueue(e, "Have these reactions: " + String.join(" ", displaying));
            return;

        }

        if (params.getEmotis().isEmpty()) {
            sendMessageQueue(e, "Clearing your reactions");
            db.clearUserReacts(e.getAuthor().getIdLong());
            return;
        }

        AtomicLong messageId = new AtomicLong();
        if (e.isFromGuild() && !e.getGuild().getSelfMember().hasPermission(Permission.MESSAGE_ADD_REACTION)) {
            sendMessageQueue(e, "Don't have permissions to add reactions in this server!");
            return;
        }

        if (params.hasEmotes()) {
            List<String> content = validateEmotes(e, params);
            if (content.isEmpty()) {
                sendMessageQueue(e, "Didn't add any reaction.");
            } else {
                List<String> toAdd;
                if (append) {
                    toAdd = db.getUserReacts(e.getAuthor().getIdLong());

                    toAdd.addAll(content);
                    toAdd = new ArrayList<>(new LinkedHashSet<>(toAdd));
                } else {
                    toAdd = content;
                }
                db.insertUserReactions(e.getAuthor().getIdLong(), toAdd);
                sendMessageQueue(e, "Will %s the following reactions: %s".formatted(
                        (append ? "add" : "set"),
                        content.stream().map(EmotiParameters.Emotable::toDisplay).collect(Collectors.joining(" "))));
            }
        } else {
            if (params.hasEmojis()) {

                List<String> toAdd;
                if (append) {
                    toAdd = db.getUserReacts(e.getAuthor().getIdLong());
                    toAdd.addAll(params.getEmojis());
                    toAdd = new ArrayList<>(new LinkedHashSet<>(toAdd));

                } else {
                    toAdd = params.getEmojis();
                }

                db.insertUserReactions(e.getAuthor().getIdLong(), toAdd);
                String emojiLine = params.getEmojis().stream().map(EmotiParameters.Emotable::toDisplay).collect(Collectors.joining(" "));
                sendMessageQueue(e, "Will %s the following reactions: %s".formatted(
                        (append ? "add" : "set")
                        , String.join(" ", emojiLine)));
            }
        }
    }
}
