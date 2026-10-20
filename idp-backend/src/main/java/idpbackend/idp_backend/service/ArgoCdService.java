package idpbackend.idp_backend.service;


import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ArgoCdService {

    private final RestClient restClient;
    private final String gitRepoUrl;

    public ArgoCdService(@Value("${argocd.url}") String argoCdUrl,
                         @Value("${argocd.token}") String argoCdToken,
                         @Value("${git.repo.url}") String gitRepoUrl) {
        this.gitRepoUrl = gitRepoUrl;
        this.restClient = RestClient.builder()
                .baseUrl(argoCdUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + argoCdToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void createApplication(String envId, String namespace) {
        Map<String, Object> application = Map.of(
                "metadata", Map.of("name", envId),
                "spec", Map.of(
                        "project", "default",
                        "source", Map.of(
                                "repoURL", gitRepoUrl,
                                "targetRevision", "dev",
                                "path","charts/environment",
                                "helm",Map.of("valueFiles", List.of("../../environments/" + envId + "/values.yaml"))
                        ),
                        "destination", Map.of(
                                "server", "https://kubernetes.default.svc",
                                "namespace", namespace
                        ),
                        "syncPolicy", Map.of(
                                "automated", Map.of("prune", true, "selfHeal", true),
                                "syncOptions", List.of("CreateNamespace=true")
                        )
                )
        );

        restClient.post()
                .uri("/api/v1/applications")
                .body(application)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteApplication(String envId) {
        restClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/applications/{name}")
                        .queryParam("cascade", true)
                        .build(envId))
                .retrieve()
                .toBodilessEntity();
    }

    public ApplicationStatus getApplicationStatus(String envId) {
        JsonNode response = restClient.get()
                .uri("/api/v1/applications/{name}", envId)
                .retrieve()
                .body(JsonNode.class);

        JsonNode status = response.path("status");
        return new ApplicationStatus(
                status.path("sync").path("status").asText("Unknown"),
                status.path("health").path("status").asText("Unknown")
        );
    }

    public record ApplicationStatus(String syncStatus, String healthStatus) {
    }
}
