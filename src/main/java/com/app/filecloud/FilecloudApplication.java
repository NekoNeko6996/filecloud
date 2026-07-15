package com.app.filecloud;

import javax.imageio.ImageIO;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FilecloudApplication {

	public static void main(String[] args) {
		ImageIO.scanForPlugins();
		SpringApplication.run(FilecloudApplication.class, args);
	}

}
