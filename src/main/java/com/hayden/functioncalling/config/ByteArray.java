package com.hayden.functioncalling.config;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;

@DgsScalar(name = "ByteArray")
@RequiredArgsConstructor
@Data
public class ByteArray implements Coercing<byte[], String> {

    private final byte[] bytes;

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof byte[]) {
            return new String((byte[]) dataFetcherResult, StandardCharsets.UTF_8);
        }
        throw new CoercingSerializeException("Invalid value '" + dataFetcherResult + "' for Byte");
    }

    @Override
    public byte[] parseValue(Object input) throws CoercingParseValueException {
        if (input instanceof String) {
            return ((String) input).getBytes(StandardCharsets.UTF_8);
        }
        throw new CoercingParseValueException("Invalid value '" + input + "' for Byte");
    }

    @Override
    public byte[] parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            return ((StringValue) input).getValue().getBytes(StandardCharsets.UTF_8);
        }
        throw new CoercingParseLiteralException("Invalid value '" + input + "' for Byte");
    }
}
