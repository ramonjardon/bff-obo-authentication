package dev.ramonjardon.bff.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods=false)
public class AsyncConfig implements WebMvcConfigurer{

     @Value("${app.async.timeout:5000}")
    private long asyncTimeout;

    // Spring inyecta aquí el bean ya decorado por Micrometer
    // (con propagación de tracing, métricas, etc.)
    // Es el mismo bean que registra applicationTaskExecutor()
    // pero pasado por los BeanPostProcessors de Micrometer
    private final AsyncTaskExecutor executor;

    public AsyncConfig(
            @Lazy AsyncTaskExecutor applicationTaskExecutor) {
        this.executor = applicationTaskExecutor;
    }

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
            .name("bff-vt-", 0)
            .factory();
        return new TaskExecutorAdapter(
            Executors.newThreadPerTaskExecutor(factory));
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(asyncTimeout);
        // El executor ya tiene tracing propagado por Micrometer
        configurer.setTaskExecutor(executor);
    }
}
