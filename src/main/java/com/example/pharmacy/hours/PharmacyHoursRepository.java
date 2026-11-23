package com.example.pharmacy.hours;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PharmacyHoursRepository extends JpaRepository<PharmacyHours, String> {

    // 위도/경도 사각형 안의 약국. 정확한 거리는 서비스에서 다시 계산한다.
    List<PharmacyHours> findByLatitudeBetweenAndLongitudeBetween(
            double minLat, double maxLat, double minLng, double maxLng);
}
