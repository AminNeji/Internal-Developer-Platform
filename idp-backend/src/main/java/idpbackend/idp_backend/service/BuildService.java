package idpbackend.idp_backend.service;

import idpbackend.idp_backend.dto.ServiceConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// builds images from developers' Git repositories, using the build-env-image.yml
// GitHub Actions workflow in the platform's own repository
@Service
public class BuildService {

    static final String WORKFLOW_FILE = "build-env-image.yml";
    // the branch the platform works on, as in GitOpsService and ArgoCdService
    private static final String PLATFORM_BRANCH = "dev";
    private static final String DEFAULT_GIT_REF = "main";
    private static final String DEFAULT_DOCKERFILE = "Dockerfile";

    private final RestClient github;
    private final String owner;
    private final String repo;
    private final String imageRepository;
    private final JsonMapper json = new JsonMapper();

    public BuildService(@Value("${git.repo.url}") String gitRepoUrl,
                        @Value("${git.password}") String gitToken,
                        @Value("${idp.build.image-repository:}") String imageRepository) {
        // https://github.com/owner/repo.git -> owner, repo
        String[] parts = gitRepoUrl.replaceFirst("^https://github\\.com/", "")
                .replaceFirst("\\.git$", "")
                .split("/");
        this.owner = parts[0];
        this.repo = parts[1];
        this.imageRepository = imageRepository;
        this.github = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gitToken)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    // the image the build will push; known before the build starts, so it can be saved right away
    public String imageFor(String envId, String serviceName) {
        if (imageRepository == null || imageRepository.isBlank()) {
            throw new IllegalStateException("idp.build.image-repository n'est pas configuré");
        }
        return imageRepository + ":" + envId + "-" + serviceName;
    }

    public static String gitRefOf(ServiceConfig service) {
        return blank(service.getGitRef()) ? DEFAULT_GIT_REF : service.getGitRef();
    }

    public static String dockerfileOf(ServiceConfig service) {
        return blank(service.getDockerfilePath()) ? DEFAULT_DOCKERFILE : service.getDockerfilePath();
    }

    // one workflow run per environment; its matrix builds each Git service in parallel
    public void startBuild(String envId, List<ServiceConfig> services) {
        List<Map<String, String>> matrix = services.stream()
                .filter(ServiceConfig::builtFromGit)
                .map(service -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("name", service.getServiceName());
                    entry.put("gitUrl", service.getGitUrl());
                    entry.put("gitRef", gitRefOf(service));
                    entry.put("dockerfile", dockerfileOf(service));
                    entry.put("image", service.getImage());
                    return entry;
                })
                .toList();

        Map<String, Object> body = Map.of(
                "ref", PLATFORM_BRANCH,
                // workflow inputs are strings only, so the list travels as JSON text
                "inputs", Map.of("env_id", envId, "services", json.writeValueAsString(matrix)));

        github.post()
                .uri("/repos/{owner}/{repo}/actions/workflows/{file}/dispatches", owner, repo, WORKFLOW_FILE)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // GitHub doesn't return the run it starts, so it is found by its name: the workflow's
    // run-name is "Build <envId>", and envIds are unique. Null until GitHub has created the run
    public BuildRun findRun(String envId) {
        Map<String, Object> response = github.get()
                .uri("/repos/{owner}/{repo}/actions/workflows/{file}/runs?event=workflow_dispatch&per_page=50",
                        owner, repo, WORKFLOW_FILE)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response != null && response.get("workflow_runs") instanceof List<?> runs) {
            for (Object item : runs) {
                if (item instanceof Map<?, ?> run && ("Build " + envId).equals(run.get("display_title"))) {
                    return new BuildRun(
                            String.valueOf(run.get("status")),
                            run.get("conclusion") == null ? null : String.valueOf(run.get("conclusion")),
                            String.valueOf(run.get("html_url")));
                }
            }
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record BuildRun(String status, String conclusion, String url) {
        public boolean finished() {
            return "completed".equals(status);
        }

        public boolean succeeded() {
            return "success".equals(conclusion);
        }
    }
}
