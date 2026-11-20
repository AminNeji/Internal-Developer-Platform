package idpbackend.idp_backend.dto;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfig {
    private String serviceName;
    private int port;
    private String path;
    private String image;
    private String healthPath;
    private Map<String, String> env;
    // set instead of image when the platform builds the image from a Git repository
    private String gitUrl;
    private String gitRef;
    private String dockerfilePath;

    // not a getter on purpose, so Jackson leaves it out of the JSON
    public boolean builtFromGit() {
        return gitUrl != null && !gitUrl.isBlank();
    }

    // both are optional in the request, and rows created before they existed hold null
    public String getHealthPath() {
        return healthPath == null || healthPath.isBlank() ? "/" : healthPath;
    }

    public Map<String, String> getEnv() {
        return env == null ? Map.of() : env;
    }
}
