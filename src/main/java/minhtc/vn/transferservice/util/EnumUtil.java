package minhtc.vn.transferservice.util;

import lombok.experimental.UtilityClass;
import minhtc.vn.walletservice.exception.BadRequestException;

@UtilityClass
public class EnumUtil {
    public <T extends Enum<T>> void validateEnumValue(String valueString, Class<T> enumClass) {
        // 1. Kiểm tra null để tránh NullPointerException (vì Enum.valueOf không nhận null)
        if (valueString == null || valueString.trim().isEmpty()) {
            throw new BadRequestException("Value for " + enumClass.getSimpleName() + " cannot be null or empty");
        }
        try {
            // 2. Sử dụng class cha java.lang.Enum để ép kiểu từ String sang Enum
            Enum.valueOf(enumClass, valueString);
        } catch (IllegalArgumentException e) {
            // 3. (Tùy chọn) Viết lại message cho rõ ràng hơn trả về cho Client
            throw new BadRequestException("Invalid value '" + valueString + "'. Accepted values are: " +
                    java.util.Arrays.toString(enumClass.getEnumConstants()));
        }
    }
}
