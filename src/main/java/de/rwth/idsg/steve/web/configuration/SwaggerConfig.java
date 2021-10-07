package de.rwth.idsg.steve.web.configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import com.google.common.base.Predicates;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.VendorExtension;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
 
@Configuration
@EnableSwagger2
public class SwaggerConfig extends WebMvcConfigurerAdapter 
{

    Contact contact = new Contact(
        "Anirudh Ramesh",
        "http://irasus.com", 
        "anirudh@irasus.com"
    );

    List<VendorExtension> vendorExtensions = new ArrayList<>();

    ApiInfo info = new ApiInfo("Irasus EVCMS REST API", "", "1.0.1", contact.toString(), "", "", vendorExtensions.toString());

    @Bean
    public Docket api() {
        // @formatter:off
        return new Docket(DocumentationType.SWAGGER_2).select()
                // .apis(RequestHandlerSelectors.any())
                .apis(Predicates.not(RequestHandlerSelectors.basePackage("org.springframework.boot")))
                // .paths(PathSelectors.any())
                .paths(PathSelectors.ant("/v1.0.1/**"))
                .build()
                .apiInfo(info);
        // @formatter:on
    }
 
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) 
    {
        registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
