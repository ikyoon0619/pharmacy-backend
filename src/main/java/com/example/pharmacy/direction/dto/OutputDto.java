package com.example.pharmacy.direction.dto;

// 결과 화면 카드 하나에 보여 줄 값
public record OutputDto(
        String pharmacyName,
        String pharmacyAddress,
        String distance,
        String directionUrl,
        String roadViewUrl,
        String phone,      // 없으면 null -> 전화 버튼을 숨긴다
        String placeUrl,
        String hoursToday, // 예: "오늘(월) 09:00~22:00". 영업시간 데이터가 없으면 null
        Boolean openNow) { // 지금 영업 중이면 true, 아니면 false, 모르면 null

    // 영업시간 정보 없이 만들 때 (예전 코드와 테스트용)
    public OutputDto(String pharmacyName, String pharmacyAddress, String distance,
                     String directionUrl, String roadViewUrl, String phone, String placeUrl) {
        this(pharmacyName, pharmacyAddress, distance, directionUrl, roadViewUrl, phone, placeUrl, null, null);
    }

    public OutputDto withHours(String hoursToday, Boolean openNow) {
        return new OutputDto(pharmacyName, pharmacyAddress, distance, directionUrl, roadViewUrl,
                phone, placeUrl, hoursToday, openNow);
    }

    // tel: 링크용. "02-123-4567" -> "02-123-4567" 그대로 써도 되지만 공백은 뺀다
    public String telLink() {
        return phone == null ? null : "tel:" + phone.replaceAll("\\s", "");
    }
}
