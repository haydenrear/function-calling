package com.hayden.functioncalling.model_server;

import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModelServerFunctionCallingModel extends AbstractToolCallSupport implements org.springframework.ai.chat.model.ChatModel {

    public ModelServerFunctionCallingModel() {
        super(new FunctionCallbackContext());
    }

    protected ModelServerFunctionCallingModel(FunctionCallbackContext functionCallbackContext) {
        super(functionCallbackContext);
    }

    protected ModelServerFunctionCallingModel(FunctionCallbackContext functionCallbackContext, FunctionCallingOptions functionCallingOptions, List<FunctionCallback> toolFunctionCallbacks) {
        super(functionCallbackContext, functionCallingOptions, toolFunctionCallbacks);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return null;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }
}
