package dev.disputeguard.dispute;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Optional<Dispute> findByPaypalDisputeId(String paypalDisputeId);

    List<Dispute> findAllByOrderByCreatedAtDesc();
}
