package Marketly.Payment.Service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private static final String BEARER="Bearer";
    public byte[] expectedKey;

    public ApiKeyFilter(AppProps props){
        this.expectedKey = props.merchantApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return p.equals("/health") || p.equals("/webhooks/paystack");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header=request.getHeader("Authorization");
            if(header != null && header.startsWith(BEARER)){
                byte[] provided=header.substring(BEARER.length()).trim().getBytes(StandardCharsets.UTF_8);
                if(MessageDigest.isEqual(provided,expectedKey)){
                    filterChain.doFilter(request,response);
                    return;
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            throw new RuntimeException(e);
        }

    }

}
