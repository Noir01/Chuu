package test.commands;

import org.junit.jupiter.api.Test;
import test.commands.parsers.NullReturnParsersTest;
import test.commands.utils.CommandTest;
import test.commands.utils.OneLineUtils;
import test.commands.utils.TestResources;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlArtistCommandTest extends CommandTest {


    @Override
    public String giveCommandName() {
        return "!url";
    }

    @Test
    @Override
    public void nullParserReturned() {
        NullReturnParsersTest.artistUrlParser(COMMAND_ALIAS);
    }

    @Test
    public void normalCase() {
        Pattern pattern = Pattern.compile("Image of (.*) updated");
        Predicate<Matcher> predicate = matcher1 ->
                matcher1.group(1).equalsIgnoreCase("blackpink");
        OneLineUtils
                .testCommands(COMMAND_ALIAS + " blackpink  https://lastfm.freetls.fastly.net/i/u/770x0/4a34236b764b92094ce0693a68b3dee8.png ",
                        pattern, predicate);
        OneLineUtils
                .embedLink(COMMAND_ALIAS + " blackpink", "https://lastfm.freetls.fastly.net/i/u/770x0/4a34236b764b92094ce0693a68b3dee8.png",
                        pattern, predicate, 10);
    }

    @Test
    public void invalidUrl() {

        Pattern pattern = Pattern.compile("Error on (.*?)'s request:\nCouldn't get an Image from link supplied");
        Predicate<Matcher> predicate = matcher1 ->
                matcher1.group(1).equals(TestResources.testerJdaUsername);

        OneLineUtils
                .testCommands(COMMAND_ALIAS + " blackpink  https://www.google.com",
                        pattern, predicate);

        OneLineUtils
                .testCommands(COMMAND_ALIAS + " blackpink  https://lastfm.freetls.fastly.net/i/u/770x0/4a34236b764b92094ce0693a68b3dee8.webp",
                        pattern, predicate);
    }

}
