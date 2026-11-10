package isep.psoft.aisafe.flightroutes.query;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/** Read-side routes of THIS replica (see {@link ReplicatedRoute}); never aggregated with the peers. */
public interface ReplicatedRouteRepository extends CrudRepository<ReplicatedRoute, String> {

    @Query("SELECT r FROM ReplicatedRoute r ORDER BY r.originIata, r.destinationIata, r.id")
    List<ReplicatedRoute> findAllOrdered();
}
