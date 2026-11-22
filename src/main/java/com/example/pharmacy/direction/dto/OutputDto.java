package com.example.pharmacy.direction.dto;

// 결과 화면 카드 하나에 보여 줄 값
public record OutputDto(
        String pharmacyName,
        String pharmacyAddress,
        String distance,
        String directionUrl,
        String roadViewUrl,
        String phone,      // 없으면 null -> 전화 버튼을 숨긴다
        String placeUrl) {

    // tel: 링크용. "02-123-4567" -> "02-123-4567" 그대로 써도 되지만 공백은 뺀다
    public String telLink() {
        return phone == null ? null : "tel:" + phone.replaceAll("\\s", "");
    }
}
