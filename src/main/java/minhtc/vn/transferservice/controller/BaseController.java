package minhtc.vn.transferservice.controller;


import minhtc.vn.transferservice.dto.response.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public abstract class BaseController {
    public <T> ResponseEntity<BaseResponse<T>> success(T response) {
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "success",
                response));
    }

    public <T> ResponseEntity<BaseResponse<T>> create(T response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.create(HttpStatus.CREATED.name(), "success",
                response));
    }

    public <T> ResponseEntity<BaseResponse<T>> badRequest() {
        return ResponseEntity.badRequest().body(BaseResponse.error(HttpStatus.BAD_REQUEST.name(),
                "bad request"));
    }

    public <T> ResponseEntity<BaseResponse<T>> error() {
        return ResponseEntity.badRequest().body(BaseResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.name(),
                "internal server"));
    }
}
