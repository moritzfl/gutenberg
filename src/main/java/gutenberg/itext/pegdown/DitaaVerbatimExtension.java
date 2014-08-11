package gutenberg.itext.pegdown;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import gutenberg.itext.PygmentsAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stathissideris.ascii2image.core.ConversionOptions;
import org.stathissideris.ascii2image.graphics.BitmapRenderer;
import org.stathissideris.ascii2image.graphics.Diagram;
import org.stathissideris.ascii2image.text.TextGrid;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;

import static gutenberg.itext.pegdown.Processor.elements;
import static java.util.Arrays.asList;

/**
 * @author <a href="http://twitter.com/aloyer">@aloyer</a>
 */
public class DitaaVerbatimExtension implements VerbatimExtension {

    private final Logger log = LoggerFactory.getLogger(DitaaVerbatimExtension.class);
    private final PygmentsAdapter pygments;

    public DitaaVerbatimExtension(PygmentsAdapter pygments) {
        this.pygments = pygments;
    }

    public boolean accepts(String lang) {
        return asList("ditaa").contains(lang.toLowerCase());
    }

    public List<Element> process(String lang, String code) {
        try {
            TextGrid grid = new TextGrid();
            grid.initialiseWithText(code, null);

            ConversionOptions options = new ConversionOptions();
            options.renderingOptions.setScale(2.0f);
            Diagram diagram = new Diagram(grid, options);

            RenderedImage image = new BitmapRenderer().renderToImage(diagram, options.renderingOptions);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(image, "png", os);
            Image img = Image.getInstance(os.toByteArray());
            img.scalePercent(0.5f*100f);

            return elements(img);

        } catch (UnsupportedEncodingException e) {
            log.error("Oops", e);
        } catch (MalformedURLException e) {
            log.error("Oops", e);
        } catch (BadElementException e) {
            log.error("Oops", e);
        } catch (IOException e) {
            log.error("Oops", e);
        }
        return pygments.process(lang, code);
    }
}
