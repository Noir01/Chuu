package test.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import org.junit.jupiter.api.Test;
import test.commands.parsers.NullReturnParsersTest;
import test.commands.utils.CommandTest;
import test.commands.utils.TestResources;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class LogoCommandTest extends CommandTest {

    @Override
    public String giveCommandName() {
        return "!logo";
    }

    @Test
    @Override
    public void nullParserReturned() {
        NullReturnParsersTest.urlParser(COMMAND_ALIAS);
    }


    @Test
    public void NormalLogoUpdate() {
        long id = TestResources.channelWorker
                .sendMessage(COMMAND_ALIAS + " https://lastfm.freetls.fastly.net/i/u/770x0/299814a38e10e697599ff128c5c7f0b0.png")
                .complete().getIdLong();
        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
            return complete.getRetrievedHistory().size() == 1;
        });
        Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);
        assertThat("Logo updated").isEqualTo(message.getContentStripped());
    }

    @Test
    public void NormalLogoDelete() {
        long id = TestResources.channelWorker.sendMessage(COMMAND_ALIAS).complete().getIdLong();
        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
            return complete.getRetrievedHistory().size() == 1;
        });
        Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);
        assertThat("Removed logo from the server").isEqualTo(message.getContentStripped());
    }

    @Test
    public void InvalidUrlLogoUpdate() {
        long id = TestResources.channelWorker.sendMessage(COMMAND_ALIAS + " zxy").complete().getIdLong();
        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
            return complete.getRetrievedHistory().size() == 1;
        });
        Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);
        assertThat(message.getContentStripped()).isEqualTo("Error on TesterBot's request:\nInvalid url");
    }

    @Test
    public void ValidUrlNoImageLogoUpdate() {
        long id = TestResources.channelWorker
                .sendMessage(COMMAND_ALIAS + "  https://stackoverflow.com/questions/27391055/how-to-share-junit-beforeclass-logic-among-multiple-test-classes")
                .complete().getIdLong();
        await().atMost(45, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
            return complete.getRetrievedHistory().size() == 1;
        });
        Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);
        assertThat("Couldn't get an Image from link supplied").isEqualTo(message.getContentStripped());
    }


}
