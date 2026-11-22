package com.example.pharmacy.direction.entity;

import com.example.pharmacy.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자가 입력한 주소와 추천된 약국 1곳의 정보를 저장한다.
// 저장된 id 로 짧은 길안내 URL(/dir/{encodedId})을 만든다.
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Direction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String inputAddress;
    private double inputLatitude;
    private double inputLongitude;

    private String targetPharmacyName;
    private String targetAddress;
    private double targetLatitude;
    private double targetLongitude;

    private double distanceKm;

    private String targetPhone;     // 방문 전 전화 확인용
    private String targetPlaceUrl;  // 카카오맵 장소 상세
}