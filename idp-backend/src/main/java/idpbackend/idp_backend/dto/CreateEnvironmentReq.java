package idpbackend.idp_backend.dto;


import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CreateEnvironmentReq {
    private String team;
    private String type;
    private List<ServiceConfig> services;
    // how long the environment lives before CleanupService destroys it
    private Integer ttlHours;

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(Integer ttlHours) {
        this.ttlHours = ttlHours;
    }

    public List<ServiceConfig> getServices() {
        return services;
    }

    public void setServices(List<ServiceConfig> services) {
        this.services = services;
    }
}
