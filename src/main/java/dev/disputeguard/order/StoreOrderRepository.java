package dev.disputeguard.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {

    Optional<StoreOrder> findByPaypalOrderId(String paypalOrderId);

    Optional<StoreOrder> findByCaptureId(String captureId);

    List<StoreOrder> findAllByOrderByCreatedAtDesc();
}
