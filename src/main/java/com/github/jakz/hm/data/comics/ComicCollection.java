package com.github.jakz.hm.data.comics;

public class ComicCollection
{
  String name;
  int issues;
  
  public ComicCollection(String name, int issues)
  {
    this.name = name;
    this.issues = issues;
  }
  
  public String name() { return name; }
  public int size() { return issues; }
}
