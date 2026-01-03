package com.app.filecloud.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 1. Current language (Save in Session)
    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver slr = new SessionLocaleResolver();
        // Default language vi_VN
        slr.setDefaultLocale(new Locale("vi"));
        return slr;
    }

    // 2. Interceptor
    // EX: ?lang=en or ?lang=vi
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang"); // Parameter name
        return lci;
    }

    // 3. Register Interceptor to System
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
    
    @Value("${app.storage.root:uploads}")
    private String rootUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map đường dẫn URL /avatars/** vào thư mục vật lý uploads/avatars/
        Path uploadDir = Paths.get(rootUploadDir);
        String uploadPath = uploadDir.toFile().getAbsolutePath();

        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:/" + uploadPath + "/avatars/");
    }
}