package minhtc.vn.transferservice.util;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

@UtilityClass
public class StringUtil {
    public String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public String normalizeTextRequire(
            String value,
            String fieldName
    ) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        return value.trim();
    }

    public String normalizeTextUpperCase(
            String value,
            String fieldName
    ) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        return value.trim();
    }
}
