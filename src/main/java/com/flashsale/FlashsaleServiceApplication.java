package com.flashsale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FlashsaleServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlashsaleServiceApplication.class, args);
	}

}
