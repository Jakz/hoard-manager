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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.github.jakz.hm.data.comics.ComicArchive;
import com.github.jakz.hm.data.comics.ComicIssue;
import com.github.jakz.hm.formats.Format;
import com.github.jakz.hm.formats.FormatGuesser;
import com.pixbits.lib.functional.StreamException;
import com.pixbits.lib.ui.UIUtils;
import com.pixbits.lib.ui.table.ColumnSpec;
import com.pixbits.lib.ui.table.DataSource;
import com.pixbits.lib.ui.table.FilterableDataSource;
import com.pixbits.lib.ui.table.TableModel;
import com.pixbits.lib.ui.table.renderers.LambdaLabelTableRenderer;
import com.pixbits.lib.util.IconCache;

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
      model.addColumn(new ColumnSpec<ComicIssue, String>("Name", String.class, m -> m.title).setWidth(100));
      model.addColumn(new ColumnSpec<>("Type", String.class, m -> m.entry != null ? m.entry.type.name() : ""));
      model.addColumn(
          new ColumnSpec<>("Path", String.class, m -> m.entry != null ? m.entry.path.getFileName().toString() : ""));

      model.setRowHeight(120);
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

  public static void main(String[] args)
  {
    List<ComicIssue> issues = new ArrayList<>();
    for (int i = 0; i < 3363; ++i)
      issues.add(new ComicIssue("Topolino", i + 1));

    FormatGuesser guesser = new FormatGuesser();
    guesser.registerFormat(new byte[] { 0x50, 0x4b, 0x03, 0x04 }, ComicArchive.ComicFormat.ZIP);
    guesser.registerFormat(new byte[] { 0x52, 0x61, 0x72, 0x21 }, ComicArchive.ComicFormat.RAR);

    Path root = Paths.get("F:\\Ebooks\\Comics\\Disney\\Topolino");

    try
    {
      var list = Files.walk(root).filter(p -> Files.isRegularFile(p)).collect(Collectors.toList());

      Pattern regex = Pattern.compile("([a-zA-Z\\ ]+)\\ ([0-9]+).*");

      list.forEach(StreamException.rethrowConsumer(path -> {
        Matcher matcher = regex.matcher(path.getFileName().toString());

        if (matcher.find())
        {
          int number = Integer.valueOf(matcher.group(2));

          issues.get(number - 1).entry = new ComicArchive(path, guesser.guess(path).orElse(Format.of("Unknown")));
          // System.out.printf("%s %s %s\n", matcher.group(1), matcher.group(2),
          // guesser.guess(path).map(Format::name).orElse("unknown"));
        }

        else
          System.out.printf("Unrecognized: %s\n", path.getFileName().toString());
      }));

      long owned = issues.stream().filter(c -> c.entry != null).count();

      System.out.printf("Owned %d of %d issues (%2.2f%%).", owned, issues.size(),
          (owned / (float) issues.size()) * 100);

      ComicsTable table = new ComicsTable(null);
      var frame = UIUtils.buildFrame(table, "Issues");
      frame.panel().refresh(issues);
      frame.centerOnScreen();
      frame.exitOnClose();
      frame.setVisible(true);
    } catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}
