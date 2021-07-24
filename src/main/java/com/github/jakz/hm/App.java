package com.github.jakz.hm;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.github.jakz.hm.data.comics.ComicArchive;
import com.github.jakz.hm.data.comics.ComicCollection;
import com.github.jakz.hm.data.comics.ComicIssue;
import com.github.jakz.hm.data.comics.IssueDate;
import com.github.jakz.hm.formats.Format;
import com.github.jakz.hm.formats.FormatGuesser;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfDocument;
import com.itextpdf.text.pdf.PdfImage;
import com.itextpdf.text.pdf.PdfPage;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.pixbits.lib.functional.StreamException;
import com.pixbits.lib.io.archive.support.Archive;
import com.pixbits.lib.io.archive.support.Archive.Item;
import com.pixbits.lib.ui.UIUtils;
import com.pixbits.lib.ui.table.ColumnSpec;
import com.pixbits.lib.ui.table.DataSource;
import com.pixbits.lib.ui.table.FilterableDataSource;
import com.pixbits.lib.ui.table.TableModel;
import com.pixbits.lib.ui.table.renderers.LambdaLabelTableRenderer;
import com.pixbits.lib.util.IconCache;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

public class App
{
  public static class Mediator
  {
  }
  
  static IconCache<ComicArchive> cache = new IconCache<>(StreamException.rethrowFunction(c -> {
    return ComicArchive.fetchThumbnail(c);
  }));
  
 
  public static class ComicsTable extends JPanel
  {
    private final Mediator mediator;

    private FilterableDataSource<ComicIssue> data;

    private TableModel<ComicIssue> model;
    private JTable table;

    public ComicsTable(Mediator mediator)
    {
      this.mediator = mediator;

      table = new JTable();
      table.setAutoCreateRowSorter(true);
      model = new Model(table);

      setLayout(new BorderLayout());
      JScrollPane pane = new JScrollPane(table);
      pane.setPreferredSize(new Dimension(600, 500));
      add(pane, BorderLayout.CENTER);

      model.addColumn(new ColumnSpec<ComicIssue, ImageIcon>("", ImageIcon.class, m -> m.entry != null ? cache.asyncGet(m.entry, model::fireTableDataChanged) : new ImageIcon()));
      model.addColumn(new ColumnSpec<ComicIssue, Integer>("", Integer.class, m -> m.number).setWidth(60));
      model.addColumn(new ColumnSpec<ComicIssue, String>("Year", String.class, m -> m.date.toString()).setWidth(100));
      model.addColumn(new ColumnSpec<ComicIssue, String>("Name", String.class, m -> m.title).setWidth(100));
      model.addColumn(new ColumnSpec<>("Type", String.class, m -> m.entry != null ? m.entry.type.name() : ""));
      model.addColumn(
          new ColumnSpec<>("Path", String.class, m -> m.entry != null ? m.entry.path.getFileName().toString() : ""));

      //model.setRowHeight(120);
    }

    public void refresh(Collection<ComicIssue> data)
    {
      this.data = FilterableDataSource.of(data);
      model.setData(this.data);
      model.fireTableDataChanged();
    }

    private class Model extends TableModel<ComicIssue>
    {
      Model(JTable table)
      {
        super(table, DataSource.empty());
      }
    }
  }
  
  public static void scanFolderForCollection(ComicCollection collection, Path root, boolean printMissing) throws IOException
  {
    List<Path> files = Files.walk(root).filter(p -> Files.isRegularFile(p)).collect(Collectors.toList());
    
    Pattern regex = Pattern.compile("([a-zA-Z\\ \\-]+)\\ ([0-9]+).*");
    //Pattern regex = Pattern.compile("([0-9]+)");
    
    Set<Integer> found = files.stream().map(path -> {
      String filename = path.getFileName().toString();
      Matcher matcher = regex.matcher(filename);
      
      if (matcher.find())
        return Integer.valueOf(matcher.group(2));
      else
        return -1;
    }).filter(i -> i > 0).collect(Collectors.toSet());
    
    Stream<Integer> generator = Stream.iterate(1, i -> i + 1);
    
    Set<Integer> all = generator.limit(collection.size()).collect(Collectors.toSet());
    Set<Integer> missing = new TreeSet<>(all);
    missing.removeAll(found);
    
    System.out.printf("%s: owned %d of %d issues (%2.2f%%).\n", collection.name(), found.size(), all.size(),
        (found.size() / (float) all.size()) * 100);
    
    
    if ((missing.size() < 10 && missing.size() > 0) || printMissing)
      System.out.println(" Missing: "+missing.stream().map(Object::toString).collect(Collectors.joining(", ")));
  }
  
  public static void scanForDigitalPDFsIntoFolder() throws IOException
  {
    Path dest = Paths.get("F:\\Ebooks\\Comics\\Disney\\Topolino\\Temp\\Digital");
    
    Files.createDirectories(dest);
    
    Path root = Paths.get("F:\\Ebooks\\Comics\\Disney\\Topolino\\Temp");
    List<Path> files = Files.walk(root).filter(p -> Files.isRegularFile(p)).collect(Collectors.toList());

    files.stream().forEach(StreamException.rethrowConsumer(path -> {
      boolean digital = ComicArchive.isPDFDigital(path);
      System.out.println(path.getFileName() + ": " + ComicArchive.isPDFDigital(path));
      
      if (digital)
      {
        System.out.println("Moving "+path.getFileName());
        Files.move(path,  dest.resolve(path.getFileName()));
      }
    }));
  }
  
  public static void convertCBZtoPDF(Path from, Path to) throws IOException, DocumentException
  {
    ZipFile file = new ZipFile(from.toFile());
    List<FileHeader> entries = file.getFileHeaders();
    
    Document doc = null;
    PdfWriter writer = null;
    PdfContentByte canvas = null;
    
    Rectangle size = null;

    int counter = 1;
    
    for (FileHeader entry : entries)
    {
      System.out.println(entry.getFileName());
      if (entry.isDirectory())
        continue;
      
      Path dest = Paths.get("temp");
      String destPath = dest.toAbsolutePath().resolve(entry.getFileName()).toString();
            
      file.extractFile(entry, dest.toString());
      
      if (destPath.endsWith(".jpg"))
      {
        com.itextpdf.text.Image image = com.itextpdf.text.Image.getInstance(destPath);

        if (doc == null)
          size = new Rectangle(image.getWidth(), image.getHeight());

        image.scaleToFit(size);
        image.setAbsolutePosition(0.0f, 0.0f);
        
        if (doc == null)
        {     
          System.out.printf("Creating output PDF of size %dx%d\n", (int)image.getWidth(), (int)image.getHeight());
          doc = new Document(size);          
          writer = PdfWriter.getInstance(doc, Files.newOutputStream(to, StandardOpenOption.CREATE));
          doc.open();

          canvas = writer.getDirectContent();

        }
        
        doc.newPage();
        canvas.addImage(image);
      }
    }
 
    doc.close();
    file.close();
  }


  public static void main(String[] args)
  {
    try
    {
      convertCBZtoPDF(Paths.get("F:\\Ebooks\\Comics\\Disney\\Topolino\\Topolino 3345- (Anno 2020)\\Topolino 3384 (Disney c2c By Roy).cbz"), Paths.get("output.pdf"));
      if (true)
        return;
      
      //scanForDigitalPDFsIntoFolder();
    }
    catch (Exception e)
    {
      e.printStackTrace();
      return;
    }
    
    try
    {
      ComicCollection classiciDisney1 = new ComicCollection("I Classici Disney - Serie I", 71);
      scanFolderForCollection(classiciDisney1, Paths.get("F:\\Ebooks\\Comics\\Disney\\I Classici Disney\\I Classici di Walt Disney - Serie I (1957-1976) (71 issues)"), false);
      
      ComicCollection superAlmanaccoPaperino1 = new ComicCollection("Super Almanacco Paperino - Serie I", 17);
      scanFolderForCollection(superAlmanaccoPaperino1, Paths.get("F:\\Ebooks\\Comics\\Disney\\Super Almanacco Paperino\\Super Almanacco Paperino - Serie I (1976-1980) (17 issues)"), false);
      
      ComicCollection superAlmanaccoPaperino2 = new ComicCollection("Super Almanacco Paperino - Serie II", 66);
      scanFolderForCollection(superAlmanaccoPaperino2, Paths.get("F:\\Ebooks\\Comics\\Disney\\Super Almanacco Paperino\\Super Almanacco Paperino - Serie II (1980-1985) (66 issues)"), false);   
      
      ComicCollection topolino = new ComicCollection("Topolino", 3400);
      scanFolderForCollection(topolino, Paths.get("F:\\Ebooks\\Comics\\Disney\\Topolino"), false);
      
      ComicCollection almanaccoTopolino = new ComicCollection("Almanacco Topolino", 336);
      scanFolderForCollection(almanaccoTopolino, Paths.get("F:\\Ebooks\\Comics\\Disney\\Almanacco Topolino (1957-1884) (336 issues)"), true);
      
      ComicCollection settimanaEnigmistica = new ComicCollection("La Settimana Enigmistica", 4600);
      scanFolderForCollection(settimanaEnigmistica, Paths.get("F:\\Ebooks\\Comics\\Vari\\Settimana Enigmistica"), false);

    }
    catch (Exception e)
    {
      e.printStackTrace();

    }
    
    //if (true)
    //  return;
    
    
    /*try
    {
      ComicArchive.extractImagesFromPdf(Paths.get("E:\\Downloads\\eMule\\Topolino 2905.pdf"), Paths.get("temp"), "out");
      ComicArchive.createComicArchiveFromFolder(Paths.get("temp"), Paths.get("Topolino 2905.cbz"), "", ComicArchive.ComicFormat.ZIP);
      Files.list(Paths.get("temp")).forEach(StreamException.rethrowConsumer(Files::delete));
      return;
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }*/
    
    final int[] dates = {
        10,   23,   37,   58,   82,  106,
       130,  154,  178,  202,  226,  266,
       319,  371,  423,  475,  527,  579,
       632,  684,  736,  788,  840,  893,
       945,  997, 1049, 1101, 1153, 1206,
      1258, 1310, 1362, 1414, 1466, 1519,
      1571, 1623, 1675, 1727, 1780, 1832,
      1884, 1936, 1988, 2040, 2092, 2145,
      2197, 2249, 2301, 2353, 2405, 2458,
      2510, 2562, 2614, 2666, 2718, 2771, 
      2823, 2875, 2927, 2979, 3032, 3084, 
      3136, 3188, 3241, 3293, 3345, 3398
        
    };
    
    IssueDate.Mapping dateMapper = i -> {
      for (int j = 0; j < dates.length; ++j)
        if (i < dates[j]) return IssueDate.of(1949+j);
      
      return IssueDate.of(2020);
    };
    
    List<ComicIssue> issues = new ArrayList<>();
    for (int i = 0; i < 3400; ++i)
      issues.add(new ComicIssue("Topolino", i + 1, dateMapper.map(i+1)));

    FormatGuesser guesser = new FormatGuesser();
    guesser.registerFormat(new byte[] { 0x50, 0x4b, 0x03, 0x04 }, ComicArchive.ComicFormat.ZIP);
    guesser.registerFormat(new byte[] { 0x52, 0x61, 0x72, 0x21 }, ComicArchive.ComicFormat.RAR);
    guesser.registerFormat(new byte[] { 0x25, 0x50, 0x44, 0x46 }, ComicArchive.ComicFormat.PDF);
    
    Path root = Paths.get("F:\\Ebooks\\Comics\\Disney\\Topolino");

    try
    {
      var list = Files.walk(root).filter(p -> Files.isRegularFile(p)).collect(Collectors.toList());

      Pattern regex = Pattern.compile("([a-zA-Z\\ \\-]+)\\ ([0-9]+).*");

      list.forEach(StreamException.rethrowConsumer(path -> {
        Matcher matcher = regex.matcher(path.getFileName().toString());

        if (matcher.find())
        {
          int number = Integer.valueOf(matcher.group(2));

          issues.get(number - 1).entry = new ComicArchive(path, Format.of("Unknown")/*guesser.guess(path).orElse(Format.of("Unknown"))*/);
          // System.out.printf("%s %s %s\n", matcher.group(1), matcher.group(2),
          // guesser.guess(path).map(Format::name).orElse("unknown"));
        }

        else
          System.out.printf("Unrecognized: %s\n", path.getFileName().toString());
      }));

      long owned = issues.stream().filter(c -> c.entry != null).count();

      System.out.printf("Owned %d of %d issues (%2.2f%%).\n", owned, issues.size(),
          (owned / (float) issues.size()) * 100);
      
      Map<Integer, Set<ComicIssue>> byYear = issues.stream().collect(Collectors.groupingBy(i -> i.date.year, Collectors.toSet()));
      
      List<Integer> completeYears = new ArrayList<>();
      for (Map.Entry<Integer, Set<ComicIssue>> entry : byYear.entrySet())
      {
        Stream<ComicIssue> issuesByYear = entry.getValue().stream();
        List<ComicIssue> missingByYear = issuesByYear.filter(is -> is.entry == null).collect(Collectors.toList());
        Collections.sort(missingByYear, (c,d) -> Integer.compare(c.number, d.number));
        
        if (missingByYear.size() > 0)
          System.out.println("Year "+entry.getKey()+", missing "+missingByYear.size()+": "+missingByYear.stream().map(ci -> Integer.toString(ci.number)).collect(Collectors.joining(", ")));
        else
          completeYears.add(entry.getKey());
      }
      
      System.out.println("Complete years: "+completeYears.stream().map(Object::toString).collect(Collectors.joining(", ")));
      

      ComicsTable table = new ComicsTable(null);
      var frame = UIUtils.buildFrame(table, "Issues");
      frame.panel().refresh(issues/*.stream().filter(i -> i.entry == null).collect(Collectors.toList())*/);
      frame.centerOnScreen();
      frame.exitOnClose();
      frame.setVisible(true);
    } catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}
