package isep.psoft.aisafe.maintenance.events;

import isep.sidis.common.messaging.MessagingNames;

public final class MaintenanceEvents {

    public static final String SOURCE_SERVICE = "maintenance";

    public static final String TEMPLATE_AGGREGATE = "maintenance-template";
    public static final String RECORD_AGGREGATE = "maintenance-record";

    public static final String TEMPLATE_CREATED = "MaintenanceTemplateCreated";
    public static final String RECORD_CREATED = "MaintenanceRecordCreated";
    public static final String RECORD_CATEGORIZED = "MaintenanceRecordCategorized";
    public static final String RECORD_COMPLETED = "MaintenanceRecordCompleted";

    public static final String TEMPLATE_CREATED_KEY = MessagingNames.routingKey(TEMPLATE_AGGREGATE, "created");
    public static final String RECORD_CREATED_KEY = MessagingNames.routingKey(RECORD_AGGREGATE, "created");
    public static final String RECORD_CATEGORIZED_KEY = MessagingNames.routingKey(RECORD_AGGREGATE, "categorized");
    public static final String RECORD_COMPLETED_KEY = MessagingNames.routingKey(RECORD_AGGREGATE, "completed");

    public static final String SYNC_REQUESTED = "MaintenanceSyncRequested";
    public static final String SYNC_REQUESTED_KEY = MessagingNames.routingKey("maintenance-sync", "requested");

    public static final String ALL_TEMPLATE_EVENTS = TEMPLATE_AGGREGATE + ".*";
    public static final String ALL_RECORD_EVENTS = RECORD_AGGREGATE + ".*";

    private MaintenanceEvents() {
    }
}
