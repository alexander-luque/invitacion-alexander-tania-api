package pe.at.invitacion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration public class CorsConfig {
 @Bean WebMvcConfigurer cors(@Value("${app.frontend-origin}") String origin){return new WebMvcConfigurer(){@Override public void addCorsMappings(CorsRegistry r){r.addMapping("/api/**").allowedOrigins(origin.split(",")).allowedMethods("GET","POST","PATCH","OPTIONS").allowedHeaders("Content-Type","X-Admin-Key");}};}
}
