package com.example.pharmacyrecomandation.pharmacy.service;

import com.example.pharmacyrecomandation.api.dto.KakaoApiReponseDto;
import com.example.pharmacyrecomandation.api.service.KakaoAddressSearchService;
import com.example.pharmacyrecomandation.pharmacy.dto.PharmacyDto;
import com.example.pharmacyrecomandation.pharmacy.entity.Pharmacy;
import com.example.pharmacyrecomandation.pharmacy.repository.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PharmacySearchService {
    PharmacyRepository pharmacyRepository;
    private final PharmacyRepositoryService pharmacyRepositoryService;

    public List<PharmacyDto> searchPharmacyDtoList(){

        //redis


        // db
        return pharmacyRepositoryService.findAll()
                .stream()
                .map(PharmacySearchService::convertToPharmacyDto)
                .collect(Collectors.toList());

    }


    public static PharmacyDto convertToPharmacyDto(Pharmacy pharmacy) {

        return PharmacyDto.builder()
                .id(pharmacy.getId())
                .pharmacyAddress(pharmacy.getPharmacyAddress())
                .pharmacyName(pharmacy.getPharmacyName())
                .latitude(pharmacy.getLatitude())
                .longitude(pharmacy.getLongitude())
                .build();
    }

}
