package com.github.jakz.hm.data.comics;

import java.nio.file.Path;

public class ComicIssue
{
  public final String title;
  public final int number;
  public ComicArchive entry;
  
  public ComicIssue(String title, int number)
  {
    this.title = title;
    this.number = number;
  }
}
