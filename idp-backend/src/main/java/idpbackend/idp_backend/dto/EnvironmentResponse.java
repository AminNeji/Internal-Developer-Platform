package idpbackend.idp_backend.dto;


import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EnvironmentResponse {
    private Long id;
    private String envId;
    private String team;
    private String type;
    private String status;
    private String url;
    private String createdBy;
    private Instant createdAt;
    private Instant lastActiveAt;
    private List<ServiceConfig> services;
}
