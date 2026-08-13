package com.jobtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class JobTrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobTrackApplication.class, args);
	}

}
