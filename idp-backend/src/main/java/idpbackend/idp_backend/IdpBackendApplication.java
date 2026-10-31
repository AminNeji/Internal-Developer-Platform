package idpbackend.idp_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdpBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdpBackendApplication.class, args);
	}

}
