package uk.gov.hmcts.payment.api.configuration;

import io.swagger.annotations.ApiParam;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ResolvedMethodParameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.ParameterBuilderPlugin;
import springfox.documentation.spi.service.contexts.ParameterContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import static org.apache.commons.lang3.StringUtils.*;
import static springfox.documentation.builders.PathSelectors.regex;

@Configuration
@EnableSwagger2
public class SwaggerConfiguration {

    @Bean
    public Docket newsApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("payment")
            .globalOperationParameters(Arrays.asList(
                new ParameterBuilder()
                    .name("Authorization")
                    .description("User authorization header")
                    .required(true)
                    .parameterType("header")
                    .modelRef(new ModelRef("string"))
                    .build(),
                new ParameterBuilder()
                    .name("ServiceAuthorization")
                    .description("Service authorization header")
                    .required(true)
                    .parameterType("header")
                    .modelRef(new ModelRef("string"))
                    .build())
            )
            .apiInfo(apiInfo())
            .select()
            .paths(regex("/users/.*"))
            .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
            .title("Payment API documentation")
            .description("Payment API documentation")
            .build();
    }

    @Component
    public static class CamelCaseToSpacesDocumentationPlugin implements ParameterBuilderPlugin {

        @Override
        public void apply(ParameterContext context) {
            ResolvedMethodParameter methodParameter = context.resolvedMethodParameter();

            if (methodParameter.hasParameterAnnotation(ApiParam.class)) {
                return;
            }

            String description = capitalize(join(splitByCharacterTypeCamelCase(methodParameter.defaultName().get()), ' '));
            context.parameterBuilder().description(description);
        }

        @Override
        public boolean supports(DocumentationType delimiter) {
            return DocumentationType.SWAGGER_2.equals(delimiter);
        }
    }
}