package com.github.jakz.hm.formats;

public interface Format
{
  String name();
  
  public static Format of(String name)
  {
    return new Format() {
      @Override public String name() { return name; }
    };
  }
  
  public static final String[] jpegExtensions = { "jpg", "jpeg" };
}
