package idpbackend.idp_backend.repository;


import idpbackend.idp_backend.model.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment,Long> {

    public Environment findByTeam(String team);
    public Environment findByType(String type);
    public Environment findByEnvId(String envId);
    public List<Environment> findByExpiresAtBefore(Instant time);
    public List<Environment> findByStatus(String status);

    // changes the status only if it still is `from`, and returns how many rows changed (0 or 1).
    // Both backend replicas run BuildWatcher, so this is how one of them claims an environment
    @Modifying
    @Transactional
    @Query("update Environment e set e.status = :to where e.id = :id and e.status = :from")
    public int changeStatus(Long id, String from, String to);

    @Modifying
    @Transactional
    @Query("update Environment e set e.buildUrl = :buildUrl where e.id = :id")
    public int setBuildUrl(Long id, String buildUrl);
}
