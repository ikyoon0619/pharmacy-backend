package com.example.pharmacyrecomandation.pharmacy.repository

import com.example.pharmacyrecomandation.AbstractIntegrationContainerBaseTest
import com.example.pharmacyrecomandation.pharmacy.entity.Pharmacy
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDateTime

class PharmacyRepositoryTest extends AbstractIntegrationContainerBaseTest{

   @Autowired
   private PharmacyRepository pharmacyRepository;

    def "pharmacyRepository save"(){

        given:
        String address = "서울 특별시 성북구 종암동"
        String name = "은혜 약국"
        double latitude = 36.11
        double longitude = 128.11

        def pharmacy = Pharmacy.builder()
        .pharmacyAddress(address)
        .pharmacyName(name)
        .latitude(latitude)
        .longitude(longitude)
        .build()

        when:
        def result = pharmacyRepository.save(pharmacy)

        then:
        List<Pharmacy> pharmacyList = pharmacyRepository.findAll()

        result.getPharmacyAddress() == address
        result.getPharmacyName() == name
        result.getLatitude() == latitude
        result.getLongitude() == longitude

    }

    def "BaseTimeEntity 등록"(){
        given:
        String address = "서울 특별시 성북구 종암동"
        String name = "은혜 약국"

        def pharmacy = Pharmacy.builder()
                .pharmacyAddress(address)
                .pharmacyName(name)
                .build()

        when:
        def result = pharmacyRepository.save(pharmacy)

        then:
        def createdDate = result.getCreatedDate()
        createdDate != null
    }
}
