package com.plateforme.covoiturage;

import org.springframework.boot.SpringApplication;

public class TestCovoiturageApplication {

	public static void main(String[] args) {
		SpringApplication.from(CovoiturageApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
