package minhtc.vn.transferservice.util;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@UtilityClass
public class SecurityUtil {
    private final String USERNAME_KEY = "preferred_username";
    /**
     * Lấy đối tượng Authentication hiện tại từ SecurityContext.
     * @return đối tượng Authentication, hoặc null nếu không có ai được xác thực.
     */
    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Lấy username của người dùng đã được xác thực.
     * @return username, hoặc "System" nếu không có ai được xác thực.
     */
    public String getUsername() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getClaim(USERNAME_KEY) : "System";
    }

    /**
     * Kiểm tra xem có người dùng nào đã được xác thực trong context hiện tại hay không.
     * @return true nếu đã xác thực, ngược lại là false.
     */
    public boolean isAuthenticated() {
        Authentication auth = getAuthentication();
        return auth != null && auth.isAuthenticated();
    }

    /**
     * Lấy danh sách các quyền hạn (authorities) của người dùng đã được xác thực.
     * Phương thức này trả về một Collection cụ thể để tránh sử dụng wildcard, tuân thủ quy tắc của Sonar.
     * @return một Collection chứa các GrantedAuthority, hoặc một danh sách rỗng nếu không có ai được xác thực.
     */
    public List<GrantedAuthority> getAuthorities() {
        Authentication auth = getAuthentication();
        if (auth == null) {
            return Collections.emptyList();
        }
        // Tạo một ArrayList mới để có kiểu trả về cụ thể, không dùng wildcard.
        return auth.getAuthorities().stream().collect(Collectors.toUnmodifiableList());
    }

    public List<String> getRoles() {
        return getAuthorities().stream().map(Objects::toString).toList();
    }

    public boolean hasRole(String role) {
        if (role == null)
            return false;
        return getRoles().contains(role.contains("ROLE_") ? role : "ROLE_" + role);
    }

    public Jwt getJwt() {
        Authentication auth = getAuthentication();
        if (auth == null || auth.getPrincipal() == null)
            return null;
        if (auth.getPrincipal() instanceof Jwt jwt)
            return jwt;
        return null;
    }

    public String getToken() {
        Jwt jwt = getJwt();
        return jwt.getTokenValue();
    }

    public String getKeycloakUserId() {
        Jwt jwt = getJwt();
        return jwt.getSubject();
    }

    public String getFullName() {
        Jwt jwt = getJwt();
        return jwt.getClaimAsString("name");
    }

    public String getEmail() {
        Jwt jwt = getJwt();
        return jwt.getClaimAsString("email");
    }
}
