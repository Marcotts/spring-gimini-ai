package info.bmdb.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateTimeTools {

    @Tool(description = "Cherche la date ou l'heure actuellement (présentation humaine)")
    public String chercheHeureouDate() {
        ZoneId zone = LocaleContextHolder.getTimeZone().toZoneId();
        ZonedDateTime now = LocalDateTime.now().atZone(zone);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm:ss (VV)", Locale.FRENCH);
        return "Nous sommes le " + now.format(fmt) + ".";
    }

    @Tool(description = "Get the current date and time in the user's timezone (human friendly)")
    public String getCurrentDateTime() {
        ZoneId zone = LocaleContextHolder.getTimeZone().toZoneId();
        ZonedDateTime now = LocalDateTime.now().atZone(zone);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' HH:mm:ss z (VV)", Locale.ENGLISH);
        String message = "Current date and time: " + now.format(fmt);
        System.out.println(message);
        return message;
    }

    @Tool(description = "Set a user alarm for the given time, provided in ISO-8601 format")
    public void setAlarm(String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("Alarm set for " + alarmTime);
    }
}
