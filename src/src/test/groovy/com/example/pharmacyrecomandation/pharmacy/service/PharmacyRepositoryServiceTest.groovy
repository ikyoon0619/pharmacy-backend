package com.example.pharmacyrecomandation.pharmacy.service

import com.example.pharmacyrecomandation.AbstractIntegrationContainerBaseTest
import com.example.pharmacyrecomandation.pharmacy.entity.Pharmacy
import com.example.pharmacyrecomandation.pharmacy.repository.PharmacyRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PharmacyRepositoryServiceTest extends AbstractIntegrationContainerBaseTest {

    @Autowired
    PharmacyRepositoryService pharmacyRepositoryService;

    @Autowired
    PharmacyRepository pharmacyRepository;

    def setup(){
        pharmacyRepository.deleteAll();
    }

    def "PharmacyRepository update - dirty checking success"(){
        given:
        String inputAddress = "서울 특별시 성북구 종암동"
        String modifiedAddress = "서울 광진구 구의동"
        String name = "은혜 약국"

        when:
        def pharmacy = Pharmacy.builder()
                .pharmacyAddress(inputAddress)
                .pharmacyName(name)
                .build()

        def entity = pharmacyRepository.save(pharmacy)
            pharmacyRepositoryService.updateAddress(entity.getId(), modifiedAddress);

        then:
        def result = pharmacyRepository.findAll()[0];

        result.getPharmacyAddress() == modifiedAddress;

    }
}
