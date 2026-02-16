package info.bmdb.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeTools {
    @Tool(description = "Cherche la date ou l'heure actuellement")
    String chercheHeureouDate() {
        //return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
        return LocalDateTime.now().toString();
    }

    @Tool(description = "Get the current date and time in the user's timezone")
    String getCurrentDateTime() {
        System.out.println("Voici la date et l'heure " + LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString());
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

    @Tool(description = "Set a user alarm for the given time, provided in ISO-8601 format")
    void setAlarm(String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("Alarm set for " + alarmTime);
    }
}
