package idpbackend.idp_backend.service;


import idpbackend.idp_backend.dto.CreateEnvironmentReq;
import idpbackend.idp_backend.dto.EnvironmentResponse;
import idpbackend.idp_backend.dto.ServiceConfig;
import idpbackend.idp_backend.model.Environment;
import idpbackend.idp_backend.repository.EnvironmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class EnvironmentService {
    private static final int DEFAULT_TTL_HOURS = 24;
    private static final int MIN_TTL_HOURS = 1;
    private static final int MAX_TTL_HOURS = 72;

    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private GitOpsService gitOpsService;
    @Autowired
    private ArgoCdService argoCdService;
    EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }


    public EnvironmentResponse createEnvironment(CreateEnvironmentReq req, String createdBy) throws Exception {

        String envId = generateEnvId(req);

        // Generate the template using the TemplateService
        Map<String,String> generatedManifests = templateService.generateTemplate(envId, req);

        // push to git repo
        gitOpsService.commitAndPushManifests(envId,generatedManifests);

        try {
            // register the environment with ArgoCD so it syncs the manifests to the cluster
            // the namespace must match the one rendered into the manifests, otherwise ArgoCD refuses to sync
            argoCdService.createApplication(envId, envId);

            return toResponse(environmentRepository.save(buildEnvironment(envId, req, createdBy)));
        } catch (Exception e) {
            // Git and ArgoCD can't join a database transaction, so undo them by hand:
            // a failed create must not leave a running environment nobody can see in the portal
            rollback(envId);
            throw e;
        }
    }

    // best effort: each step runs even if the other fails, and the original error is the one reported
    private void rollback(String envId) {
        try {
            argoCdService.deleteApplication(envId);
        } catch (Exception e) {
            System.err.println("Rollback ArgoCD échoué pour " + envId + " : " + e.getMessage());
        }
        try {
            gitOpsService.deleteEnvironment(envId);
        } catch (Exception e) {
            System.err.println("Rollback Git échoué pour " + envId + " : " + e.getMessage());
        }
    }

    // never trust the client: a missing TTL gets the default, and the rest stay within the limits
    private int resolveTtlHours(Integer requested) {
        int hours = requested == null ? DEFAULT_TTL_HOURS : requested;
        return Math.max(MIN_TTL_HOURS, Math.min(MAX_TTL_HOURS, hours));
    }

    private Environment buildEnvironment(String envId, CreateEnvironmentReq req, String createdBy) {
        Environment environment = new Environment();
        environment.setEnvId(envId);
        environment.setTeam(req.getTeam());
        environment.setType(req.getType());
        environment.setStatus("active");
        environment.setCreatedBy(createdBy);
        environment.setCreatedAt(Instant.now());
        environment.setExpiresAt(Instant.now().plus(resolveTtlHours(req.getTtlHours()), ChronoUnit.HOURS));

        if (req.getServices() != null) {
            for (ServiceConfig config : req.getServices()) {
                // fully qualified: the simple name Service is taken by Spring's @Service here
                var service = new idpbackend.idp_backend.model.Service();
                service.setServiceName(config.getServiceName());
                service.setPort(config.getPort());
                service.setPath(config.getPath());
                service.setImage(config.getImage());
                service.setHealthPath(config.getHealthPath());
                service.getEnv().putAll(config.getEnv());
                environment.addService(service);
            }
        }

        return environment;
    }

    public List<EnvironmentResponse> listAll() {
        return environmentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public EnvironmentResponse getEnvironmentByEnvId(String envId) {
        Environment environment = environmentRepository.findByEnvId(envId);
        return environment == null ? null : toResponse(environment);
    }

    // live sync/health from ArgoCD; also saves the environment's URL the first time ArgoCD
    // reports one, since the Ingress only gets its address a little after creation
    public ArgoCdService.ApplicationStatus refreshStatus(String envId) {
        Environment environment = environmentRepository.findByEnvId(envId);
        if (environment == null) {
            return null;
        }

        ArgoCdService.ApplicationStatus status = argoCdService.getApplicationStatus(envId);
        if (status.url() != null && !status.url().equals(environment.getUrl())) {
            environment.setUrl(status.url());
            environmentRepository.save(environment);
        }
        return status;
    }

    public void destroy(String envId) throws Exception {
        Environment environment = environmentRepository.findByEnvId(envId);
        if (environment != null) {
            // cascade delete removes the workloads from the cluster, not just the ArgoCD app
            argoCdService.deleteApplication(envId);
            gitOpsService.deleteEnvironment(envId);
            // last on purpose: if a step above fails the row survives, and both steps are
            // safe to repeat, so a retry or the next cleanup run can finish the job
            // orphanRemoval on the services collection clears the child rows too
            environmentRepository.delete(environment);
        }
    }

    private EnvironmentResponse toResponse(Environment environment) {
        List<ServiceConfig> services = environment.getServices()
                .stream()
                .map(service -> new ServiceConfig(
                        service.getServiceName(),
                        service.getPort(),
                        service.getPath(),
                        service.getImage(),
                        service.getHealthPath(),
                        service.getEnv()))
                .toList();

        return new EnvironmentResponse(
                environment.getId(),
                environment.getEnvId(),
                environment.getTeam(),
                environment.getType(),
                environment.getStatus(),
                environment.getUrl(),
                environment.getCreatedBy(),
                environment.getCreatedAt(),
                environment.getLastActiveAt(),
                environment.getExpiresAt(),
                services);
    }

    // the envId becomes a Kubernetes namespace and an ArgoCD app name, so it must be a
    // valid RFC 1123 label: lowercase alphanumerics and dashes only
    private String generateEnvId(CreateEnvironmentReq req) {
        String base = (req.getType() + "-" + req.getTeam())
                .toLowerCase()
                .replaceAll("[^a-z0-9-]", "-");
        return base + "-" + Long.toString(Instant.now().toEpochMilli(), 36);
    }
}
