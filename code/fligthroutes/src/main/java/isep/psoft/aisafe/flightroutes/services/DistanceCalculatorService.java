package isep.psoft.aisafe.flightroutes.services;

import org.springframework.stereotype.Service;

@Service
public class DistanceCalculatorService {

    // Raio da Terra em quilómetros
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calcula a distância entre duas coordenadas usando a Fórmula de Haversine.
     * Fórmula recomendada para fazer calculos deste tipo
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double originLat = Math.toRadians(lat1);
        double destinationLat = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) + 
                   Math.pow(Math.sin(dLon / 2), 2) * Math.cos(originLat) * Math.cos(destinationLat);
        double c = 2 * Math.asin(Math.sqrt(a));

        return EARTH_RADIUS_KM * c;
    }
}