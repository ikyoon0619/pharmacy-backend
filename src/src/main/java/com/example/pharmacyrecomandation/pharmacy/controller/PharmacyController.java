package com.example.pharmacyrecomandation.pharmacy.controller;

import com.example.pharmacyrecomandation.api.dto.KakaoApiReponseDto;
import com.example.pharmacyrecomandation.api.service.KakaoAddressSearchService;
import com.example.pharmacyrecomandation.pharmacy.entity.Pharmacy;
import com.example.pharmacyrecomandation.pharmacy.service.PharmacySearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PharmacyController {

    KakaoAddressSearchService kakaoAddressSearchService;

    @GetMapping(value = "/address")
    private List<Pharmacy> getPharmacy(String address){
        KakaoApiReponseDto kakaoApiReponseDto = kakaoAddressSearchService.kakaoRequestAddressSearch(address);

    }
}
