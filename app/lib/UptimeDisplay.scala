package lib

import org.joda.time.{Duration, DateTime}

object UptimeDisplay {
  def print(dt: java.util.Date): String = print(new DateTime(dt))

  def print(dt: DateTime): String = print(new Duration(dt, DateTime.now))

  def print(d: Duration): String = {
    if (d.getStandardDays > 0)
      pluralize(d.getStandardDays, "day")
    else if (d.getStandardHours > 0)
      pluralize(d.getStandardHours, "hour")
    else if (d.getStandardMinutes > 0)
      pluralize(d.getStandardMinutes, "min")
    else
      pluralize(d.getStandardSeconds, "sec")
  }

  private def pluralize(l: Long, baseString: String) = l match {
    case 1L => l + " " + baseString
    case _ => l + " " + baseString + "s"
  }
}