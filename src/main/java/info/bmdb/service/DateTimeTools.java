package info.bmdb.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateTimeTools {

    @Tool(description = "FR: Donne la date et l'heure actuelles (présentation humaine). EN: Get the current date and time (human friendly). Paramètre/Parameter (optional): 'locale' (e.g. 'fr-FR', 'en-US').")
    public String dateTimeNow(String locale) {
        ZoneId zone = LocaleContextHolder.getTimeZone().toZoneId();
        Locale loc = (locale == null || locale.isBlank()) ? LocaleContextHolder.getLocale() : Locale.forLanguageTag(locale);
        ZonedDateTime now = LocalDateTime.now().atZone(zone);
        boolean fr = loc != null && "fr".equalsIgnoreCase(loc.getLanguage());
        DateTimeFormatter fmt = fr
                ? DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm:ss (VV)", Locale.FRENCH)
                : DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' HH:mm:ss z (VV)", Locale.ENGLISH);
        return fr ? ("Nous sommes le " + now.format(fmt) + ".") : ("Current date and time: " + now.format(fmt));
    }

    @Tool(description = "Set a user alarm for the given time, provided in ISO-8601 format")
    public void setAlarm(String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("Alarm set for " + alarmTime);
    }
}
