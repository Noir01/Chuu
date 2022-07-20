package core.parsers;

import core.commands.Context;
import core.parsers.params.CommandParameters;
import core.parsers.utils.OptionalEntity;
import dao.ChuuService;
import dao.entities.*;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.entities.User;

import java.util.EnumSet;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Function;

public abstract class DaoParser<T extends CommandParameters> extends Parser<T> {
    private static final QuadFunction<Context, ChartMode, WhoKnowsDisplayMode, RemainingImagesMode, LastFMData> DEFAULT_DATA = (e, c, w, r) ->
            new LastFMData(null, e.getAuthor().getIdLong(), e.isFromGuild() ? e.getGuild().getIdLong() : 693124899220226178L, false, false, w, c, r, ChartableParser.DEFAULT_X, ChartableParser.DEFAULT_Y, PrivacyMode.NORMAL, true, false, true, TimeZone.getDefault(), null, null, true, EmbedColor.defaultColor(), false, 0, ChartOptions.defaultMode(), EnumSet.of(WKMode.NORMAL));
    final ChuuService dao;
    private boolean expensiveSearch = false;
    private boolean allowUnaothorizedUsers = false;


    public DaoParser(ChuuService dao, OptionalEntity... opts) {
        super(opts);
        this.dao = dao;
    }


    LastFMData atTheEndOneUser(Context event, String[] message) throws InstanceNotFoundException {
        ParserAux aux = new ParserAux(message);
        User oneUserPermissive = aux.getOneUserPermissive(event, dao);
        return findLastfmFromID(oneUserPermissive, event);
    }

    protected LastFMData findLastfmFromID(User user, Context event) throws InstanceNotFoundException {
        try {
            if (event.isFromGuild() && expensiveSearch) {
                return this.dao.computeLastFmData(user.getIdLong(), event.getGuild().getIdLong());
            } else {
                return this.dao.findLastFMData(user.getIdLong());
            }
        } catch (InstanceNotFoundException exception) {
            if (allowUnaothorizedUsers) {
                WhoKnowsDisplayMode whoKnowsDisplayMode = WhoKnowsDisplayMode.IMAGE;
                ChartMode chartMode = ChartMode.IMAGE;
                RemainingImagesMode remainingImagesMode = RemainingImagesMode.IMAGE;

                if (event.isFromGuild()) {
                    GuildProperties guildProperties = this.dao.getGuildProperties(event.getGuild().getIdLong());
                    whoKnowsDisplayMode = guildProperties.whoKnowsDisplayMode() != null ? guildProperties.whoKnowsDisplayMode() : whoKnowsDisplayMode;
                    chartMode = guildProperties.chartMode() != null ? guildProperties.chartMode() : chartMode;
                    remainingImagesMode = guildProperties.remainingImagesMode() != null ? guildProperties.remainingImagesMode() : remainingImagesMode;
                }
                return DEFAULT_DATA.apply(event, chartMode, whoKnowsDisplayMode, remainingImagesMode);
            }
            throw exception;
        }
    }

    Function<User, Optional<LastFMData>> wrapperFind(Context ctx) {
        return (x) -> {
            try {
                return Optional.of(findLastfmFromID(x, ctx));
            } catch (InstanceNotFoundException instanceNotFoundException) {
                return Optional.empty();
            }
        };
    }

    @Override
    protected void setUpErrorMessages() {
        errorMessages.put(1, "User not on database");
        errorMessages.put(2, "Internal Server Error, try again later");
        errorMessages.put(3, "User hasn't played anything recently");
        errorMessages.put(4, "User does not exist on last.fm");
    }

    public boolean isExpensiveSearch() {
        return expensiveSearch;
    }

    public void setExpensiveSearch(boolean expensiveSearch) {
        this.expensiveSearch = expensiveSearch;
    }

    public boolean isAllowUnaothorizedUsers() {
        return allowUnaothorizedUsers;
    }

    public void setAllowUnaothorizedUsers(boolean allowUnaothorizedUsers) {
        this.allowUnaothorizedUsers = allowUnaothorizedUsers;
    }
}
