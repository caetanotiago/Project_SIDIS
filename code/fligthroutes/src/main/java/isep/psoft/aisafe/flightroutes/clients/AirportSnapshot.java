package isep.psoft.aisafe.flightroutes.clients;

/**
 * Vista local (anti-corruption layer) do agregado Airport do microsserviço Airports.
 */
public record AirportSnapshot(String iataCode, Double latitude, Double longitude, String status) {

    public boolean isOperational() {
        return "OPERATIONAL".equals(status);
    }
}
