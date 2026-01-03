package com.app.filecloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FilecloudApplication {

	public static void main(String[] args) {
		SpringApplication.run(FilecloudApplication.class, args);
	}

}
