package core.parsers;

import core.commands.Context;
import core.commands.InteracionReceived;
import core.commands.utils.CommandUtil;
import core.exceptions.LastFmException;
import core.parsers.exceptions.InvalidChartValuesException;
import core.parsers.explanation.DecadeExplanation;
import core.parsers.explanation.util.Explanation;
import core.parsers.interactions.InteractionAux;
import core.parsers.params.ChartYearRangeParameters;
import core.parsers.utils.CustomTimeFrame;
import core.parsers.utils.OptionalEntity;
import core.util.StringUtils;
import dao.ChuuService;
import dao.entities.LastFMData;
import dao.entities.TimeFrameEnum;
import dao.exceptions.InstanceNotFoundException;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ChartDecadeParser extends ChartableParser<ChartYearRangeParameters> {

    public ChartDecadeParser(ChuuService dao) {
        super(dao, TimeFrameEnum.ALL);
    }

    @Override
    protected void setUpOptionals() {
        super.setUpOptionals();
        addOptional(new OptionalEntity("nolimit", "make the chart as big as possible"), new OptionalEntity("time", "to sort by duration"));
    }

    @Override
    public ChartYearRangeParameters parseSlashLogic(InteracionReceived<? extends CommandInteraction> ctx) throws LastFmException, InstanceNotFoundException {
        CommandInteraction e = ctx.e();
        TimeFrameEnum tfe = InteractionAux.parseTimeFrame(e, defaultTFE);
        User user = InteractionAux.parseUser(e);
        OptionMapping decade = e.getOption(DecadeExplanation.NAME);
        Point size = InteractionAux.parseSize(e, () -> sendError(getErrorMessage(8), ctx));
        if (size == null) {
            return null;
        }

        int baseYear;
        int numberOfYears;
        if (decade != null) {
            String value = decade.getAsString();
            value = value.substring(0, value.length() - 1);
            baseYear = ChartParserAux.getYearFromDecade(Integer.parseInt(value));
            numberOfYears = 9;
        } else {
            OptionMapping start = e.getOption(DecadeExplanation.RANGE_START);

            baseYear = Optional.ofNullable(e.getOption(DecadeExplanation.RANGE_START))
                    .map(OptionMapping::getAsLong)
                    .map(Math::toIntExact)
                    .map(CommandUtil::getDecade)
                    .orElse(Year.now().getValue());
            OptionMapping end = e.getOption(DecadeExplanation.RANGE_END);


            if (end != null) {
                long endYear = e.getOption(DecadeExplanation.RANGE_END).getAsLong();
                if (baseYear > endYear) {
                    sendError("First year must be greater than the second", ctx);
                    return null;
                }
                numberOfYears = (int) (endYear - baseYear);
            } else {
                numberOfYears = 9;
            }
        }
        return new ChartYearRangeParameters(ctx, findLastfmFromID(user, ctx), CustomTimeFrame.ofTimeFrameEnum(tfe), size.x, size.y, Year.of(baseYear), numberOfYears);

    }

    @Override
    public ChartYearRangeParameters parseLogic(Context e, String[] subMessage) throws InstanceNotFoundException {
        TimeFrameEnum timeFrame = defaultTFE;
        LastFMData discordName;
        Year baseYear = Year.now().minus(Year.now().getValue() % 10, ChronoUnit.YEARS);
        int numberOfYears = 9;
        Pattern compile = Pattern.compile(".*(\\s*(\\d{4})\\s*-\\s*(\\d{4})\\s*).*");
        boolean matched = false;

        String join = String.join(" ", subMessage);
        Matcher matcher = compile.matcher(join);
        if (matcher.matches()) {
            String firstYear = matcher.group(2);
            String secondYear = matcher.group(3);
            Year firstYear1 = Year.parse(firstYear);
            Year secondYear2 = Year.parse(secondYear);


            if (firstYear1.compareTo(secondYear2) > 0) {
                sendError("First year must be greater than second", e);
                return null;
            }
            baseYear = firstYear1;
            int i = firstYear1.get(ChronoField.YEAR);
            numberOfYears = secondYear2.minusYears(i).getValue();
            matched = true;
            String replace = join.replace(matcher.group(1), "");
            subMessage = StringUtils.WORD_SPLITTER.split(replace);
        }


        ChartParserAux chartParserAux = new ChartParserAux(subMessage);
        if (!matched) {
            baseYear = Year.of(chartParserAux.parseDecade());
        }


        int x = 5;
        int y = 5;

        Point chartSize;
        try {
            chartSize = chartParserAux.getChartSize();
        } catch (
                InvalidChartValuesException ex) {
            sendError(getErrorMessage(6), e);
            return null;
        }

        if (chartSize != null) {
            x = chartSize.x;
            y = chartSize.y;
        }

        timeFrame = chartParserAux.parseTimeframe(timeFrame);
        subMessage = chartParserAux.getMessage();
        discordName = atTheEndOneUser(e, subMessage);


        return new ChartYearRangeParameters(e, discordName, CustomTimeFrame.ofTimeFrameEnum(timeFrame), x, y, baseYear, numberOfYears);
    }

    @Override
    public List<Explanation> getUsages() {
        return Stream.concat(Stream.of(new DecadeExplanation()), super.getUsages().stream()).toList();
    }

    @Override
    public void setUpErrorMessages() {
        super.setUpErrorMessages();
    }

}
