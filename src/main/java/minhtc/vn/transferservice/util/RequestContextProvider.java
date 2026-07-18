package minhtc.vn.transferservice.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RequestContextProvider {
    private static final String CLIENT_IP_HEADER = "X-Client-IP";
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    private final HttpServletRequest request;

    public String getClientIp() {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR_HEADER);

        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");

        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        String apiGatewayFilterIp = request.getHeader(CLIENT_IP_HEADER);

        if (StringUtils.hasText(apiGatewayFilterIp)) {
            return apiGatewayFilterIp.trim();
        }

        String remoteAddress = request.getRemoteAddr();

        return StringUtils.hasText(remoteAddress)
                ? remoteAddress
                : UNKNOWN;
    }

    public String getCorrelationId() {
        String correlationId = request.getHeader("X-Correlation-Id");

        return StringUtils.hasText(correlationId)
                ? correlationId
                : null;
    }

    public String getRequestPath() {
        return request.getRequestURI();
    }
}
