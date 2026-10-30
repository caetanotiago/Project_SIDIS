package isep.psoft.aisafe.airports.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Testes unitários do agregado Airport e dos seus value objects (sem Spring). */
class AirportTest {

    private static Airport porto() {
        return new Airport("opo", "Francisco Sá Carneiro", "Porto", "Portugal", "Europe",
                "Europe/Lisbon", new Coordinates(41.2481, -8.6814),
                List.of(new Runway("17/35", 3480, 170, "asphalt")));
    }

    // ------------------------------------------------------------------ IATA

    @Test
    void iataCodeIsNormalisedToUpperCase() {
        assertEquals("OPO", IataCode.normalize(" opo "));
    }

    @Test
    void iataCodeMustHaveExactlyThreeLetters() {
        assertThrows(IllegalArgumentException.class, () -> IataCode.normalize("OP"));
        assertThrows(IllegalArgumentException.class, () -> IataCode.normalize("OPOR"));
        assertThrows(IllegalArgumentException.class, () -> IataCode.normalize("O1O"));
        assertThrows(IllegalArgumentException.class, () -> IataCode.normalize(null));
    }

    // ------------------------------------------------------------------ value objects

    @Test
    void coordinatesMustBeWithinRange() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(0.0, -181.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(null, 0.0));
        assertEquals(new Coordinates(41.0, -8.0), new Coordinates(41.0, -8.0)); // value object
    }

    @Test
    void runwayValidatesLengthAndOrientation() {
        assertThrows(IllegalArgumentException.class, () -> new Runway("17/35", 0, 170, null));
        assertThrows(IllegalArgumentException.class, () -> new Runway("17/35", 3000, 360, null));
        assertThrows(IllegalArgumentException.class, () -> new Runway(" ", 3000, 170, null));
        assertEquals("ASPHALT", new Runway("17/35", 3000, 170, "asphalt").getSurface());
    }

    // ------------------------------------------------------------------ registo (US106)

    @Test
    void newAirportIsOperationalWithNormalisedIata() {
        Airport airport = porto();

        assertEquals("OPO", airport.getIataCode());
        assertEquals(AirportStatus.OPERATIONAL, airport.getStatus());
        assertTrue(airport.isOperational());
        assertEquals(1, airport.getRunways().size());
    }

    @Test
    void airportRequiresAtLeastOneRunwayWithUniqueNames() {
        Coordinates c = new Coordinates(41.0, -8.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Airport("OPO", "Porto", "Porto", "Portugal", null, "Europe/Lisbon", c, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Airport("OPO", "Porto", "Porto", "Portugal", null, "Europe/Lisbon", c,
                        List.of(new Runway("17/35", 3000, 170, null), new Runway("17/35", 2000, 170, null))));
    }

    @Test
    void airportRequiresAValidIanaTimezone() {
        Coordinates c = new Coordinates(41.0, -8.0);
        List<Runway> runways = List.of(new Runway("17/35", 3000, 170, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Airport("OPO", "Porto", "Porto", "Portugal", null, "Lisbon/Porto", c, runways));
        assertThrows(IllegalArgumentException.class,
                () -> new Airport("OPO", " ", "Porto", "Portugal", null, "Europe/Lisbon", c, runways));
    }

    @Test
    void runwayListIsNotModifiableFromOutside() {
        Airport airport = porto();
        assertThrows(UnsupportedOperationException.class,
                () -> airport.getRunways().add(new Runway("05/23", 2000, 50, null)));
    }

    // ------------------------------------------------------------------ US106a

    @Test
    void certifyingTheSameModelTwiceIsAConflict() {
        Airport airport = porto();
        airport.certifyModel("A320");

        assertTrue(airport.isCertifiedFor("a320"));
        assertFalse(airport.isCertifiedFor("B737"));
        assertThrows(IllegalStateException.class, () -> airport.certifyModel("a320"));
        assertThrows(IllegalArgumentException.class, () -> airport.certifyModel(" "));
    }

    // ------------------------------------------------------------------ US109

    @Test
    void statusCanChangeToAnyKnownValue() {
        Airport airport = porto();
        airport.changeStatus(AirportStatus.from("under_maintenance"));

        assertEquals(AirportStatus.UNDER_MAINTENANCE, airport.getStatus());
        assertFalse(airport.isOperational());
        assertThrows(IllegalArgumentException.class, () -> AirportStatus.from("OPEN"));
        assertThrows(IllegalArgumentException.class, () -> airport.changeStatus(null));
    }

    // ------------------------------------------------------------------ US207 / US208

    @Test
    void facilitiesAreOptionalButCannotBeNegative() {
        Airport airport = porto();
        airport.describeFacilities(1, 37, Set.of("WIFI"), List.of("https://example.org/opo.jpg"));

        assertEquals(37, airport.getGates());
        assertEquals(Set.of("WIFI"), airport.getServices());
        assertThrows(IllegalArgumentException.class, () -> airport.describeFacilities(-1, null, null, null));
    }

    @Test
    void updateDetailsOnlyChangesTheFieldsThatAreSent() {
        Airport airport = porto();
        airport.describeFacilities(1, 37, Set.of("WIFI"), List.of());
        airport.updateContactDetails("00:00-24:00", "info@opo.pt", null);

        airport.updateDetails(null, null, "+351 229 432 400", null, 40, null, null);

        assertEquals("00:00-24:00", airport.getOperationalHours()); // não enviado → mantém
        assertEquals("info@opo.pt", airport.getContactEmail());
        assertEquals("+351 229 432 400", airport.getContactPhone());
        assertEquals(1, airport.getTerminals());
        assertEquals(40, airport.getGates());
        assertEquals(Set.of("WIFI"), airport.getServices());

        airport.updateDetails("", null, null, null, null, Set.of(), null); // vazio → limpa
        assertNull(airport.getOperationalHours());
        assertTrue(airport.getServices().isEmpty());
    }
}
