package com.hayden.functioncalling.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.functioncalling.model.GraphQlPreRegisteredRequest;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

@Configuration
@ComponentScan(basePackages = {
        "com.hayden.commitdiffmodel",
        "com.hayden.proto"})
public class FunctionConfig {

    @Bean
    public FunctionCallingHelper functionCallingHelper() {
        return new FunctionCallingHelper();
    }

    /**
     * TODO: Note to self, there should definitely be some sort of circuit breaker here...
     *  pre-registered graphQL to start.
     * This function callback will accept the result from the AI model, which will accept
     * some natural language along with a context of the available functions registered
     * and produce the function to be called and the arguments of that function.
     * @param om
     * @return
     */
//    @Bean
    public FunctionCallback graphQlFunctionCallback(ObjectMapper om) {
        return FunctionCallback.builder()
                .objectMapper(om)
                .function("", a -> {})
                .inputType(GraphQlPreRegisteredRequest.class)
                .build();
    }

}
