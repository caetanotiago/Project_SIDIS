package isep.psoft.aisafe.maintenance.bootstrap;

import isep.psoft.aisafe.maintenance.domain.MaintenanceInterval;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.domain.TemplateType;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WP#0A — "Preload maintenance templates". Carregados em TODAS as réplicas com ids derivados do nome
 * ({@link MaintenanceTemplate#reference}): as três cópias são o mesmo template, qualquer réplica o
 * serve localmente e a junção por id elimina os duplicados. Os templates não têm caso de uso de
 * alteração, por isso as cópias nunca divergem.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "maintenance.bootstrap", name = "reference-templates", havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class ReferenceTemplatesBootstrap implements ApplicationRunner {

    public static final String A_CHECK = "A-Check Inspection";
    public static final String C_CHECK = "C-Check Heavy Maintenance";
    public static final String ENGINE_OVERHAUL = "Engine Overhaul";
    public static final String WIFI_RETROFIT = "Cabin Wi-Fi Retrofit";

    private static final List<String> ALL_MODELS = List.of("A320", "B737", "A330", "E190");
    private static final Logger log = LoggerFactory.getLogger(ReferenceTemplatesBootstrap.class);

    private final MaintenanceTemplateRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        save(MaintenanceTemplate.reference(A_CHECK, TemplateType.INSPECTION, ALL_MODELS,
                List.of("Inspect landing gear and tyres", "Check engine oil and hydraulic fluid levels",
                        "Inspect fuselage and wings for damage", "Test emergency equipment and lighting"),
                new MaintenanceInterval(500, 120)));
        save(MaintenanceTemplate.reference(C_CHECK, TemplateType.SCHEDULED_MAINTENANCE, ALL_MODELS,
                List.of("Detailed structural inspection", "Corrosion inspection and treatment",
                        "Replace cabin components as required", "Functional test of flight controls"),
                new MaintenanceInterval(6000, 730)));
        save(MaintenanceTemplate.reference(ENGINE_OVERHAUL, TemplateType.OVERHAUL, ALL_MODELS,
                List.of("Remove engine", "Disassemble and inspect modules", "Replace life-limited parts",
                        "Reassemble, install and run-up test"),
                new MaintenanceInterval(20000, null)));
        save(MaintenanceTemplate.reference(WIFI_RETROFIT, TemplateType.MODIFICATION, List.of("A320", "A330"),
                List.of("Install antenna and access points", "Route and test wiring", "Certify installation"),
                null));
    }

    private void save(MaintenanceTemplate template) {
        if (!repository.existsById(template.getId())) {
            repository.save(template);
            log.info("Reference maintenance template loaded: {}", template.getName());
        }
    }
}
