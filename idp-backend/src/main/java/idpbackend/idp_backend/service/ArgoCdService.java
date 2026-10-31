package idpbackend.idp_backend.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
        try {
            restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/applications/{name}")
                            .queryParam("cascade", true)
                            .build(envId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyGone) {
            // an earlier destroy got this far, or the other backend replica's cleanup job did;
            // not an error, otherwise the database row could never be removed
            System.out.println("Application ArgoCD déjà supprimée : " + envId);
        }
    }

    // read as a plain Map: Spring Boot 4 parses JSON with Jackson 3, and Jackson 2 types
    // such as com.fasterxml JsonNode (still on the classpath via fabric8) fail at runtime
    public ApplicationStatus getApplicationStatus(String envId) {
        Map<String, Object> response = restClient.get()
                .uri("/api/v1/applications/{name}", envId)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> status = child(response, "status");

        // ArgoCD lists the load balancer address here once the environment's Ingress has
        // one; with several paths it lists one URL per path, so the shortest is the root
        String url = null;
        if (child(status, "summary").get("externalURLs") instanceof List<?> urls) {
            for (Object entry : urls) {
                String candidate = String.valueOf(entry);
                if (url == null || candidate.length() < url.length()) {
                    url = candidate;
                }
            }
        }

        // ArgoCD only says https when the Ingress names a TLS secret, and here the certificate
        // lives on the load balancer instead. Every environment is on HTTPS, so report that
        if (url != null) {
            url = url.replaceFirst("^http://", "https://");
        }

        return new ApplicationStatus(
                text(child(status, "sync"), "status"),
                text(child(status, "health"), "status"),
                url
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "Unknown" : value.toString();
    }

    public record ApplicationStatus(String syncStatus, String healthStatus, String url) {
    }
}
