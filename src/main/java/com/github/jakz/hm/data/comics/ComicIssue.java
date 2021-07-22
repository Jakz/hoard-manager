package com.github.jakz.hm.data.comics;

import java.nio.file.Path;

public class ComicIssue
{
  public final String title;
  public final int number;
  public final IssueDate date;
  public ComicArchive entry;
  
  public ComicIssue(String title, int number)
  {
    this.title = title;
    this.number = number;
    this.date = IssueDate.unknown();
  }
  
  public ComicIssue(String title, int number, IssueDate date)
  {
    this.title = title;
    this.number = number;
    this.date = date;
  }
}
