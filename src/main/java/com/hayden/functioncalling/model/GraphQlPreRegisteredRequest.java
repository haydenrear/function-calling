package com.hayden.functioncalling.model;

import java.util.Map;

public record GraphQlPreRegisteredRequest(String preRegisteredName,
                                          Map<String, String> arguments) {
}
