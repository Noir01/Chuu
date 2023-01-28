package core.commands.moderation;

import core.Chuu;
import core.apis.lyrics.TextSplitter;
import core.commands.Context;
import core.commands.abstracts.ConcurrentCommand;
import core.commands.abstracts.MyCommand;
import core.commands.utils.ChuuEmbedBuilder;
import core.commands.utils.CommandCategory;
import core.otherlisteners.util.PaginatorBuilder;
import core.parsers.DisabledCommandParser;
import core.parsers.Parser;
import core.parsers.params.DisabledCommandParameters;
import core.services.MessageDisablingService;
import core.util.ServiceView;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DisabledCommand extends ConcurrentCommand<DisabledCommandParameters> {
    public DisabledCommand(ServiceView dao) {
        super(dao);
        respondInPrivate = false;
    }

    @Override
    protected CommandCategory initCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public Parser<DisabledCommandParameters> initParser() {
        return new DisabledCommandParser();
    }

    @Override
    public String getDescription() {
        return "Disable and re enable all the commands of the bot in a specific channel or in the whole server";
    }

    @Override
    public String slashName() {
        return "command-status";
    }

    @Override
    public List<String> getAliases() {
        return List.of("disable", "enable", "toggle");
    }

    @Override
    public String getName() {
        return "Disable";
    }

    @Override
    public void onCommand(Context e, @NotNull DisabledCommandParameters params) {

        MessageDisablingService messageDisablingService = Chuu.getMessageDisablingService();


        List<? extends MyCommand<?>> commandsToAllow;
        if (params.hasOptional("all")) {
            commandsToAllow = e.getJDA().getRegisteredListeners().stream().filter(x -> x instanceof MyCommand<?> && !(x instanceof DisabledCommand)).map(x -> (MyCommand<?>) x).collect(Collectors.toList());
        } else if (params.hasOptional("category")) {
            commandsToAllow = e.getJDA().getRegisteredListeners().stream().filter(x -> x instanceof MyCommand<?> && !(x instanceof DisabledCommand)).map(x -> (MyCommand<?>) x).
                    filter(x -> x.getCategory().equals(params.getCommand().getCategory())).collect(Collectors.toList());
        } else {
            commandsToAllow = new ArrayList<>(Collections.singletonList(params.getCommand()));
        }
        // Wont accept this command
        // TODO x is null
        commandsToAllow.removeIf(x -> x.getName().equals(this.getName()));

        boolean enable = params.getAction() == DisabledCommandParameters.Action.ENABLE;
        boolean toggl = params.getAction() == DisabledCommandParameters.Action.TOGGLE;
        Predicate<Boolean> transformation = (b) -> {
            if (toggl) {
                return !b;
            } else
                return enable;
        };


        Map<Boolean, List<MyCommand<?>>> allowedCommands = commandsToAllow.stream().collect(Collectors.partitioningBy(x -> transformation.test(messageDisablingService.isMessageAllowed(x, e))));
        List<MyCommand<?>> previouslyAllowedCommands = allowedCommands.get(true);
        for (MyCommand<?> command : commandsToAllow) {
            boolean messageAllowed = previouslyAllowedCommands.contains(command);
            if (params.isExceptThis()) {
                messageDisablingService.toggleCommandDisabledness(command, params.getGuildId(), messageAllowed, db);
                messageDisablingService.toggleCommandChannelDisabledness(command, params.getGuildId(), params.getChannelId(), !messageAllowed, db);
            } else if (params.isOnlyChannel()) {
                messageDisablingService.toggleCommandChannelDisabledness(command, params.getGuildId(), params.getChannelId(), messageAllowed, db);
            } else {
                messageDisablingService.toggleCommandDisabledness(command, params.getGuildId(), messageAllowed, db);
            }
        }
        char prefix = e.getPrefix();
        String allowedStr = allowedCommands.entrySet().stream()
                .map(x -> {
                    String commands = x.getValue().stream()
                            .map(y -> prefix + y.getAliases().get(0))
                            .collect(Collectors.joining(", "));
                    if (commands.isBlank()) return "";
                    else
                        return commands + (x.getValue().size() > 1 ? " are now " : " is now ")
                                + (x.getKey() ? "enabled." : "disabled.") + "\n";
                }).collect(Collectors.joining(""));
        List<String> pages = TextSplitter.split(allowedStr, 2000, ", ");


        EmbedBuilder embedBuilder = new ChuuEmbedBuilder(e);
        new PaginatorBuilder<>(e, embedBuilder, pages).pageSize(1).unnumered().withIndicator().build().queue();

    }
}
