package com.hayden.functioncalling.config;

import lombok.*;

import java.util.Objects;


@RequiredArgsConstructor
@AllArgsConstructor
@Data
@Getter
@Setter
public class ModelDescriptor {
    private boolean defaultModel;
    private int dimensions;
    private String name;
}
