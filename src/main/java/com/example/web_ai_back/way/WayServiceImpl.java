package com.example.web_ai_back.way;

import com.example.web_ai_back.gps.Gps;
import lombok.SneakyThrows;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Service
public class WayServiceImpl implements WayService {
    @Value("${TMAP_URL}")
    private String tmap_way_url; // 티맵 URL
    @Value("${TMAP_APPKEY}")
    private String tmap_apikey; // 티맵 API KEY

    @SneakyThrows
    public List<WayDto> findWay(double startX, double startY, double endX, double endY, String startName, String endName, Number option) { // 티맵 도보 길찾기
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(tmap_way_url);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);


        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) // memory size를 제한없음으로 바꿈
                .build();

        WebClient wc = WebClient.builder().uriBuilderFactory(factory).baseUrl(tmap_way_url).exchangeStrategies(exchangeStrategies).build();


        String encodedStartName = URLEncoder.encode(startName, "UTF-8");
        String encodedEndName = URLEncoder.encode(endName, "UTF-8");


        ResponseEntity<String> result = wc.get()
                .uri(uriBuilder -> uriBuilder.path("/tmap/routes/pedestrian")
                        .queryParam("startX", startX) //시작 경도
                        .queryParam("startY", startY) // 시작 위도
                        .queryParam("endX", endX) // 끝 경도
                        .queryParam("endY", endY) // 끝 위도
                        .queryParam("startName", encodedStartName) // 출발지 이름
                        .queryParam("endName", encodedEndName) // 도착지 이름
                        .queryParam("searchOption", option) // 경로 탐색 옵션
                        // 0:추천(기본값) / 4:추천+대로우선 / 10:최단 / 30: 최단거리+계단제외
                        .queryParam("appKey", tmap_apikey) // api appKey
                        .build())
                .retrieve() //response 불러옴
                .toEntity(String.class)
                .block();


        JSONParser parser = new JSONParser();
        JSONObject object = (JSONObject) parser.parse(result.getBody());
        JSONArray features = (JSONArray) object.get("features");

        List<WayDto> dtos = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            JSONObject array = (JSONObject) features.get(i);
            JSONObject geometry = (JSONObject) array.get("geometry");
            JSONObject properties = (JSONObject) array.get("properties");
            WayDto wayDto = new WayDto();

            // 좌표 배열 자르기
            JSONArray coordinates = (JSONArray) geometry.get("coordinates");
            for (int j = 0; j < coordinates.size(); j++) {
                if (geometry.get("type").equals("Point") && coordinates.get(j).getClass().getName() == "java.lang.Double") { // [경도,위도] 이런 경우
                    Double array1 = (Double) coordinates.get(j);
                    wayDto.setPointLongitude(array1);
                    j += 1;
                    wayDto.setPointLatitude((Double) coordinates.get(j));
                    continue;
                } else if (geometry.get("type").equals("LineString") && coordinates.get(j).getClass().getName() == "org.json.simple.JSONArray") { // 배열 안에 배열 있을 경우
                    ArrayList<Gps> lineArray = new ArrayList<Gps>();
                    for (int k = 0; k < coordinates.size(); k++) {
                        JSONArray lArray = (JSONArray) coordinates.get(k);
                        Gps gps = new Gps();
                        Double array2 = (Double) lArray.get(0);
                        gps.setLongitude(array2);
                        gps.setLatitude((Double) lArray.get(1));
                        lineArray.add(gps);

                    }
                    wayDto.setLinePointArray(lineArray);
                }
                break;
            }

            // properties 안의 value들 얻기
            if (geometry.get("type").equals("Point")) { // point(안내지점)
                if (i == 0) {
                    Number totalDistance = (Number) properties.get("totalDistance"); // 총 거리
                    Number totalTime = (Number) properties.get("totalTime"); // 총 소요시간
                    wayDto.setTotalDistance(totalDistance);
                    wayDto.setTotalTime(totalTime);
                }
                Long pointIndex = (Long) properties.get("pointIndex"); // 안내지점 순번
                String description = (String) properties.get("description"); // 길 안내 정보
                Number turnType = (Number) properties.get("turnType"); //회전정보
                String pointType = (String) properties.get("pointType"); //안내지점 구분
                String facilityType = (String) properties.get("facilityType"); //시설물 구분
                if(facilityType.equals("17")){
                    wayDto.setIsStair(Boolean.TRUE);
                }
                else{
                    wayDto.setIsStair(Boolean.FALSE);
                }
                wayDto.setPointIndex(pointIndex);
                wayDto.setPointDescription(description);
                wayDto.setTurnType(turnType);
                wayDto.setPointType(pointType);
                wayDto.setPointFacilityType(facilityType);
            } else { // line(구간)
                Long lineIndex = (Long) properties.get("lineIndex"); // 구간 순번
                String description = (String) properties.get("description"); // 길 안내 정보
                Number distance = (Number) properties.get("distance"); // 구간거리
                Number time = (Number) properties.get("time");  // 구간 소요시간
                Number roadType = (Number) properties.get("roadType"); //도로 타입 정보
                String facilityType = (String) properties.get("facilityType"); //시설물 구분
                if(facilityType.equals("17")){
                    wayDto.setIsStair(Boolean.TRUE);
                }
                else{
                    wayDto.setIsStair(Boolean.FALSE);
                }
                wayDto.setLineIndex(lineIndex);
                wayDto.setLineDescription(description);
                wayDto.setDistance(distance);
                wayDto.setTime(time);
                wayDto.setRoadType(roadType);
                wayDto.setLineFacilityType(facilityType);
            }
            dtos.add(i, wayDto);
        }
        return dtos;
    }
}
