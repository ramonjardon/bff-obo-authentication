package dev.ramonjardon.bff.config;

import java.util.TimeZone;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TimeZoneConfig {
@EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent() {
        // Establecemos UTC como estándar global de la aplicación
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        
        
    
    }
}
