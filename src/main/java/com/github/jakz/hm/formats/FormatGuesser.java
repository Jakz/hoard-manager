package com.github.jakz.hm.formats;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FormatGuesser
{
  class FormatSpec
  {
    byte[] magic;
    Format format;
  }
  
  private final List<FormatSpec> specs = new ArrayList<>();
  
  
  public void registerFormat(byte[] magic, Format format)
  {
    FormatSpec spec = new FormatSpec();
    spec.magic = magic;
    spec.format = format;
    specs.add(spec);
  }
  
  public Optional<Format> guess(Path path) throws IOException
  {
    ByteBuffer buffer = ByteBuffer.allocate(16);
    
    try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ))
    {
      int read = channel.read(buffer);
          
      return specs.stream().filter(spec -> read >= spec.magic.length).filter(spec -> {
        for (int i = 0; i < spec.magic.length; ++i)
          if (spec.magic[i] != buffer.get(i))
            return false;

        return true;
      }).findFirst().map(spec -> spec.format);
    }
  }
  
}
