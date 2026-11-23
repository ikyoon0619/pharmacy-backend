package com.example.pharmacy.hours;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 국립중앙의료원 전국 약국 정보 한 줄 (영업시간 포함).
// 위도로 범위 검색을 자주 하니 latitude 에 인덱스를 둔다.
@Entity
@Table(name = "pharmacy_hours", indexes = @Index(name = "idx_pharmacy_hours_lat", columnList = "latitude"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PharmacyHours {

    @Id
    private String hpid;          // 기관 ID (공공데이터 고유값)

    private String name;
    private String address;
    private String tel;
    private double latitude;
    private double longitude;

    @Column(length = 120)
    private String weeklyHours;   // WeeklyHours.serialize() 결과

    public WeeklyHours hours() {
        return WeeklyHours.deserialize(weeklyHours);
    }
}
