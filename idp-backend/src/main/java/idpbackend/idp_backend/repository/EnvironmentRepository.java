package idpbackend.idp_backend.repository;


import idpbackend.idp_backend.model.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment,Long> {

    public Environment findByTeam(String team);
    public Environment findByType(String type);
    public Environment findByEnvId(String envId);
    public List<Environment> findByExpiresAtBefore(Instant time);
}
