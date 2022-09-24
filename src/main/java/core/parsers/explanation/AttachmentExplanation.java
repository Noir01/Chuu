package core.parsers.explanation;

import core.parsers.explanation.util.Explanation;
import core.parsers.explanation.util.ExplanationLineType;
import core.parsers.explanation.util.Interactible;
import net.dv8tion.jda.api.interactions.commands.OptionType;

public class AttachmentExplanation implements Explanation {
    public static final String NAME = "attachment";

    @Override
    public Interactible explanation() {
        return new ExplanationLineType(NAME,
                "A url to an image. It can be a plain image or you can upload directly to discord using the command", OptionType.ATTACHMENT);
    }
}
