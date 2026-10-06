package isep.psoft.aisafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microsserviço Airports (WP#2A/WP#2B). A segurança (JWT, X-Service-Key, auditoria), o PeerClient, os
 * clientes de outros serviços e os health checks vêm auto-configurados da biblioteca common.
 * Guia: README.md na raiz. Exemplo completo: módulo flightroutes.
 */
@SpringBootApplication
public class AirportsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AirportsApplication.class, args);
	}

}
