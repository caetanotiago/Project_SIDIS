package isep.psoft.aisafe.flightroutes.clients;

/**
 * Vista local (anti-corruption layer) do agregado Aircraft do microsserviço
 * Aircraft Management. Só contém o que este serviço precisa.
 */
public record AircraftSnapshot(String registrationNumber, String status,
                               Integer seatingCapacity, Double maximumRange) {

    public boolean isAvailable() {
        return "AVAILABLE".equals(status);
    }
}
