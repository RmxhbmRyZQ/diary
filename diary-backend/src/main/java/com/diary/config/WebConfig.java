package com.diary.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "https://diary.example.com")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer beijingTimeCustomizer() {
        return builder -> {
            builder.timeZone("Asia/Shanghai");

            SimpleModule module = new SimpleModule();
            module.addSerializer(Instant.class, new JsonSerializer<Instant>() {
                @Override
                public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                    String formatted = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            .withZone(BEIJING)
                            .format(value);
                    gen.writeString(formatted);
                }
            });
            builder.modulesToInstall(module);
        };
    }
}
