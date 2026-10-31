package isep.psoft.aisafe.maintenance.bootstrap;

import isep.psoft.aisafe.maintenance.domain.CompletionDetails;
import isep.psoft.aisafe.maintenance.domain.MaintenanceComponent;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.domain.RecordDetails;
import isep.psoft.aisafe.maintenance.domain.TemplateReference;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados de demonstração (perfil {@code demo}). Cada réplica carrega registos DIFERENTES (definidos no
 * seu application-instanceN.properties), para demonstrar a agregação e o encaminhamento P2P.
 * São gravados diretamente, sem chamar o Aircraft Management (o estado das aeronaves de demo já
 * está coerente: CS-TUB é carregado UNDER_MAINTENANCE no Aircraft Management).
 *
 * <pre>
 * matrícula:modelo:template:início:minutosPrevistos:componente[:dataConclusão:custo:minutosReais[:horasDeVoo]]
 * CS-TUA:A320:A-Check Inspection:2026-05-10:480:AIRFRAME:2026-05-11:12500:600:1400
 * </pre>
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "maintenance.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    private final MaintenanceRecordRepository recordRepository;
    private final MaintenanceTemplateRepository templateRepository;

    @Value("${maintenance.demo.records:}")
    private List<String> records;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (recordRepository.count() > 0) {
            return;
        }
        records.stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(this::load);
    }

    private void load(String spec) {
        String[] p = spec.split(":");
        MaintenanceTemplate template = templateRepository.findById(MaintenanceTemplate.referenceId(p[2]))
                .orElseThrow(() -> new IllegalStateException("Demo record uses an unknown template: " + p[2]));
        MaintenanceRecord record = new MaintenanceRecord(p[0], p[1],
                new TemplateReference(template.getId(), template.getName(), template.getType()),
                template.getChecklist(),
                new RecordDetails("Demo: " + template.getName() + " of " + p[0], LocalDate.parse(p[3]),
                        Integer.parseInt(p[4])),
                MaintenanceComponent.parseOptional(p[5]));
        if (p.length >= 9) {
            record.complete(new CompletionDetails(LocalDate.parse(p[6]), "Demo: work completed without findings.",
                    new BigDecimal(p[7]), Integer.parseInt(p[8]), p.length > 9 ? Double.parseDouble(p[9]) : null));
        }
        recordRepository.save(record);
        log.info("Demo maintenance record loaded: {} {} ({})", p[0], p[2], record.getStatus());
    }
}
