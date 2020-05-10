package com.github.jakz.hm.data.comics;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import org.imgscalr.Scalr;

import com.github.jakz.hm.formats.Format;

public class ComicArchive
{
  public static enum ComicFormat implements com.github.jakz.hm.formats.Format
  {
    ZIP, RAR
  };

  public final Path path;
  public final Format type;

  public ComicArchive(Path path, Format type)
  {
    this.path = path;
    this.type = type;
  }

  public static ImageIcon fetchThumbnail(ComicArchive archive) throws IOException
  {
    if (archive.type == ComicFormat.ZIP)
    {
      try (ZipFile file = new ZipFile(archive.path.toString()))
      {
        var entries = file.entries();

        while (entries.hasMoreElements())
        {
          ZipEntry entry = entries.nextElement();

          // add .jpeg?
          if (entry.getName().toLowerCase().endsWith(".jpg"))
          {
            InputStream is = file.getInputStream(entry);
            BufferedImage src = ImageIO.read(is);

            int w = src.getWidth(), h = src.getHeight();
            
            return new ImageIcon(Scalr.resize(src, Scalr.Method.ULTRA_QUALITY, (int)(w * (100.0f / h)), 100));

            
            //return thumbnail;
          }
        }
      }
    }

    return new ImageIcon();
  }
}
