package com.example.pharmacy.direction.service;

import org.springframework.stereotype.Service;

// id(숫자) <-> 짧은 문자열 변환. 예: 125 -> "21"
@Service
public class Base62Service {

    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = CHARS.length();

    public String encode(long id) {
        if (id == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(CHARS.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    public long decode(String encoded) {
        long result = 0;
        for (char c : encoded.toCharArray()) {
            int index = CHARS.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("잘못된 길안내 코드입니다: " + encoded);
            }
            result = result * BASE + index;
        }
        return result;
    }
}