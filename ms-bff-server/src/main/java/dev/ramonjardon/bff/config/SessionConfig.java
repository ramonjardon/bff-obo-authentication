package dev.ramonjardon.bff.config;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;


@Configuration(proxyBeanMethods=false)
public class SessionConfig implements BeanClassLoaderAware {
    private ClassLoader loader;
    
@Bean
public RedisSerializer<Object> springSessionDefaultRedisSerializer(JsonMapper.Builder jsonMapperBuilder) {
    // En Spring Security 7, registrar los módulos es suficiente 
    // para que el mapper sepa cómo tratar el tipado de los objetos de seguridad.
    ObjectMapper sessionMapper = jsonMapperBuilder
            .addModules(SecurityJacksonModules.getModules(this.loader))
            .build();

    return new JacksonJsonRedisSerializer<>(sessionMapper, Object.class);
}
    
    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
       this.loader = classLoader;
    }

}
