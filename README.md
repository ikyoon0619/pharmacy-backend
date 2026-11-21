# pharmacy-backend
약국추천 서비스 backend 입니다

# Tech Stack  
- JDK 11  
- Spring Boot 2.6.7  
- Spring Data JPA  
- Gradle  
- Lombok  
- Github  
- Docker  
- NCP  
- Redis  
- MariaDB  
- Spock  
- Testcontainers  

# Feature List
- Spring Data JPA를 이용한 CRUD 메서드 구현하기  
- Spock를 이용한 테스트 코드 작성하기  
- Testcontainers를 이용하여 독립 테스트 환경 구축하기  
- 카카오 주소검색 API 연동하여 주소를 위도, 경도로 변환하기  
- 추천 결과를 카카오 지도 URL로 연동하여 제공하기  
- 공공 데이터를 활용하여 개발하기 (약국 현황 데이터)  
- 도커를 사용하여 다중 컨테이너 애플리케이션 만들기  
- 애플리케이션을 클라우드 서비스에 배포하기  
- Spring retry를 이용하여 재처리 구현하기 (카카오 API의 네트워크 오류 등에 대한 재처리)  
- base64를 이용한 shorten url 개발하기 (길안내 URL)  
- redis를 이용하여 성능 최적화하기  

# 약국 추천 서비스 (pharmacy-backend)

주소를 입력하면 **반경 10km 안에서 가장 가까운 약국 3곳**과 카카오맵 길안내·로드뷰 링크를 알려 주는 Spring Boot 서비스입니다.

**데모:** https://<RENDER-URL>.onrender.com
> 무료 서버라서 15분 동안 접속이 없으면 잠듭니다. 첫 접속은 30초~1분쯤 걸릴 수 있어요.

## 화면 흐름
주소 입력 ──► POST /search ──► 약국 3곳 표 ──► /dir/{code} ──► 카카오맵 길안내


## 기술 스택

| 구분 | 사용 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3, Spring Data JPA, Spring Retry, Thymeleaf |
| DB | H2 (데모), MariaDB (로컬, Docker Compose) |
| 외부 API | 카카오 로컬 API (주소 검색, 카테고리 검색 `PM9`) |
| Test | JUnit 5, Mockito, MockRestServiceServer, `@WebMvcTest` (21개) |
| 배포 | Docker (멀티 스테이지), Render |

## 구조

```
src/main/java/com/example/pharmacy
├── config/      RestClient(카카오 인증 헤더), JPA Auditing
├── kakao/       카카오 API 호출 (재시도 + 실패 시 Recover)
├── direction/   추천 로직, Base62 짧은 URL, 컨트롤러
└── common/      BaseTimeEntity, 예외 처리
```


## 설계 포인트

- **재시도 대상을 구분했습니다.** 네트워크 오류와 5xx는 최대 3번 재시도하고, 401 같은 4xx는 바로 실패 처리합니다. 끝내 실패하면 에러 페이지 대신 "결과 없음"을 보여 줍니다.
- **짧은 길안내 URL을 만듭니다.** 추천 결과를 DB에 저장하고, id를 Base62로 인코딩해 `/dir/21` 같은 짧은 링크를 만듭니다.
- **외부 API 없이 테스트합니다.** `MockRestServiceServer`로 카카오 응답을 흉내 내기 때문에, 키 없이도 테스트가 매번 같은 결과로 돕니다.
- **프로필로 환경을 나눕니다.** `demo`(H2, 기본값)와 `local`(MariaDB)이 있습니다. 키와 비밀번호는 환경변수로만 받습니다.

## 실행 방법

```bash
cp .env.example .env          # KAKAO_REST_API_KEY 입력
set -a; source .env; set +a
./gradlew bootRun             # http://localhost:8080

로컬 MariaDB로 실행하기:

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

테스트:

```bash
./gradlew test
```

## 참고

강의 실습(<약국 길찾기 서비스/Fastcampus-10개 프로젝트로 완성하는 백엔드 웹개발(Java/Spring) 초격차 패키지 Online>)으로 처음 만든 프로젝트입니다. 이후 Spring Boot 3로 다시 구현하면서 구조를 정리했습니다.
- 약국 데이터는 CSV 대신 카카오 카테고리 검색으로 가져옵니다.
- 테스트는 JUnit 5 기반으로 다시 작성했습니다.
- 무료 클라우드에 배포했습니다.