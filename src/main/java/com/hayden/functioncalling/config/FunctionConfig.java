package com.hayden.functioncalling.config;

import com.hayden.persistence.config.JpaConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan(basePackages = {
        "com.hayden.commitdiffmodel.codegen",
        "com.hayden.commitdiffmodel.scalar"})
@Import({JpaConfig.class})
public class FunctionConfig {


    /**
     * TODO: Note to self, there should definitely be some sort of circuit breaker here...
     *  pre-registered graphQL to start.
     * This function callback will accept the result from the AI model, which will accept
     * some natural language along with a context of the available functions registered
     * and produce the function to be called and the arguments of that function.
     * @param om
     * @return
     */
////    @Bean
//    public FunctionCallback graphQlFunctionCallback(ObjectMapper om) {
//        return FunctionCallback.builder()
//                .objectMapper(om)
//                .function("", a -> {})
//                .inputType(GraphQlPreRegisteredRequest.class)
//                .build();
//    }

}
