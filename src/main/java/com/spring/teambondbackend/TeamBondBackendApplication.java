package com.spring.teambondbackend;

import com.spring.teambondbackend.config.LoadEnvConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TeamBondBackendApplication {

	public static void main(String[] args) {
		LoadEnvConfig.load();
		SpringApplication.run(TeamBondBackendApplication.class, args);
	}

}
