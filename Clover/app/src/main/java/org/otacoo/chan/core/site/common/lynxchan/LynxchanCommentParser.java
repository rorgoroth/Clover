package org.otacoo.chan.core.site.common.lynxchan;

import org.otacoo.chan.core.site.parser.CommentParser;
import org.otacoo.chan.core.site.parser.StyleRule;

import java.util.regex.Pattern;

public class LynxchanCommentParser extends CommentParser {
    public LynxchanCommentParser() {
        addDefaultRules();
        
        // Lynxchan quotes usually look like >>123456 or /[board]/res/[thread].html#[post]
        setQuotePattern(Pattern.compile(".*#(\\d+)"));
        setFullQuotePattern(Pattern.compile("/(\\w+)/res/(\\d+)\\.html#(\\d+)"));

        // Lynxchan specific HTML tags
        rule(StyleRule.tagRule("span").cssClass("quote").color(StyleRule.Color.QUOTE).linkify());
        rule(StyleRule.tagRule("span").cssClass("greenText").color(StyleRule.Color.INLINE_QUOTE));
        rule(StyleRule.tagRule("span").cssClass("pinkText").color(StyleRule.Color.PINK));
        rule(StyleRule.tagRule("span").cssClass("redText")
                .color(StyleRule.Color.RED)
                .bold()
                .relativeSize(1.25f));
    }
}
