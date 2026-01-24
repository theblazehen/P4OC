package com.fluid.afm.markdown;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fluid.afm.MarkdownAwareMovementMethod;
import com.fluid.afm.markdown.code.CodeBlockPlugin;
import com.fluid.afm.markdown.html.CustomHtmlPlugin;
import com.fluid.afm.markdown.html.HtmlMarkTagHandler;
import com.fluid.afm.markdown.html.HtmlParaTagHandler;
import com.fluid.afm.markdown.html.HtmlSpanTagHandler;
import com.fluid.afm.markdown.list.DefinitionListPlugin;
import com.fluid.afm.markdown.span.LinkClickSpan;
import com.fluid.afm.markdown.text.AfmTextPlugin;
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView;
import com.fluid.afm.styles.MarkdownStyles;

import org.commonmark.node.Link;

import java.util.ArrayList;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.RenderProps;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParser;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.movement.MovementMethodPlugin;

public class MarkdownParserFactory {
    public static final String TAG = "MarkdownPluginsCreator";

    public static MarkdownParser create(Context context, PrinterMarkDownTextView textView) {
        return create(context, textView, MarkdownStyles.getDefaultStyles(), null);
    }

    public static void bindMarkdownParser(Context context, PrinterMarkDownTextView textView, MarkdownStyles styles, ElementClickEventCallback callback) {
        create(context, textView, styles, callback);
    }

    public static MarkdownParser create(Context context, PrinterMarkDownTextView textView, MarkdownStyles styles, ElementClickEventCallback callback) {
        ArrayList<AbstractMarkwonPlugin> finalPlugins = getDefaultPlugins(context, textView, callback);
        return new MarkdownParser(context, finalPlugins, textView, styles);
    }

    public static ArrayList<AbstractMarkwonPlugin> getPlugins(Context context, PrinterMarkDownTextView textView, TablePlugin tablePlugin) {
        ArrayList<AbstractMarkwonPlugin> plugins = getDefaultPlugins(context, textView, null);
        plugins.add(tablePlugin);
        plugins.add(CorePlugin.create(context));
        plugins.add(MarkwonInlineParserPlugin.create(MarkwonInlineParser.factoryBuilder()));
        plugins.add(CodeBlockPlugin.create(context, false));
        return plugins;
    }

    public static ArrayList<AbstractMarkwonPlugin> getDefaultPlugins(Context context, PrinterMarkDownTextView textView, ElementClickEventCallback callback) {
        ArrayList<AbstractMarkwonPlugin> plugins = new ArrayList<>();
        plugins.add(linkPlugin(callback));
        if (textView != null) {
            plugins.add(htmlPlugin(context, textView, callback));
        }
        plugins.add(MovementMethodPlugin.create(MarkdownAwareMovementMethod.create()));
        plugins.add(createStrikethroughPlugin());
        plugins.add(printStreamPlugin(context, callback));
        plugins.add(definitationPlugin());
        plugins.add(TablePlugin.create(context));
        return plugins;
    }

    public static AbstractMarkwonPlugin createStrikethroughPlugin() {
        return StrikethroughPlugin.create();
    }

    public static AbstractMarkwonPlugin definitationPlugin() {
        return DefinitionListPlugin.create();
    }

    public static AbstractMarkwonPlugin linkPlugin(ElementClickEventCallback callback) {
        return new AbstractMarkwonPlugin() {
            @Override
            public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                builder.setFactory(Link.class, new SpanFactory() {
                    @Nullable
                    @Override
                    public Object getSpans(@NonNull MarkwonConfiguration configuration, @NonNull RenderProps props) {
                        return new LinkClickSpan(CoreProps.LINK_DESTINATION.require(props), props, configuration.theme(), callback);
                    }
                });
            }
        };
    }

    public static AbstractMarkwonPlugin htmlPlugin(Context context, TextView textView, ElementClickEventCallback onClickCallback) {
        return CustomHtmlPlugin.create()
                .addHandler(new HtmlSpanTagHandler(context, onClickCallback))
                .addHandler(new HtmlMarkTagHandler())
                .addHandler(new HtmlParaTagHandler(context));
    }

    public static AbstractMarkwonPlugin printStreamPlugin(Context context, ElementClickEventCallback linkClickedCallback) {
        return new AfmTextPlugin(context).setCustomClickListener(linkClickedCallback);
    }

}
