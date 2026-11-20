package idpbackend.idp_backend.service;


import idpbackend.idp_backend.dto.CreateEnvironmentReq;
import idpbackend.idp_backend.dto.EnvironmentResponse;
import idpbackend.idp_backend.dto.ServiceConfig;
import idpbackend.idp_backend.model.Environment;
import idpbackend.idp_backend.repository.EnvironmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class EnvironmentService {
    private static final int DEFAULT_TTL_HOURS = 24;
    private static final int MIN_TTL_HOURS = 1;
    private static final int MAX_TTL_HOURS = 72;

    // building: the image build runs on GitHub Actions; deploying: BuildWatcher is deploying it;
    // active: deployed (environments with ready-made images start here); build_failed: stays until destroyed or expired
    public static final String STATUS_BUILDING = "building";
    public static final String STATUS_DEPLOYING = "deploying";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_BUILD_FAILED = "build_failed";

    // these values end up in a GitHub Actions run, so only plain, safe characters get through.
    // Public GitHub repositories only: private ones would need the developer's own token
    private static final Pattern GIT_URL = Pattern.compile("^https://github\\.com/[\\w.-]+/[\\w.-]+$");
    private static final Pattern GIT_REF = Pattern.compile("^\\w[\\w./-]{0,99}$");
    private static final Pattern DOCKERFILE_PATH = Pattern.compile("^\\w[\\w./-]{0,199}$");
    // it becomes part of the image tag
    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-z0-9-]{1,40}$");

    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private GitOpsService gitOpsService;
    @Autowired
    private ArgoCdService argoCdService;
    @Autowired
    private BuildService buildService;
    EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }


    public EnvironmentResponse createEnvironment(CreateEnvironmentReq req, String createdBy) throws Exception {

        String envId = generateEnvId(req);

        List<ServiceConfig> services = req.getServices() == null ? List.of() : req.getServices();
        validateSources(services);
        if (services.stream().anyMatch(ServiceConfig::builtFromGit)) {
            return startBuild(envId, req, createdBy);
        }

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

    // the image doesn't exist yet: save the environment as "building" and start the build.
    // BuildWatcher deploys it when the build succeeds, through deployBuilt below
    private EnvironmentResponse startBuild(String envId, CreateEnvironmentReq req, String createdBy) {
        for (ServiceConfig service : req.getServices()) {
            if (service.builtFromGit()) {
                service.setImage(buildService.imageFor(envId, service.getServiceName()));
                service.setGitRef(BuildService.gitRefOf(service));
                service.setDockerfilePath(BuildService.dockerfileOf(service));
            }
        }

        Environment environment = buildEnvironment(envId, req, createdBy);
        environment.setStatus(STATUS_BUILDING);
        environment.setBuildStartedAt(Instant.now());
        Environment saved = environmentRepository.save(environment);

        try {
            buildService.startBuild(envId, req.getServices());
        } catch (RuntimeException e) {
            // nothing was built or pushed to Git yet, so removing the row undoes everything
            environmentRepository.delete(saved);
            throw e;
        }
        return toResponse(saved);
    }

    // the same steps as createEnvironment, from the saved environment; each one is safe to repeat
    public void deployBuilt(Environment environment) throws Exception {
        String envId = environment.getEnvId();
        CreateEnvironmentReq req = new CreateEnvironmentReq(environment.getTeam(), environment.getType(),
                environment.getServices().stream().map(this::toConfig).toList(), null);

        gitOpsService.commitAndPushManifests(envId, templateService.generateTemplate(envId, req));
        argoCdService.createApplication(envId, envId);
    }

    // each service needs exactly one source: a ready-made image, or a Git repository to build
    private void validateSources(List<ServiceConfig> services) {
        for (ServiceConfig service : services) {
            boolean hasImage = service.getImage() != null && !service.getImage().isBlank();
            if (hasImage == service.builtFromGit()) {
                throw badRequest("Service " + service.getServiceName() + " : donnez une image ou un dépôt Git, pas les deux");
            }
            if (!service.builtFromGit()) {
                continue;
            }
            if (service.getServiceName() == null || !SERVICE_NAME.matcher(service.getServiceName()).matches()) {
                throw badRequest("Nom de service invalide : " + service.getServiceName());
            }
            if (!GIT_URL.matcher(service.getGitUrl()).matches()) {
                throw badRequest("Dépôt Git invalide, format attendu https://github.com/owner/repo : " + service.getGitUrl());
            }
            String ref = BuildService.gitRefOf(service);
            String dockerfile = BuildService.dockerfileOf(service);
            if (!GIT_REF.matcher(ref).matches() || ref.contains("..")) {
                throw badRequest("Branche invalide : " + ref);
            }
            if (!DOCKERFILE_PATH.matcher(dockerfile).matches() || dockerfile.contains("..")) {
                throw badRequest("Chemin du Dockerfile invalide : " + dockerfile);
            }
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    // best effort: each step runs even if the other fails, and the original error is the one reported
    void rollback(String envId) {
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
        environment.setStatus(STATUS_ACTIVE);
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
                service.setGitUrl(config.getGitUrl());
                service.setGitRef(config.getGitRef());
                service.setDockerfilePath(config.getDockerfilePath());
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

        // no ArgoCD application exists until the image is built
        if (STATUS_BUILDING.equals(environment.getStatus()) || STATUS_BUILD_FAILED.equals(environment.getStatus())) {
            return new ArgoCdService.ApplicationStatus("Unknown", "Unknown", null);
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
                .map(this::toConfig)
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
                services,
                environment.getBuildUrl());
    }

    private ServiceConfig toConfig(idpbackend.idp_backend.model.Service service) {
        return new ServiceConfig(
                service.getServiceName(),
                service.getPort(),
                service.getPath(),
                service.getImage(),
                service.getHealthPath(),
                service.getEnv(),
                service.getGitUrl(),
                service.getGitRef(),
                service.getDockerfilePath());
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
