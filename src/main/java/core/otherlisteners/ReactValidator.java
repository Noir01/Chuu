package core.otherlisteners;

import core.commands.Context;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static core.otherlisteners.Reactions.LEFT_ARROW;
import static core.otherlisteners.Reactions.RIGHT_ARROW;

public class ReactValidator<T> extends ReactionListener {

    private final Function<EmbedBuilder, EmbedBuilder> getLastMessage;
    private final Supplier<T> elementFetcher;
    private final BiFunction<T, EmbedBuilder, EmbedBuilder> fillBuilder;
    private final long whom;
    private final Context context;
    private final Map<String, Reaction<T, MessageReactionAddEvent, ReactionResult>> actionMap;
    private final boolean allowOtherUsers;
    private final boolean renderInSameElement;
    private final AtomicBoolean hasCleaned = new AtomicBoolean(false);
    private T currentElement;

    public ReactValidator(UnaryOperator<EmbedBuilder> getLastMessage, Supplier<T> elementFetcher, BiFunction<T, EmbedBuilder, EmbedBuilder> fillBuilder, EmbedBuilder who, Context context, long discordId,
                          Map<String, Reaction<T, MessageReactionAddEvent, ReactionResult>> actionMap, boolean allowOtherUsers, boolean renderInSameElement) {
        super(who, null, 30, context.getJDA());
        this.getLastMessage = getLastMessage;
        this.elementFetcher = elementFetcher;
        this.fillBuilder = fillBuilder;
        this.context = context;
        this.whom = discordId;
        this.actionMap = actionMap;
        this.allowOtherUsers = allowOtherUsers;
        this.renderInSameElement = renderInSameElement;

        init();
    }

    @org.jetbrains.annotations.NotNull
    public static ReactionResult leftMove(int size, AtomicInteger counter, MessageReactionAddEvent r, boolean isSame) {
        int i = counter.decrementAndGet();
        if (i == 0) {
            if (isSame) {
                r.getReaction().clearReactions().queue();
            } else {
                r.getChannel().removeReactionById(r.getMessageId(), Emoji.fromUnicode(LEFT_ARROW)).queue();
            }
        }
        if (i == size - 2) {
            r.getChannel().addReactionById(r.getMessageIdLong(), Emoji.fromUnicode(RIGHT_ARROW)).queue();
        }
        return () -> false;
    }

    @org.jetbrains.annotations.NotNull
    public static ReactionResult rightMove(int size, AtomicInteger counter, MessageReactionAddEvent r, boolean isSame) {
        int i = counter.incrementAndGet();
        if (i == size - 1) {
            if (isSame) {
                r.getReaction().clearReactions().queue();
            } else {
                r.getChannel().removeReactionById(r.getMessageId(), Emoji.fromUnicode(RIGHT_ARROW)).queue();
            }
        }
        if (i == 1) {
            r.getChannel().addReactionById(r.getMessageIdLong(), Emoji.fromUnicode(LEFT_ARROW)).queue();
        }
        return () -> false;
    }

    private void noMoreElements() {
        RestAction<Message> a;
        if (hasCleaned.compareAndSet(false, true)) {
            boolean check;
            if (message == null) {
                check = true;
                a = context.sendMessage(getLastMessage.apply(who).build());
            } else {
                check = false;
                a = message.editMessageEmbeds(getLastMessage.apply(who).build());
            }
            a.queue(z -> {
                if (check) {
                    message = z;
                }
                clearReacts();
            });
            this.unregister();
        }
    }

    private void initEmotes() {
        List<RestAction<Void>> reacts = this.actionMap.keySet().stream().map(x -> message.addReaction(Emoji.fromFormatted(x))).toList();
        RestAction.allOf(reacts).queue();
    }

    private RestAction<Message> doTheThing(ReactionResult newElement) {
        T t = elementFetcher.get();
        if (t == null) {
            noMoreElements();
            return null;
        }
        this.currentElement = t;
        return dotheLogicThing(t, newElement);
    }

    private RestAction<Message> dotheLogicThing(T t, ReactionResult newElement) {
        EmbedBuilder apply = fillBuilder.apply(t, who);
        if (newElement.newResult() || this.message == null) {
            return context.sendMessage(apply.build());
        }
        return this.message.editMessageEmbeds(apply.build());
    }

    @Override
    public void init() {
        RestAction<Message> messageAction = doTheThing(() -> true);
        if (messageAction == null) {
            return;
        }
        messageAction.queue(z -> {
            this.message = z;
            initEmotes();
        });
    }

    @Override
    public void dispose() {
        noMoreElements();
    }

    @Override
    public void onEvent(@Nonnull GenericEvent event) {
        if (event instanceof MessageReactionAddEvent e) {
            if (isValid(e)) {
                onMessageReactionAdd(e);
            }
        }
    }

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event) {
        Reaction<T, MessageReactionAddEvent, ReactionResult> action = this.actionMap.get(event.getReaction().getEmoji().getFormatted());
        if (action == null)
            return;
        ReactionResult apply = action.release(currentElement, event);
        RestAction<Message> messageAction = this.doTheThing(apply);
        if (messageAction != null) {
            if (apply.newResult()) {
                messageAction.queue(this::accept);
            } else if (event.getUser() != null) {
                clearOneReact(event);
                if (renderInSameElement) {
                    messageAction.queue();
                }
            } else {
                messageAction.queue();
            }
        }
        refresh(event.getJDA());
    }

    public boolean isValid(@Nonnull MessageReactionAddEvent event) {
        if (this.message == null) {
            return false;
        }
        return !(event.getMessageIdLong() != message.getIdLong() || (!this.allowOtherUsers && event.getUserIdLong() != whom) ||
                event.getUserIdLong() == event.getJDA().getSelfUser().getIdLong());
    }

    @Override
    public boolean isValid(ButtonInteractionEvent event) {
        return false;
    }

    @Override
    public boolean isValid(SelectMenuInteractionEvent event) {
        return false;
    }

    @Override
    public void onButtonClickedEvent(@Nonnull ButtonInteractionEvent event) {
    }

    @Override
    public void onSelectedMenuEvent(@NotNull SelectMenuInteractionEvent event) {

    }

    private void accept(Message mes) {
        this.message.delete().queue(t -> {
            this.message = mes;
            this.initEmotes();
        });
    }

}
