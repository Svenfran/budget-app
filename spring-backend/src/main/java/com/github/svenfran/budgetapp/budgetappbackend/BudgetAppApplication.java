package com.github.svenfran.budgetapp.budgetappbackend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BudgetAppApplication {

	public static void main(String[] args) {
		useUtcAsDefaultTimeZone();
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(BudgetAppApplication.class, args);
	}

	/**
	 * DIV-81: Die JVM läuft unabhängig vom Host in UTC. Kaufdaten (DATE-Spalten) werden über {@code DateUtils} in der
	 * Default-Zone umgerechnet, Mitgliedschaftsgrenzen und "heute" für wiederkehrende Ausgaben gelten als UTC-Tag.
	 * Prod lief bereits in GMT (geprüft 2026-11-22); Dev und Tests (Surefire/Failsafe {@code -Duser.timezone=UTC})
	 * verhalten sich damit wie Prod.
	 */
	static void useUtcAsDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

}
