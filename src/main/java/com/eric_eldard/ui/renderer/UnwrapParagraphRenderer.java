package com.eric_eldard.ui.renderer;

import org.commonmark.node.Document;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.CoreHtmlNodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Removes wrapper paragraph so text-only messages can appear next to the message sender, on the same line
 */
public class UnwrapParagraphRenderer extends CoreHtmlNodeRenderer implements NodeRenderer
{
    public UnwrapParagraphRenderer(HtmlNodeRendererContext context)
    {
        super(context);
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes()
    {
        Set<Class<? extends Node>> types = new HashSet<>();
        types.add(Paragraph.class);
        return types;
    }

    @Override
    public void render(Node node)
    {
        if (node.getParent() instanceof Document)
        {
            visitChildren(node);
        }
        else
        {
            visit((Paragraph) node);
        }
    }
}