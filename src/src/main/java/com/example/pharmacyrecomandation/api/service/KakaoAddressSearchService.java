package com.example.pharmacyrecomandation.api.service;

import com.example.pharmacyrecomandation.api.dto.KakaoApiReponseDto;
import io.netty.util.internal.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAddressSearchService {

    private RestTemplate restTemplate;
    private KakaoUriBuilderService kakaoUriBuilderService;

    @Value("${kakao.rest.api.key}")
    private String kakaoRestApiKey;
    public KakaoApiReponseDto kakaoRequestAddressSearch(String address){

        if(ObjectUtils.isEmpty(address)) return null;

        URI uri = kakaoUriBuilderService.buildUriByAddressSearch(address);

        HttpHeaders header = new HttpHeaders();
        header.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoRestApiKey);
        HttpEntity httpEntity = new HttpEntity<>(header);

        return restTemplate.exchange(uri, HttpMethod.GET, httpEntity, KakaoApiReponseDto.class).getBody();
    }
}
