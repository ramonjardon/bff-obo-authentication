package dev.ramonjardon.bff.config;

import java.util.List;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;

import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods=false)
public class JacksonConfig {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Primary
    public JsonMapper.Builder jsonMapperBuilder(
            List<JsonMapperBuilderCustomizer> customizers) {

        // JsonFactory con el pool configurado debe pasarse
        // en el constructor del builder, no después
        JsonFactory factory = JsonFactory.builder()
            .recyclerPool(JsonRecyclerPools.nonRecyclingPool())
            .build();

        // Creamos el builder con la factory personalizada
        JsonMapper.Builder builder = JsonMapper.builder(factory);

        // Aplicamos manualmente todos los customizers de Boot 4
        // (los que leen application.yml, registran módulos, etc.)
        // Esto es lo que hace JacksonAutoConfiguration internamente
        for (JsonMapperBuilderCustomizer customizer : customizers) {
            customizer.customize(builder);
        }

        return builder;
    }
}
