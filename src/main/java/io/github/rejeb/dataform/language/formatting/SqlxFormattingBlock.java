package io.github.rejeb.dataform.language.formatting;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.formatter.common.InjectedLanguageBlockBuilder;
import com.intellij.psi.tree.IElementType;
import io.github.rejeb.dataform.language.psi.SharedTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SqlxFormattingBlock extends AbstractBlock {
    private static final Indent NONE_INDENT = Indent.getNoneIndent();
    private static final Indent NORMAL_IDENT = Indent.getNormalIndent();
    private static final List<IElementType> JS_TOKENS = List.of(
            SharedTokenTypes.TEMPLATE_EXPRESSION,
            SharedTokenTypes.JS_BLOCK,
            SharedTokenTypes.JS_CONTENT,
            SharedTokenTypes.CONFIG_CONTENT,
            SharedTokenTypes.CONFIG_BLOCK
    );
    private static final Map<IElementType, Indent> INDENT_MAP = Map.of(
            SharedTokenTypes.JS_CONTENT, NORMAL_IDENT,
            SharedTokenTypes.CONFIG_CONTENT, NONE_INDENT,
            SharedTokenTypes.PRE_OPERATIONS_CONTENT, NONE_INDENT,
            SharedTokenTypes.POST_OPERATIONS_CONTENT, NONE_INDENT,
            SharedTokenTypes.SQL_CONTENT, NONE_INDENT,
            SharedTokenTypes.TEMPLATE_EXPRESSION, NORMAL_IDENT,
            SharedTokenTypes.CONFIG_BLOCK, NONE_INDENT,
            SharedTokenTypes.JS_BLOCK, NONE_INDENT
    );

    private final SpacingBuilder spacingBuilder;
    private final InjectedLanguageBlockBuilder injectedLanguageBlockBuilder;

    public SqlxFormattingBlock(@NotNull ASTNode node,
                               @NotNull SpacingBuilder spacingBuilder,
                               @NotNull InjectedLanguageBlockBuilder injectedLanguageBlockBuilder,
                               @Nullable Wrap wrap) {
        super(node, wrap, null);
        this.spacingBuilder = spacingBuilder;
        this.injectedLanguageBlockBuilder = injectedLanguageBlockBuilder;
    }

    @Override
    protected List<Block> buildChildren() {
        List<Block> blocks = new ArrayList<>();
        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {

            IElementType type = child.getElementType();
            if (JS_TOKENS.contains(type)) {
                List<Block> injectedBlocks = new ArrayList<>();
                boolean injected = injectedLanguageBlockBuilder.addInjectedBlocks(injectedBlocks, child, null, null, getIndent());
                if (injected && !injectedBlocks.isEmpty()) {
                    blocks.addAll(injectedBlocks);
                } else {
                    blocks.add(new SqlxFormattingBlock(child, spacingBuilder, injectedLanguageBlockBuilder, getWrap()));
                }
            } else if (type != TokenType.WHITE_SPACE) {
                Block block = new SqlxFormattingBlock(child, spacingBuilder, injectedLanguageBlockBuilder, getWrap());
                blocks.add(block);
            }
            child = child.getTreeNext();
        }
        return blocks;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        if (child1 instanceof AbstractBlock ab && ab.getNode().getElementType() == SharedTokenTypes.LBRACE) {
            IElementType parentType = myNode.getElementType();
            if (parentType == SharedTokenTypes.CONFIG_BLOCK
                    || parentType == SharedTokenTypes.JS_BLOCK
                    || parentType == SharedTokenTypes.PRE_OPERATIONS_BLOCK
                    || parentType == SharedTokenTypes.POST_OPERATIONS_BLOCK) {
                return Spacing.createSpacing(0, 0, 1, false, 0);
            }
        }
        return spacingBuilder.getSpacing(this, child1, child2);
    }

    @Override
    public boolean isLeaf() {
        return myNode.getFirstChildNode() == null;
    }


    @Override
    public @Nullable Indent getIndent() {
        return INDENT_MAP.getOrDefault(myNode.getElementType(), NONE_INDENT);
    }
}