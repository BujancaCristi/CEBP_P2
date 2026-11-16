package ro.upt.exchange.s1.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Registers {@link AuthenticatedUserArgumentResolver}. Owner: Cezar. */
@Configuration
public class WebMvcPlatformConfig implements WebMvcConfigurer {

    private final AuthenticatedUserArgumentResolver resolver;

    public WebMvcPlatformConfig(AuthenticatedUserArgumentResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}
