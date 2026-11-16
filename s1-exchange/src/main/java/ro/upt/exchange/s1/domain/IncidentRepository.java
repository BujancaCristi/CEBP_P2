package ro.upt.exchange.s1.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Page<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status, Pageable pageable);
    Page<Incident> findByTypeOrderByCreatedAtDesc(IncidentType type, Pageable pageable);
    Page<Incident> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
