package talmal.contact.contactForm;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableEurekaClient
@Slf4j
public class ContactFormApplication
{
	public static void main(String[] args)
	{
		SpringApplication.run(ContactFormApplication.class, args);
	}

	@Bean
	@LoadBalanced
	public WebClient.Builder getWebClientBuilder()
	{
		return WebClient.builder();
	}

	/**
	 * set cros filter to allow frontend communicate with backend (set in environment file)
	 * @return
	 */
	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter(@Value("#{'${services.allowed_origins}'.split(',')}") List<String> allowedOrigins)
	{
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		
		log.debug("---------------------------------------------------");
		log.debug(allowedOrigins.toString());
		config.setAllowedOriginPatterns(allowedOrigins);
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		
		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<CorsFilter>(new CorsFilter(source));
		bean.setOrder(0);
		
		return bean;
	}
}
