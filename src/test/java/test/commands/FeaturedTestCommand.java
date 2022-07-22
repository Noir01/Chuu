package test.commands;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.commands.utils.CommandTest;
import test.commands.utils.EmbedWithFieldsUtils;
import test.commands.utils.FieldRowMatcher;
import test.commands.utils.TestResources;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FeaturedTestCommand extends CommandTest {
    private static Pattern titlePattern;

    @BeforeAll
    public static void init() {
        titlePattern = Pattern.compile("(.*)'s Featured Artist:");

    }

    @Override
    public String giveCommandName() {
        return "!featured";
    }

    @Override
    public void nullParserReturned() {

    }

    @Test
    public void normalUsage() {
        List<FieldRowMatcher> fieldRowMatchers = new ArrayList<>();
        //Relaxed pattern, won't be the exact one when album or artist has -

        fieldRowMatchers.add(new FieldRowMatcher("Artist:", Pattern.compile("(.*)")));
        fieldRowMatchers.add(new FieldRowMatcher("User:", Pattern.compile("(.*)"),
                matcher ->
                        matcher.group(1).equalsIgnoreCase("Chuu") || TestResources.channelWorker.getGuild().getMembers()
                                                                             .stream()
                                                                             .filter(x -> x.getUser().getName().equals(matcher.group(1))).count() == 1));

        fieldRowMatchers.add(FieldRowMatcher.numberFieldFromRange("Total Artist Plays:", 1));

        EmbedWithFieldsUtils
                .testEmbedWithFields(COMMAND_ALIAS, null, fieldRowMatchers, titlePattern, matcher
                        -> TestResources.channelWorker.getGuild().getMembers().stream().filter(x -> matcher.group(1)
                        .equals(x.getEffectiveName())).count() == 1);

    }

}
