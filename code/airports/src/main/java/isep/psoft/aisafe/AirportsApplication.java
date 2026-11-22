package isep.psoft.aisafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Arranque do serviço
@SpringBootApplication
public class AirportsApplication {
	// Ponto de entrada
	public static void main(String[] args) {
		SpringApplication.run(AirportsApplication.class, args);
	}
}
