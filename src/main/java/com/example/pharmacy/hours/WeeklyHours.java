package com.example.pharmacy.hours;

import java.util.ArrayList;
import java.util.List;

// 약국 한 곳의 요일별 영업시간.
// 요일 번호는 공공데이터와 같다: 1=월 ... 7=일, 8=공휴일.
// 시간은 "0900" 같은 HHMM 정수. 닫는 시간이 2400 을 넘거나(예: 2600) 여는 시간보다 작으면(예: 0200) 다음 날 새벽까지 연다는 뜻.
public final class WeeklyHours {

    public static final int HOLIDAY = 8;
    private static final String[] DAY_NAMES = {"", "월", "화", "수", "목", "금", "토", "일", "공휴일"};

    // index 1~8 사용. null 이면 그날은 쉬거나 정보 없음
    private final Slot[] slots = new Slot[9];

    record Slot(int open, int close) {
        // 닫는 시간을 "여는 날 기준" 으로 바꾼다. 0200 -> 2600
        int closeSameDay() {
            return close <= open ? close + 2400 : close;
        }
    }

    public static WeeklyHours empty() {
        return new WeeklyHours();
    }

    public WeeklyHours set(int day, String open, String close) {
        Integer o = parse(open);
        Integer c = parse(close);
        if (day >= 1 && day <= 8 && o != null && c != null) {
            slots[day] = new Slot(o, c);
        }
        return this;
    }

    // 그 요일, 그 시각(HHMM)에 열려 있나?
    // 전날 밤부터 이어지는 영업(예: 일요일 2600 까지 -> 월요일 0130 에 영업 중)도 본다. 공휴일(8)의 전날은 알 수 없어서 보지 않는다.
    public boolean isOpen(int day, int hhmm) {
        Slot today = slots[day];
        if (today != null && today.open() <= hhmm && hhmm < today.closeSameDay()) {
            return true;
        }
        if (day >= 1 && day <= 7) {
            int prevDay = day == 1 ? 7 : day - 1;
            Slot prev = slots[prevDay];
            return prev != null && hhmm + 2400 < prev.closeSameDay();
        }
        return false;
    }

    public boolean hasDay(int day) {
        return slots[day] != null;
    }

    public boolean isEmpty() {
        for (int d = 1; d <= 8; d++) {
            if (slots[d] != null) {
                return false;
            }
        }
        return true;
    }

    // 화면/AI 에 보여 줄 글자. 예: "09:00~22:00", 정보 없으면 "휴무 또는 정보 없음"
    public String label(int day) {
        Slot s = slots[day];
        if (s == null) {
            return "휴무 또는 정보 없음";
        }
        return format(s.open()) + "~" + (s.closeSameDay() > 2400 ? "다음날 " + format(s.closeSameDay() - 2400) : format(s.close()));
    }

    public static String dayName(int day) {
        return DAY_NAMES[day];
    }

    // DB 저장용 한 줄 문자열: "0900-1800|0900-1800|...|" (8칸, 비면 빈칸)
    public String serialize() {
        List<String> parts = new ArrayList<>();
        for (int d = 1; d <= 8; d++) {
            Slot s = slots[d];
            parts.add(s == null ? "" : pad(s.open()) + "-" + pad(s.close()));
        }
        return String.join("|", parts);
    }

    public static WeeklyHours deserialize(String text) {
        WeeklyHours hours = new WeeklyHours();
        if (text == null) {
            return hours;
        }
        String[] parts = text.split("\\|", -1);
        for (int i = 0; i < parts.length && i < 8; i++) {
            String[] oc = parts[i].split("-");
            if (oc.length == 2) {
                hours.set(i + 1, oc[0], oc[1]);
            }
        }
        return hours;
    }

    // AI 가 준 시각 문자열 "2300", "23:00", "930" -> 정수 HHMM. 범위를 벗어나면 null
    public static Integer parseTime(String text) {
        if (text == null) {
            return null;
        }
        Integer t = parse(text.replace(":", ""));
        if (t == null || t % 100 >= 60 || t > 2359) {
            return null;
        }
        return t;
    }

    static Integer parse(String hhmm) {
        if (hhmm == null || !hhmm.strip().matches("\\d{3,4}")) {
            return null;
        }
        return Integer.parseInt(hhmm.strip());
    }

    private static String pad(int hhmm) {
        return String.format("%04d", hhmm);
    }

    private static String format(int hhmm) {
        return String.format("%02d:%02d", hhmm / 100, hhmm % 100);
    }
}
