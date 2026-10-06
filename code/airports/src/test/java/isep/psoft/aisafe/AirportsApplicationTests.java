package isep.psoft.aisafe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Garante que o serviço arranca com a configuração partilhada (common).
@SpringBootTest(properties = "logging.file.name=target/test.log")
class AirportsApplicationTests {

	@Test
	void contextLoads() {
	}
}
