package idpbackend.idp_backend.service;

import idpbackend.idp_backend.model.Environment;
import idpbackend.idp_backend.repository.EnvironmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// follows the image builds, and deploys each environment once its build succeeds
@Service
public class BuildWatcher {

    // GitHub usually lists a new run within seconds; past this, the dispatch was lost
    private static final Duration RUN_APPEAR_TIMEOUT = Duration.ofMinutes(10);

    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private EnvironmentService environmentService;
    @Autowired
    private BuildService buildService;

    // fixedDelay: the next check starts 20 seconds after the previous one ends, so a slow
    // deploy never overlaps with the next check on this replica
    @Scheduled(fixedDelay = 20_000, initialDelay = 20_000)
    public void checkBuilds() {
        for (Environment environment : environmentRepository.findByStatus(EnvironmentService.STATUS_BUILDING)) {
            try {
                check(environment);
            } catch (Exception e) {
                // the environment stays "building", so the next check tries again
                System.err.println("Suivi du build échoué pour " + environment.getEnvId() + " : " + e.getMessage());
            }
        }
    }

    private void check(Environment environment) throws Exception {
        Long id = environment.getId();
        String envId = environment.getEnvId();
        BuildService.BuildRun run = buildService.findRun(envId);

        if (run == null) {
            Instant started = environment.getBuildStartedAt();
            if (started != null && started.plus(RUN_APPEAR_TIMEOUT).isBefore(Instant.now())) {
                environmentRepository.changeStatus(id, EnvironmentService.STATUS_BUILDING, EnvironmentService.STATUS_BUILD_FAILED);
                System.err.println("Aucun build GitHub trouvé pour " + envId);
            }
            return;
        }

        if (!run.url().equals(environment.getBuildUrl())) {
            environmentRepository.setBuildUrl(id, run.url());
        }
        if (!run.finished()) {
            return;
        }
        if (!run.succeeded()) {
            environmentRepository.changeStatus(id, EnvironmentService.STATUS_BUILDING, EnvironmentService.STATUS_BUILD_FAILED);
            System.err.println("Build échoué pour " + envId + " : " + run.conclusion());
            return;
        }

        // claim it: if the other replica got here first, or the environment was destroyed, stop
        if (environmentRepository.changeStatus(id, EnvironmentService.STATUS_BUILDING, EnvironmentService.STATUS_DEPLOYING) == 0) {
            return;
        }

        try {
            environmentService.deployBuilt(environment);
        } catch (Exception e) {
            // hand it back, so the next check retries the deploy (both deploy steps are safe to repeat)
            environmentRepository.changeStatus(id, EnvironmentService.STATUS_DEPLOYING, EnvironmentService.STATUS_BUILDING);
            throw e;
        }

        if (environmentRepository.changeStatus(id, EnvironmentService.STATUS_DEPLOYING, EnvironmentService.STATUS_ACTIVE) == 0) {
            // the row disappeared while deploying: someone clicked Destroy meanwhile, so undo the deploy
            environmentService.rollback(envId);
            return;
        }
        System.out.println("Build terminé, environnement déployé : " + envId);
    }
}
