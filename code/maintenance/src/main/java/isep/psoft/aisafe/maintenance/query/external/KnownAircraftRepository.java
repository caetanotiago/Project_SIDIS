package isep.psoft.aisafe.maintenance.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnownAircraftRepository extends JpaRepository<KnownAircraft, String> {

    Optional<KnownAircraft> findByRegistrationNumberIgnoreCase(String registrationNumber);

    List<KnownAircraft> findAllByOrderByRegistrationNumberAsc();
}
