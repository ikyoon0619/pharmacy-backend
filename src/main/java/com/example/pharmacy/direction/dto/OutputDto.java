package com.example.pharmacy.direction.dto;

// 결과 화면 한 줄에 보여 줄 값
public record OutputDto(
        String pharmacyName,
        String pharmacyAddress,
        String distance,
        String directionUrl,
        String roadViewUrl) {
}