package isep.psoft.aisafe.airports.events;

import isep.sidis.common.messaging.MessagingNames;

/**
 * Nomes dos eventos publicados pelo Airports no exchange {@code aisafe.airports.events}.
 * Contrato acordado com o grupo: docs/2-containers/Integration-Contracts.md, secção 9.4.
 */
public final class AirportEvents {

    /** Serviço produtor: o exchange é {@code aisafe.airports.events}. */
    public static final String SOURCE_SERVICE = "airports";

    public static final String AGGREGATE = "airport";

    /** US106 / US207 — aeroporto registado. */
    public static final String AIRPORT_CREATED = "AirportCreated";
    /** US106a (certificação) e US208 (horário, contactos, instalações). */
    public static final String AIRPORT_UPDATED = "AirportUpdated";
    /** US109 — mudança do estado operacional. */
    public static final String AIRPORT_STATUS_CHANGED = "AirportStatusChanged";

    public static final String CREATED_KEY = MessagingNames.routingKey(AGGREGATE, "created");
    public static final String UPDATED_KEY = MessagingNames.routingKey(AGGREGATE, "updated");
    public static final String STATUS_CHANGED_KEY = MessagingNames.routingKey(AGGREGATE, "status-changed");

    /**
     * Pedido de sincronização: uma réplica que acabou de arrancar pede às outras que voltem a publicar o estado dos
     * aeroportos de que são donas (ver {@code ReadModelSynchronizer}). Usa outro "agregado" ({@code airport-sync}) para
     * NÃO apanhar o binding {@code airport.*}: só as réplicas do Airports o recebem, os outros serviços não.
     */
    public static final String SYNC_REQUESTED = "AirportSyncRequested";
    public static final String SYNC_REQUESTED_KEY = MessagingNames.routingKey("airport-sync", "requested");

    /** Padrão de binding que apanha todos os eventos de aeroportos. */
    public static final String ALL_AIRPORT_EVENTS = AGGREGATE + ".*";

    private AirportEvents() {
    }
}
