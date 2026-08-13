package uz.safecity.transportobserver

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * Smoke test: fails fast if the Spring context (security, JPA, Redis, AMQP,
 * WebSocket beans) doesn't wire up. Run against docker-compose infra — see
 * README.md.
 */
@SpringBootTest
class TransportObserverApplicationTests {

	@Test
	fun contextLoads() {
	}
}
