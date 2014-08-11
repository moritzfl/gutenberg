package gutenberg.itext.pegdown;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import gutenberg.itext.AlternateTableRowBackground;
import gutenberg.itext.CellStyler;
import gutenberg.itext.FontAwesomeAdapter;
import gutenberg.itext.PygmentsAdapter;
import gutenberg.itext.Sections;
import gutenberg.pygments.Pygments;
import gutenberg.pygments.StyleSheet;
import gutenberg.pygments.Token;
import gutenberg.pygments.styles.FriendlyStyle;
import gutenberg.util.RGB;
import org.pegdown.ast.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static gutenberg.itext.FontCopier.copyFont;
import static gutenberg.itext.ITextUtils.inconsolata;
import static gutenberg.itext.ITextUtils.toColor;

/**
 * @author <a href="http://twitter.com/aloyer">@aloyer</a>
 */
public class InvocationContext {

    private final Map<Class<?>, Processor> processors;
    private final Processor processorDefault;
    private final Stack<Font> fontStack;
    private final Stack<TableInfos> tableStack;
    private final FontAwesomeAdapter fontAwesome;
    private final StyleSheet styleSheet;
    private final PygmentsAdapter pygments;
    private final Sections sections;
    private final BaseFont verbatimFont;
    private final Stack<CellStyler> cellStylerStack;
    private final Font defaultFont;

    public InvocationContext() throws IOException, DocumentException {
        fontAwesome = new FontAwesomeAdapter();
        processors = Maps.newHashMap();
        processorDefault = new DefaultProcessor();
        defaultFont = FontFactory.getFont(FontFactory.HELVETICA, 12.0f, Font.NORMAL);
        fontStack = new Stack<Font>();
        fontStack.push(defaultFont);
        tableStack = new Stack<TableInfos>();
        styleSheet = new FriendlyStyle();
        verbatimFont = inconsolata();
        cellStylerStack = new Stack<CellStyler>();
        pygments = new PygmentsAdapter(new Pygments(), styleSheet, verbatimFont, 10.0f);
        sections = new Sections(
                FontFactory.getFont(FontFactory.HELVETICA, 18.0f, Font.BOLD, BaseColor.BLACK),
                FontFactory.getFont(FontFactory.HELVETICA, 16.0f, Font.BOLD, BaseColor.DARK_GRAY),
                FontFactory.getFont(FontFactory.HELVETICA, 14.0f, Font.BOLD, BaseColor.DARK_GRAY)
        );

        initProcessors();
    }

    public Font verbatimFont(RGB rgb) {
        return verbatimFont(toColor(rgb));
    }

    private Font verbatimFont(BaseColor color) {
        return new Font(verbatimFont, 12, Font.NORMAL, color);
    }

    public Chunk symbol(String symbol, float size, BaseColor color) {
        return fontAwesome.symbol(symbol, size, color);
    }

    public List<Element> process(int depth, Node node) {
        Processor processor = processors.get(node.getClass());
        if (processor == null)
            processor = processorDefault;
        dumpProcessor(depth, node, processor);

        List<Element> elements = processor.process(depth, node, this);
        if (depth == 0) {
            return rebuildChapterSectionTree(elements);
        } else {
            return elements;
        }
    }

    private List<Element> rebuildChapterSectionTree(List<Element> elements) {
        List<Element> tree = Lists.newArrayList();
        Section prev = null;
        for (Element element : elements) {
            if (element instanceof Section) {
                //
                // Chapter are not added to the document
                // but section are automatically added on parent section...
                //
                if (element instanceof Chapter)
                    tree.add(element);
                prev = (Section) element;
            } else {
                if (prev != null)
                    prev.add(element);
                else
                    tree.add(element);
            }
        }
        return tree;
    }

    private void dumpProcessor(int depth, Node node, Processor processor) {
        String indent = indent(depth);
        System.out.print(indent);
        if (processor == processorDefault) {
            System.out.print(" ");
        } else {
            System.out.print("*");
        }
        System.out.print(node);
        if (node instanceof HeaderNode) {
            System.out.print(" L:" + ((HeaderNode) node).getLevel());
        }
        if (node instanceof VerbatimNode) {
            System.out.print(" T:" + ((VerbatimNode) node).getType());
        }
        System.out.println();
    }

    private static String indent(int level) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < level; i++)
            b.append("    ");
        return b.toString();
    }

    public List<Element> processChildren(int level, Node node) {
        List<Element> subs = Lists.newArrayList();
        for (Node child : node.getChildren()) {
            subs.addAll(process(level + 1, child));
        }
        return subs;
    }

    protected void initProcessors() {
        processors.put(SimpleNode.class, new SimpleNodeProcessor());
        processors.put(BlockQuoteNode.class, new BlockQuoteNodeProcessor());
        processors.put(ParaNode.class, new ParaNodeProcessor());
        processors.put(VerbatimNode.class, new VerbatimNodeProcessor(pygments));
        processors.put(TextNode.class, new TextNodeProcessor());
        processors.put(SpecialTextNode.class, new SpecialTextNodeProcessor());
        processors.put(OrderedListNode.class, new OrderedListNodeProcessor());
        processors.put(BulletListNode.class, new BulletListNodeProcessor());
        processors.put(ListItemNode.class, new ListItemNodeProcessor());
        processors.put(HeaderNode.class, new HeaderNodeProcessor(sections));
        processors.put(CodeNode.class, new CodeNodeProcessor(
                verbatimFont(styleSheet.foregroundOf(Token.Text)),
                toColor(styleSheet.backgroundColor())
        ));
        processors.put(StrongEmphSuperNode.class, new StrongEmphSuperNodeProcessor());
        processors.put(StrikeNode.class, new StrikeNodeProcessor());
        processors.put(SuperNode.class, new SuperNodeProcessor());

        BaseColor veryLightGray = new BaseColor(230, 230, 230);
        processors.put(TableNode.class, new TableNodeProcessor(new AlternateTableRowBackground(veryLightGray)));
        Font headerFont = copyFont(defaultFont).bold().color(BaseColor.WHITE).get();
        processors.put(TableHeaderNode.class, new TableHeaderNodeProcessor(new CellStyler(headerFont) {
            @Override
            public void applyStyle(PdfPCell cell) {
                cell.setBackgroundColor(BaseColor.BLACK);
            }
        }));
        processors.put(TableBodyNode.class, new TableBodyNodeProcessor(new CellStyler(defaultFont)));
        processors.put(TableRowNode.class, new TableRowNodeProcessor());
        processors.put(TableCellNode.class, new TableCellNodeProcessor());
    }

    public Font peekFont() {
        return fontStack.peek();
    }

    public void pushFont(Font font) {
        fontStack.push(font);
    }

    public Font popFont() {
        return fontStack.pop();
    }

    public TableInfos peekTable() {
        return tableStack.peek();
    }

    public void pushTable(TableInfos table) {
        tableStack.push(table);
    }

    public TableInfos popTable() {
        return tableStack.pop();
    }

    public CellStyler peekCellStyler() {
        return cellStylerStack.peek();
    }

    public void pushCellStyler(CellStyler cellStyler) {
        cellStylerStack.push(cellStyler);
    }

    public CellStyler popCellStyler() {
        return cellStylerStack.pop();
    }
}