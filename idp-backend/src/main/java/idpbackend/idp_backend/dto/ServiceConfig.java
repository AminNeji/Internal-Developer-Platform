package idpbackend.idp_backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfig {
    private String serviceName;
    private int port;
    private String path;
    private String image;
}
