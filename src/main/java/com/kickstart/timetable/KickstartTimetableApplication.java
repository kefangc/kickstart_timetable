package com.kickstart.timetable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KickstartTimetableApplication {

	public static void main(String[] args) {
		SpringApplication.run(KickstartTimetableApplication.class, args);
	}

}
