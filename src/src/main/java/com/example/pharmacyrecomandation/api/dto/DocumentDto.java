package com.example.pharmacyrecomandation.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentDto {

    @JsonProperty("address_name")
    public String addressName;

    @JsonProperty("y")
    public String latitude;

    @JsonProperty("x")
    public String longitude;

}
