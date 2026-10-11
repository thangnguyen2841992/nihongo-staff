package com.nihongo.staff.config;
import com.nihongo.security.SecurityJwtDecoders;
import org.springframework.context.annotation.*;import org.springframework.core.env.Environment;import org.springframework.security.oauth2.jwt.JwtDecoder;
@Configuration public class JwtConfig {
 @Bean public JwtDecoder jwtDecoder(Environment env){return SecurityJwtDecoders.servlet(env);}
}
