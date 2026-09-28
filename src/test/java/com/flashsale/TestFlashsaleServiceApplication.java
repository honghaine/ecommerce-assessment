package com.flashsale;

import org.springframework.boot.SpringApplication;

public class TestFlashsaleServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlashsaleServiceApplication::main).with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("test").run(args);
	}

}
