package isep.psoft.aisafe.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Value Object: referência, por identidade, ao template usado num registo ("maintenance type" da
 * US115) + cópia do nome e do tipo. O template pode estar guardado noutra réplica, por isso não
 * existe associação JPA.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateReference {

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false)
    private TemplateType templateType;

    public TemplateReference(String templateId, String templateName, TemplateType templateType) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("The template id is required.");
        }
        if (templateName == null || templateName.isBlank() || templateType == null) {
            throw new IllegalArgumentException("The template name and type are required.");
        }
        this.templateId = templateId;
        this.templateName = templateName;
        this.templateType = templateType;
    }
}
