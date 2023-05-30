package com.example.pharmacyrecomandation.pharmacy.dto;

import com.example.pharmacyrecomandation.pharmacy.entity.Pharmacy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PharmacyDto {

    private Long id;
    private String pharmacyName;
    private String pharmacyAddress;
    private double latitude;
    private double longitude;

}
