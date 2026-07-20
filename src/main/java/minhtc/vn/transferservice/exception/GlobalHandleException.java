package minhtc.vn.transferservice.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import minhtc.vn.transferservice.dto.response.BaseResponse;
import minhtc.vn.transferservice.enums.MessageCode;
import minhtc.vn.transferservice.service.LocaleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.StringJoiner;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalHandleException {
    private final LocaleService localeService;

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<BaseResponse> handlePCException(BadRequestException e) {
        String message;
        String code;
        if (e.getMessageArgs() != null) {
            message = localeService.getMessageWithArgs(e.getMessageKey(), e.getMessageArgs());
            code = e.getCode();
        } else if (e.getMessageKey() != null) {
            message = localeService.getMessage(e.getMessageKey());
            code = e.getCode();
        } else {
            message = e.getMessage();
            code = HttpStatus.BAD_REQUEST.name();
        }
        return ResponseEntity.badRequest().body(BaseResponse.builder()
                        .code(code)
                        .message(message)
                        .status(HttpStatus.BAD_REQUEST.value())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("Handle MethodArgumentNotValidException: {}", e);
        StringJoiner message = new StringJoiner(", ");
        String code = null;
        for (var er: e.getBindingResult().getAllErrors()) {
            var errorMessageCode = er.getDefaultMessage();
            message.add(localeService.getMessage(MessageCode.valueOf(errorMessageCode)
                    .getMessageKey()));
            code = MessageCode.valueOf(errorMessageCode)
                    .getCode();
        }
        return new ResponseEntity<>(BaseResponse.builder()
                .message(message.toString())
                .code(code)
                .status(HttpStatus.BAD_REQUEST.value())
                .build(),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TransferValidationException.class)
    public ResponseEntity<BaseResponse> handleTransferValidationException(TransferValidationException e) {
        log.warn("TransferValidationException: {}", e.getMessage());
        return new ResponseEntity<>(BaseResponse.builder()
                .code("TRANSFER_VALIDATION_ERROR") // Hoặc một mã lỗi cụ thể hơn nếu có
                .message(e.getMessage())
                .status(HttpStatus.BAD_REQUEST.value())
                .build(),
                HttpStatus.BAD_REQUEST);
    }
}
