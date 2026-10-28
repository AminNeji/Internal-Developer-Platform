package idpbackend.idp_backend.service;


import idpbackend.idp_backend.dto.CreateEnvironmentReq;
import idpbackend.idp_backend.dto.EnvironmentResponse;
import idpbackend.idp_backend.dto.ServiceConfig;
import idpbackend.idp_backend.model.Environment;
import idpbackend.idp_backend.repository.EnvironmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class EnvironmentService {
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


        // register the environment with ArgoCD so it syncs the manifests to the cluster
        // the namespace must match the one rendered into the manifests, otherwise ArgoCD refuses to sync
        argoCdService.createApplication(envId, envId);

        Environment environment = new Environment();
        environment.setEnvId(envId);
        environment.setTeam(req.getTeam());
        environment.setType(req.getType());
        environment.setStatus("active");
        environment.setCreatedBy(createdBy);
        environment.setCreatedAt(Instant.now());

        if (req.getServices() != null) {
            for (ServiceConfig config : req.getServices()) {
                // fully qualified: the simple name Service is taken by Spring's @Service here
                var service = new idpbackend.idp_backend.model.Service();
                service.setServiceName(config.getServiceName());
                service.setPort(config.getPort());
                service.setPath(config.getPath());
                service.setImage(config.getImage());
                environment.addService(service);
            }
        }

        return toResponse(environmentRepository.save(environment));
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
    // reports one, since the load balancer address only appears a minute or two after creation
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

    public void destroy(String envId) {
        Environment environment = environmentRepository.findByEnvId(envId);
        if (environment != null) {
            // cascade delete removes the workloads from the cluster, not just the ArgoCD app
            argoCdService.deleteApplication(envId);
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
                        service.getImage()))
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
