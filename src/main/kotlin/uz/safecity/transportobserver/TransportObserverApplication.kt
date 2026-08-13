package uz.safecity.transportobserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class TransportObserverApplication

fun main(args: Array<String>) {
	runApplication<TransportObserverApplication>(*args)
}
