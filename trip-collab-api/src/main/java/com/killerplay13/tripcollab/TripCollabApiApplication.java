package com.killerplay13.tripcollab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.killerplay13")
public class TripCollabApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TripCollabApiApplication.class, args);
	}

}
