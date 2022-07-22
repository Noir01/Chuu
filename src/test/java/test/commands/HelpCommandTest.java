package test.commands;

import core.commands.abstracts.MyCommand;
import core.commands.charts.AlbumChartCommand;
import core.commands.config.HelpCommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import test.commands.utils.CommandTest;
import test.commands.utils.TestResources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class HelpCommandTest extends CommandTest {
    private static HelpCommand helpCommand;

    @BeforeAll
    public static void init() {
        Optional<Object> first = TestResources.ogJDA.getEventManager().getRegisteredListeners().stream()
                .filter(x -> x instanceof HelpCommand).findFirst();
        assertThat(first.isPresent()).isTrue();
        helpCommand = (HelpCommand) first.get();
    }

    @Override
    public String giveCommandName() {
        return "!help";
    }

    @Test
    @Override
    public void nullParserReturned() {

    }

    @Test
    public void notExistsMessage() {
        long id = TestResources.channelWorker.sendMessage("marker").complete().getIdLong();

        //Usually the help message is sent to a private channel but i didnt find a way to get a private message from a marker so i opted to
        //send the message directly to the test channel
        String nonExistingCommand = "NonExistentCommand19219";
        helpCommand.sendPrivate(TestResources.ogJDA
                .getTextChannelById(TestResources.channelWorker.getId()), null);

        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();

            return complete.getRetrievedHistory().size() == 1;
        });
        Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);
        String s = "The provided command '**" + nonExistingCommand + "**' does not exist. Use " + "!help to list all commands.";
        assertThat(message.getContentRaw()).isEqualTo(s);

    }

    @Test
    public void BigHelpMessage() {

        long id = TestResources.channelWorker.sendMessage("marker").complete().getIdLong();

        //Usually the help message is sent to a private channel but i didnt find a way to get a private message from a marker so i opted to
        //send the message directly to the test channel

        helpCommand.sendPrivate(TestResources.ogJDA.getTextChannelById(TestResources.channelWorker.getId()), null);

        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();

            return complete.getRetrievedHistory().size() == 2;
        });
        Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(1);
        Message message1 = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);

        bigMessageAssert(message, message1);
    }

    private void bigMessageAssert(Message message, Message message1) {
        String[] split = message.getContentStripped().split("\n");
        String[] split1 = message1.getContentStripped().split("\n");

        //All should be commands
        List<String> strings1 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(split, 11, split.length)));
        List<String> split2 = new ArrayList<>(Arrays.asList(split1));

        strings1.addAll(split2);

        for (String s : strings1) {
            assertThat(s.matches("!(\\w+) - .*")).isTrue();
        }

        long count = TestResources.ogJDA.getEventManager().getRegisteredListeners().stream().filter(x -> x instanceof MyCommand)
                .count();
        assertThat(strings1.size()).isEqualTo(count);
        assertThat("The following commands are supported by the bot").isEqualTo(split[0]);
    }

    @Test
    public void smallHelpMessage() {
        long id = TestResources.channelWorker.sendMessage("marker").complete().getIdLong();
        for (Object registeredListener : TestResources.ogJDA.getRegisteredListeners()) {
            if (registeredListener instanceof MyCommand<?> command) {
                //String alias = command.getAliases().get(0);
                helpCommand
                        .sendPrivate(TestResources.ogJDA.getTextChannelById(TestResources.channelWorker.getId()), null);
                long finalId = id;
                await().atMost(45, TimeUnit.SECONDS).until(() ->
                {
                    MessageHistory complete = TestResources.channelWorker.getHistoryAfter(finalId, 20).complete();

                    return complete.getRetrievedHistory().size() == 1;
                });
                Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);
                id = message.getIdLong();
                String collect = "!" + String.join(", !", command.getAliases());
                String expected = "Name: " + command.getName() + "\n"
                                  + "Description: " + command.getDescription() + "\n"
                                  + "Alliases: " + collect + "\n"
                                  + "Usage: !" + command.getUsageInstructions().replaceAll("\\*", "").trim();
                assertThat(message.getContentStripped().replaceAll("\\*", "").trim()).isEqualTo(expected);

            }
        }


    }

    @Test
    public void PrivateMessage() {
        //Cannot send private meessage between bots :(

        Pattern responsePattern = Pattern.compile(
                ".+?(?=: Help information was sent as a private message\\.): Help information was sent as a private message\\.");
        long id = TestResources.channelWorker.sendMessage(COMMAND_ALIAS).complete().getIdLong();
        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
            return complete.getRetrievedHistory().size() == 1;
        });
        MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
        Message message = complete.getRetrievedHistory().get(0);
        assertThat(responsePattern.matcher(message.getContentStripped()).matches()).isTrue();
    }

    @Test
    public void ShortHelpViaCommand() {
        //Cannot send private meessage between bots :(
        Optional<AlbumChartCommand> chartCommand = TestResources.ogJDA.getRegisteredListeners().stream()
                .filter(x -> x instanceof AlbumChartCommand).map(x -> (AlbumChartCommand) x).findFirst();
        assertThat(chartCommand.isPresent()).isTrue();
        AlbumChartCommand command = chartCommand.get();
        long id = TestResources.channelWorker.sendMessage(COMMAND_ALIAS + " chart").complete().getIdLong();
        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
            return complete.getRetrievedHistory().size() == 1;
        });
        MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
        Message message = complete.getRetrievedHistory().get(0);
        String collect = "!" + String.join(", !", command.getAliases());

        String expected = "Name: " + command.getName() + "\n"
                          + "Description: " + command.getDescription() + "\n"
                          + "Alliases: " + collect + "\n"
                          + "Usage: !" + command.getUsageInstructions().replaceAll("\\*", "").trim();
        assertThat(message.getContentStripped().replaceAll("\\*", "").trim()).isEqualTo(expected);
    }


}
