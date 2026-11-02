package dev.disputeguard.evidence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidencePackRepository extends JpaRepository<EvidencePack, Long> {

    Optional<EvidencePack> findFirstByDisputeIdOrderByCreatedAtDesc(Long disputeId);
}
