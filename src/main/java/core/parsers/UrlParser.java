package core.parsers;

import core.commands.Context;
import core.commands.ContextMessageReceived;
import core.commands.InteracionReceived;
import core.commands.utils.CommandUtil;
import core.parsers.explanation.UrlExplanation;
import core.parsers.explanation.util.Explanation;
import core.parsers.params.UrlParameters;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class UrlParser extends Parser<UrlParameters> {
    private final boolean permCheck;

    public UrlParser() {
        this(true);
    }

    public UrlParser(boolean permCheck) {
        this.permCheck = permCheck;
    }

    static boolean isValidURL(String urlString) {
        try {
            URL url = new URL(urlString);
            url.toURI();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public UrlParameters parseSlashLogic(InteracionReceived<? extends CommandInteraction> ctx) {
        CommandInteraction e = ctx.e();

        if (permCheck && (CommandUtil.notEnoughPerms(ctx))) {
            sendError(CommandUtil.notEnoughPermsTemplate() + "submit links", ctx);
            return null;
        }

        String url = Optional.ofNullable(e.getOption(UrlExplanation.NAME)).map(OptionMapping::getAsString).orElse("");
        if (!isValidURL(url)) {
            sendError(getErrorMessage(1), ctx);
            return null;
        }
        return new UrlParameters(ctx, url);
    }

    @Override
    public void setUpErrorMessages() {
        errorMessages.put(1, "Invalid url ");
        errorMessages.put(2, "Insufficient Permissions, only a mod can");

    }

    public UrlParameters parseLogic(Context e, String[] subMessage) {
        if (permCheck && (e.getMember() == null || !e.getMember().hasPermission(Permission.MESSAGE_MANAGE))) {
            sendError(getErrorMessage(2), e);
            return null;
        }
        String url;

        if ((subMessage.length == 0) && e instanceof ContextMessageReceived mes) {
            if (mes.e().getMessage().getAttachments().isEmpty()) {
                return new UrlParameters(e, "");
            } else {
                url = mes.e().getMessage().getAttachments().get(0).getUrl();
            }
        } else if (subMessage.length == 1) {
            url = subMessage[0];
            if (!isValidURL(url)) {
                sendError(getErrorMessage(1), e);
                return null;
            }

        } else {
            sendError("You need to give only a url or an attachment", e);
            return null;
        }
        return new UrlParameters(e, url);


    }

    @Override
    public List<Explanation> getUsages() {
        return Collections.singletonList(new UrlExplanation());
    }

}
