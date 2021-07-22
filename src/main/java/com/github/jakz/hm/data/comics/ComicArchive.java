package com.github.jakz.hm.data.comics;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import org.imgscalr.Scalr;

import com.github.jakz.hm.formats.Format;
import com.github.junrar.Archive;
import com.github.junrar.VolumeManager;
import com.github.junrar.exception.RarException;
import com.github.junrar.impl.FileVolumeManager;
import com.github.junrar.rarfile.FileHeader;

public class ComicArchive
{
  public static enum ComicFormat implements com.github.jakz.hm.formats.Format
  {
    ZIP, RAR, PDF
  };

  public final Path path;
  public final Format type;

  public ComicArchive(Path path, Format type)
  {
    this.path = path;
    this.type = type;
  }
  
  private static String cachedThumbnailPath(ComicArchive archive)
  {
    String cachedName = String.format("cache/%8X.jpg", archive.path.getFileName().toString().hashCode());
    return cachedName;
  }

  public static ImageIcon fetchThumbnail(ComicArchive archive) throws IOException, RarException
  {
    //if (true)
    //  return new ImageIcon();

    
    File cachedName = new File(cachedThumbnailPath(archive));
    
    if (Files.exists(cachedName.toPath()))
    {
      return new ImageIcon(ImageIO.read(cachedName));
    }
    
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
            
            BufferedImage image = Scalr.resize(src, Scalr.Method.ULTRA_QUALITY, (int)(w * (100.0f / h)), 100);
            
            ImageIO.write(image, "jpg", cachedName);
            return new ImageIcon(image);
            
            //return thumbnail;
          }
        }
      }
    }
    else if (archive.type == ComicFormat.RAR)
    {
      try (Archive rar = new Archive(new FileVolumeManager(archive.path.toFile())))
      {     
        for (FileHeader header : rar)
        {
          if (header.getFileNameString().toLowerCase().endsWith(".jpg"))
          {
            InputStream is = rar.getInputStream(header);
            BufferedImage src = ImageIO.read(is);
    
            int w = src.getWidth(), h = src.getHeight();
            
            BufferedImage image = Scalr.resize(src, Scalr.Method.ULTRA_QUALITY, (int)(w * (100.0f / h)), 100);
            ImageIO.write(image, "jpg", cachedName);
            
            return new ImageIcon(image);
          }
        }
      }
      catch (RarException e)
      {
        return new ImageIcon();
      }
    }

    return new ImageIcon();
  }
  
  
  public static void extractImagesFromPdf(Path pdf, Path dest, String prefix) throws IOException
  {
    ProcessBuilder pb = new ProcessBuilder();
    
    Files.createDirectories(Paths.get("temp"));
    
    pb.command("tools/pdfimages.exe", "-j", pdf.toString(), dest.resolve(prefix).toString());
    
    var process = pb.start();
    
    try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) 
    {
      String line;

      while ((line = reader.readLine()) != null)
      {
        System.out.println(line);
      }
    }
  }
  
  public static void createComicArchiveFromFolder(Path src, Path dest, String prefix, ComicFormat format) throws IOException
  {
    byte[] buffer = new byte[1024*1024];
    int read = -1;
    
    List<Path> files = Files.walk(src).filter(p -> p.toString().endsWith(".jpg")).collect(Collectors.toList());
     
     try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)))
     {
       for (Path file : files)
       {
         ZipEntry entry = new ZipEntry(file.getFileName().toString());
         
         zos.putNextEntry(entry);
         
         try (InputStream fis = new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ)))
         {
           while ((read = fis.read(buffer)) >= 0)
           {
             zos.write(buffer, 0, read);
           }
         } 
       }
     }
  }
  
  public static boolean isPDFDigital(Path pdf) throws IOException
  {
    ProcessBuilder pb = new ProcessBuilder();
        
    pb.command("tools/pdfinfo.exe", pdf.toString());
    
    var process = pb.start();
    
    try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) 
    {
      String line;

      while ((line = reader.readLine()) != null)
      {
        if (line.startsWith("Page size"))
          return line.contains("394.016");
      }
    }
    
    return false;
  }
 
}
