package com.steven.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Iterator;

@Slf4j
@Component
public class AmapTools {

    private final WebClient http;
    private final String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AmapTools(@Value("${amap.api.key:${AMAP_API_KEY:${AMAP_MCP_KEY:}}}") String apiKey, WebClient.Builder builder) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.http = builder.baseUrl("https://restapi.amap.com").build();
        if (this.apiKey.isEmpty()) {
            log.warn("AMap API key is empty. Please set AMAP_API_KEY or amap.api.key");
        } else {
            String suffix = this.apiKey.length() >= 4 ? this.apiKey.substring(this.apiKey.length() - 4) : "****";
            log.debug("AMap API key injected. length={}, suffix=****{}", this.apiKey.length(), suffix);
        }
    }

    @Tool(description = "查询城市天气（含未来预报），入参 city（中文城市名或 adcode）")
    public String maps_weather(@ToolParam(description = "城市名或adcode，如：北京 或 110000") String city) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置高德API Key。请设置环境变量 AMAP_API_KEY，或在 application.yaml 配置 amap.api.key";
        }
        String json = http.get()
                .uri(uri -> uri.path("/v3/weather/weatherInfo")
                        .queryParam("city", city)
                        .queryParam("extensions", "all")
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(json);
            log.debug("Amap weather(all) raw: {}", json);
            if (!"1".equals(root.path("status").asText())) {
                return "天气查询失败：" + root.path("info").asText();
            }
            JsonNode forecasts = root.path("forecasts");
            if (forecasts.isArray() && forecasts.size() > 0) {
                JsonNode f = forecasts.get(0);
                String cityName = f.path("city").asText("");
                JsonNode casts = f.path("casts");
                StringBuilder sb = new StringBuilder();
                sb.append("【").append(cityName).append("】未来天气：\n");
                int count = 0;
                for (Iterator<JsonNode> it = casts.iterator(); it.hasNext() && count < 4; count++) {
                    JsonNode c = it.next();
                    String date = c.path("date").asText("");
                    String dayWeather = c.path("dayweather").asText("");
                    String nightWeather = c.path("nightweather").asText("");
                    String dayTemp = c.path("daytemp").asText("");
                    String nightTemp = c.path("nighttemp").asText("");
                    String weekday = c.path("week").asText("");
                    sb.append(String.format("%s(周%s)：白天%s %s℃ / 夜间%s %s℃\n",
                            date, weekday, dayWeather, dayTemp, nightWeather, nightTemp));
                }
                return sb.toString();
            }
            // 预报为空时，回退实时天气
            String liveJson = http.get()
                    .uri(uri -> uri.path("/v3/weather/weatherInfo")
                            .queryParam("city", city)
                            .queryParam("extensions", "base")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("Amap weather(base) raw: {}", liveJson);
            JsonNode liveRoot = objectMapper.readTree(liveJson);
            if ("1".equals(liveRoot.path("status").asText())) {
                JsonNode lives = liveRoot.path("lives");
                if (lives.isArray() && lives.size() > 0) {
                    JsonNode l = lives.get(0);
                    String cityName = l.path("city").asText("");
                    String weather = l.path("weather").asText("");
                    String temp = l.path("temperature").asText("");
                    String wind = l.path("winddirection").asText("") + "风";
                    String humidity = l.path("humidity").asText("") + "%";
                    return String.format("【%s】当前：%s %s℃，%s，湿度%s", cityName, weather, temp, wind, humidity);
                }
            }
            return "未获取到天气数据";
        } catch (Exception e) {
            return "天气数据解析失败：" + e.getMessage();
        }
    }

    @Tool(description = "周边POI检索，入参 keywords/location/radius(可选，默认2000米)")
    public String maps_around_search(
            @ToolParam(description = "关键词，如：电影院/美食") String keywords,
            @ToolParam(description = "经纬度，lng,lat，如：116.466485,39.995197") String location,
            @ToolParam(description = "半径（米），默认2000", required = false) String radius
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置高德API Key。请设置环境变量 AMAP_API_KEY，或在 application.yaml 配置 amap.api.key";
        }
        String r = (radius == null || radius.isBlank()) ? "2000" : radius;
        String json = http.get()
                .uri(uri -> uri.path("/v3/place/around")
                        .queryParam("keywords", keywords)
                        .queryParam("location", location)
                        .queryParam("radius", r)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(json);
            log.debug("Amap around raw: {}", json);
            if (!"1".equals(root.path("status").asText())) {
                return "周边检索失败：" + root.path("info").asText();
            }
            JsonNode pois = root.path("pois");
            if (!pois.isArray() || pois.size() == 0) {
                return "未找到相关地点";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("附近%s（%sm 内）候选：\n", keywords, r));
            int limit = Math.min(5, pois.size());
            for (int i = 0; i < limit; i++) {
                JsonNode p = pois.get(i);
                String name = p.path("name").asText("");
                String addr = p.path("address").asText("");
                String loc = p.path("location").asText("");
                sb.append(String.format("%d. %s — %s — %s\n", i + 1, name, addr, loc));
            }
            return sb.toString();
        } catch (Exception e) {
            return "周边检索数据解析失败：" + e.getMessage();
        }
    }

    @Tool(description = "地理编码（地址转坐标），入参 address/city(可选)")
    public String maps_geocode(
            @ToolParam(description = "地址，如：北京市朝阳区望京SOHO") String address,
            @ToolParam(description = "城市（可选），如：北京", required = false) String city
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置高德API Key。请设置环境变量 AMAP_API_KEY，或在 application.yaml 配置 amap.api.key";
        }
        String json = http.get()
                .uri(uri -> {
                    var builder = uri.path("/v3/geocode/geo")
                            .queryParam("address", address)
                            .queryParam("key", apiKey);
                    if (city != null && !city.isBlank()) {
                        builder.queryParam("city", city);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(json);
            log.debug("Amap geocode raw: {}", json);
            if (!"1".equals(root.path("status").asText())) {
                return "地理编码失败：" + root.path("info").asText();
            }
            JsonNode list = root.path("geocodes");
            if (list.isArray() && list.size() > 0) {
                JsonNode g = list.get(0);
                String loc = g.path("location").asText("");
                String level = g.path("level").asText("");
                return String.format("%s → 坐标：%s（level=%s）", address, loc, level);
            }
            return "未找到对应坐标";
        } catch (Exception e) {
            return "地理编码数据解析失败：" + e.getMessage();
        }
    }
     @Tool(description = "驾车路线规划，入参 origin/destination（lng,lat），可选 strategy(0-19)")
    public String maps_direction_driving(
            @ToolParam(description = "起点，经纬度 lng,lat") String origin,
            @ToolParam(description = "终点，经纬度 lng,lat") String destination,
            @ToolParam(description = "策略，可选，默认0 https://lbs.amap.com/api/webservice/guide/api/newroute#driving", required = false) String strategy
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置高德API Key。请设置环境变量 AMAP_API_KEY，或在 application.yaml 配置 amap.api.key";
        }
        String s = (strategy == null || strategy.isBlank()) ? "0" : strategy.trim();
        String json = http.get()
                .uri(uri -> uri.path("/v3/direction/driving")
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .queryParam("strategy", s)
                        .queryParam("extensions", "base")
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(json);
            log.debug("Amap driving raw: {}", json);
            if (!"1".equals(root.path("status").asText())) {
                return "驾车路线规划失败：" + root.path("info").asText();
            }
            JsonNode path = root.path("route").path("paths");
            if (!path.isArray() || path.size() == 0) {
                return "未找到合适的驾车路线";
            }
            JsonNode best = path.get(0);
            String distanceM = best.path("distance").asText("0");
            String durationS = best.path("duration").asText("0");
            double km = 0.0;
            try { km = Integer.parseInt(distanceM) / 1000.0; } catch (Exception ignore) {}
            int minutes = 0;
            try { minutes = (int) Math.round(Integer.parseInt(durationS) / 60.0); } catch (Exception ignore) {}
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("驾车：约 %.1f 公里，约 %d 分钟。\n", km, minutes));
            JsonNode steps = best.path("steps");
            if (steps.isArray() && steps.size() > 0) {
                int limit = Math.min(5, steps.size());
                for (int i = 0; i < limit; i++) {
                    JsonNode st = steps.get(i);
                    String road = st.path("road").asText("");
                    String instruction = st.path("instruction").asText("");
                    sb.append(String.format("%d. %s %s\n", i + 1, road, instruction));
                }
                if (steps.size() > limit) sb.append("...");
            }
            return sb.toString();
        } catch (Exception e) {
            return "驾车路线数据解析失败：" + e.getMessage();
        }
    }

    @Tool(description = "步行路线规划，入参 origin/destination（lng,lat）")
    public String maps_direction_walking(
            @ToolParam(description = "起点，经纬度 lng,lat") String origin,
            @ToolParam(description = "终点，经纬度 lng,lat") String destination
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置高德API Key。请设置环境变量 AMAP_API_KEY，或在 application.yaml 配置 amap.api.key";
        }
        String json = http.get()
                .uri(uri -> uri.path("/v3/direction/walking")
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(json);
            log.debug("Amap walking raw: {}", json);
            if (!"1".equals(root.path("status").asText())) {
                return "步行路线规划失败：" + root.path("info").asText();
            }
            JsonNode path = root.path("route").path("paths");
            if (!path.isArray() || path.size() == 0) {
                return "未找到合适的步行路线";
            }
            JsonNode best = path.get(0);
            String distanceM = best.path("distance").asText("0");
            String durationS = best.path("duration").asText("0");
            double km = 0.0;
            try { km = Integer.parseInt(distanceM) / 1000.0; } catch (Exception ignore) {}
            int minutes = 0;
            try { minutes = (int) Math.round(Integer.parseInt(durationS) / 60.0); } catch (Exception ignore) {}
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("步行：约 %.1f 公里，约 %d 分钟。\n", km, minutes));
            JsonNode steps = best.path("steps");
            if (steps.isArray() && steps.size() > 0) {
                int limit = Math.min(6, steps.size());
                for (int i = 0; i < limit; i++) {
                    JsonNode st = steps.get(i);
                    String instruction = st.path("instruction").asText("");
                    sb.append(String.format("%d. %s\n", i + 1, instruction));
                }
                if (steps.size() > limit) sb.append("...");
            }
            return sb.toString();
        } catch (Exception e) {
            return "步行路线数据解析失败：" + e.getMessage();
        }
    }

    @Tool(description = "公交路线规划（城市内/跨城），入参 origin/destination（lng,lat），city(城市名或adcode)")
    public String maps_direction_transit(
            @ToolParam(description = "起点，经纬度 lng,lat") String origin,
            @ToolParam(description = "终点，经纬度 lng,lat") String destination,
            @ToolParam(description = "城市（名或adcode），如：北京 或 110000") String city,
            @ToolParam(description = "策略，可选，0最快 1少换乘 2少步行 3不坐地铁", required = false) String strategy
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置高德API Key。请设置环境变量 AMAP_API_KEY，或在 application.yaml 配置 amap.api.key";
        }
        String s = (strategy == null || strategy.isBlank()) ? "0" : strategy.trim();
        String json = http.get()
                .uri(uri -> uri.path("/v3/direction/transit/integrated")
                        .queryParam("origin", origin)
                        .queryParam("destination", destination)
                        .queryParam("city", city)
                        .queryParam("strategy", s)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(json);
            log.debug("Amap transit raw: {}", json);
            if (!"1".equals(root.path("status").asText())) {
                return "公交路线规划失败：" + root.path("info").asText();
            }
            JsonNode transits = root.path("route").path("transits");
            if (!transits.isArray() || transits.size() == 0) {
                return "未找到合适的公交路线";
            }
            JsonNode best = transits.get(0);
            String durationS = best.path("duration").asText("0");
            int minutes = 0;
            try { minutes = (int) Math.round(Integer.parseInt(durationS) / 60.0); } catch (Exception ignore) {}
            String cost = best.path("cost").asText("");
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("公交：约 %d 分钟，票价约 %s 元。\n", minutes, cost.isEmpty() ? "-" : cost));
            JsonNode segments = best.path("segments");
            if (segments.isArray() && segments.size() > 0) {
                int limit = Math.min(6, segments.size());
                for (int i = 0; i < limit; i++) {
                    JsonNode seg = segments.get(i);
                    JsonNode bus = seg.path("bus").path("buslines");
                    if (bus.isArray() && bus.size() > 0) {
                        String name = bus.get(0).path("name").asText("");
                        sb.append(String.format("%d. 乘坐 %s\n", i + 1, name));
                    } else {
                        String walk = seg.path("walking").path("distance").asText("");
                        if (!walk.isEmpty()) {
                            sb.append(String.format("%d. 步行 %s 米\n", i + 1, walk));
                        }
                    }
                }
                if (segments.size() > limit) sb.append("...");
            }
            return sb.toString();
        } catch (Exception e) {
            return "公交路线数据解析失败：" + e.getMessage();
        }
    }
}
