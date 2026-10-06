package isep.psoft.aisafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microsserviço Maintenance (WP#4A/WP#4B). A segurança (JWT, X-Service-Key, auditoria), o PeerClient, os
 * clientes de outros serviços e os health checks vêm auto-configurados da biblioteca common.
 * Guia: README.md na raiz. Exemplo completo: módulo flightroutes.
 */
@SpringBootApplication
public class MaintenanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MaintenanceApplication.class, args);
	}

}
