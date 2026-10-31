package idpbackend.idp_backend.service;

import idpbackend.idp_backend.model.Environment;
import idpbackend.idp_backend.repository.EnvironmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

// destroys environments whose TTL has run out, so forgotten ones stop costing money
@Service
public class CleanupService {

    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private EnvironmentService environmentService;

    // every 5 minutes (the value is in milliseconds)
    @Scheduled(fixedRate = 300_000)
    public void destroyExpired() {
        for (Environment environment : environmentRepository.findByExpiresAtBefore(Instant.now())) {
            String envId = environment.getEnvId();
            try {
                environmentService.destroy(envId);
                System.out.println("Environnement expiré supprimé : " + envId);
            } catch (Exception e) {
                // one failure must not stop the others, and the row survives so the next run retries.
                // both backend replicas run this job, so this also covers losing the race to the other one
                System.err.println("Suppression échouée pour " + envId + " : " + e.getMessage());
            }
        }
    }
}
