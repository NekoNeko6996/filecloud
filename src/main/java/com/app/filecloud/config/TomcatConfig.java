package com.app.filecloud.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            // 1. Tăng giới hạn số lượng tham số URL (Query String / Form UrlEncoded)
            connector.setProperty("maxParameterCount", "100000");

            // 2. Tăng giới hạn dung lượng POST
            connector.setProperty("maxPostSize", "-1");
            
            // 3. Cho phép nuốt dữ liệu upload lớn
            connector.setProperty("maxSwallowSize", "-1");

            // --- [NEW FIX] ---
            // 4. Tăng giới hạn số lượng "Part" trong Multipart Request
            // Khắc phục lỗi: FileCountLimitExceededException
            // Mặc định Tomcat mới giới hạn cái này để chống DoS, cần mở rộng ra.
            connector.setProperty("maxPartCount", "100000");
        });
    }
}