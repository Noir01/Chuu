package test.commands.utils;

import dao.entities.TriFunction;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class EmbedTester {
    private final String command;
    private final Pattern footerPatern;
    private final Predicate<Matcher> footerMatch;
    private final Pattern titlePattern;
    private final Predicate<Matcher> titleMatch;
    private final Pattern descriptionPattern;
    private final Predicate<Matcher> descriptionMatch;
    private final Pattern noEmbbed;
    private final Predicate<Matcher> noEmbbedMatcher;
    private final int timeout;
    private final List<FieldRowMatcher> fieldRowMatcher;
    private final boolean hasThumbnail;
    private final String thumbnailUrl;
    private final TriFunction<String, Pattern, Predicate<Matcher>, Boolean> internalFunction = (string, regex, matcherPredicate) -> {
        if (string != null) {
            if (regex != null) {
                Matcher matcher = regex.matcher(string);
                assertThat(matcher.matches()).isTrue();
                if (matcherPredicate != null) {
                    return matcherPredicate.test(matcher);
                }
            }
        }
        return true;
    };
    private final FieldMatcher internalMatcher = (field, string, pattern, matcherPredicate) -> {
        if (field != null && field.getName() != null && field.getName().equals(string)) {
            Matcher matcher = pattern.matcher(field.getValue());
            return (matcher.matches()) && matcherPredicate.test(matcher);
        }
        return false;
    };

    public EmbedTester(String command, Pattern footerPatern, Predicate<Matcher> footerMatch, Pattern titlePattern, Predicate<Matcher> titleMatch, Pattern descriptionPattern, Predicate<Matcher> descriptionMatch, Pattern noEmbbed, Predicate<Matcher> noEmbbedMatcher, int timeout, List<FieldRowMatcher> fieldRowMatcher, boolean hasThumbnail, String thumbnailUrl) {
        this.command = command;
        this.footerPatern = footerPatern;
        this.footerMatch = footerMatch;
        this.titlePattern = titlePattern;
        this.titleMatch = titleMatch;
        this.descriptionPattern = descriptionPattern;
        this.descriptionMatch = descriptionMatch;
        this.noEmbbed = noEmbbed;
        this.noEmbbedMatcher = noEmbbedMatcher;
        this.timeout = timeout;
        this.fieldRowMatcher = fieldRowMatcher;
        this.hasThumbnail = hasThumbnail;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void GeneralFunction() {
        long id = TestResources.channelWorker.sendMessage(command).complete().getIdLong();
        await().atMost(timeout, TimeUnit.SECONDS).until(() ->
        {
            MessageHistory complete = TestResources.channelWorker.getHistoryAfter(id, 20).complete();
            return complete.getRetrievedHistory().size() == 1;
        });

        Message message = TestResources.channelWorker.getHistoryAfter(id, 20).complete().getRetrievedHistory().get(0);

        if (!message.getEmbeds().isEmpty()) {

            MessageEmbed messageEmbed = message.getEmbeds().get(0);
            MessageEmbed.Footer footer = messageEmbed.getFooter();

            assertThat(internalFunction
                    .apply(footer == null ? null : footer.getText(), footerPatern, footerMatch)).isTrue();
            assertThat(internalFunction.apply(messageEmbed.getTitle(), titlePattern, titleMatch)).isTrue();

            String description = messageEmbed.getDescription();

            if (description != null) {
                description = description.replaceAll("\\*", "");
                String[] split = description.split("\n");
                for (String s : split) {
                    assertThat(internalFunction.apply(s, descriptionPattern, descriptionMatch)).isTrue();
                }
            }

            if (hasThumbnail) {
                if (messageEmbed.getThumbnail() == null) {
                    assertThat(thumbnailUrl).isNull();
                } else {
                    assertThat(messageEmbed.getThumbnail().getUrl()).isEqualTo(thumbnailUrl);
                }
            }
            if (fieldRowMatcher != null) {
                List<FieldRowMatcher> localFieldRowMatcher = new ArrayList<>(fieldRowMatcher);
                List<MessageEmbed.Field> fields = new ArrayList<>(messageEmbed.getFields());
                for (int i = 0; i < fields.size(); i++) {
                    for (int j = 0; j < localFieldRowMatcher.size(); j++) {
                        FieldRowMatcher fieldRowMatcher = localFieldRowMatcher.get(j);
                        if (internalMatcher
                                .apply(fields.get(i), fieldRowMatcher.getTitle(), fieldRowMatcher
                                        .getPattern(), fieldRowMatcher
                                        .getPredicate())) {
                            fields.remove(i--);
                            localFieldRowMatcher.remove(j);
                            break;
                        }
                    }
                }
                assertThat(fields).hasSize(0);
                assertThat(localFieldRowMatcher).hasSize(0);
            }

        } else {
            assertThat(internalFunction.apply(message.getContentStripped(), noEmbbed, noEmbbedMatcher)).isTrue();
        }


    }
}


