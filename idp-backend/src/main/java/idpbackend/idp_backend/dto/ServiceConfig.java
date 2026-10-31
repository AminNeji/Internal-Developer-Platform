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

    // both are optional in the request, and rows created before they existed hold null
    public String getHealthPath() {
        return healthPath == null || healthPath.isBlank() ? "/" : healthPath;
    }

    public Map<String, String> getEnv() {
        return env == null ? Map.of() : env;
    }
}
