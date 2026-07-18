package minhtc.vn.transferservice.service;

public interface LocaleService {
    String getMessage(String code);

    String getMessageWithArgs(String code, Object[] args);
}
