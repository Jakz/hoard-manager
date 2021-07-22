package com.github.jakz.hm.data.comics;

public class IssueDate
{
  public final int year;
  public final int month;
  public final int day;
  
  private IssueDate(int year, int month, int day)
  {
    this.year = year;
    this.month = month;
    this.day = day;
  }
  
  public static final IssueDate of(int year)
  {
    return new IssueDate(year, -1, -1);
  }
  
  public static final IssueDate unknown()
  {
    return new IssueDate(-1, -1, -1);
  }
  
  @FunctionalInterface
  public static interface Mapping
  {
    IssueDate map(int index);
  }
  
  @Override
  public String toString()
  {
    if (year == -1 && month == -1 && day == -1)
      return "Unknown";
    else
      return Integer.toString(year);
  }
}
