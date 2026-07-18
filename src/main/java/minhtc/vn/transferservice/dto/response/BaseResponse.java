package minhtc.vn.transferservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@SuperBuilder
public class BaseResponse<T> {
    private int status;
    private String code;
    private String message;
    private T result;

    public static <T> BaseResponse<T> success(String code, String message, T result) {
        return BaseResponse.<T>builder()
                .message(message)
                .code(code)
                .result(result)
                .status(HttpStatus.OK.value())
                .build();
    }

    public static <T> BaseResponse<T> create(String code, String message, T result) {
        return BaseResponse.<T>builder()
                .message(message)
                .code(code)
                .result(result)
                .status(HttpStatus.CREATED.value())
                .build();
    }

    public static <T> BaseResponse<T> error(String code, String message) {
        return BaseResponse.<T>builder()
                .message(message)
                .code(code)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
    }

    public static <T> BaseResponse<T> badRequest(String code, String message) {
        return BaseResponse.<T>builder()
                .message(message)
                .code(code)
                .status(HttpStatus.BAD_REQUEST.value())
                .build();
    }
}
