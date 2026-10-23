package com.example.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.entity.vo.response.WeatherVO;
import com.example.service.WeatherService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {
    @Resource
    StringRedisTemplate template;
    @Resource
    RestTemplate rest;
    @Value("${spring.weather.key}")
    String weatherKey;
    @Override
    public WeatherVO fetchWeather(double longitude, double latitude) {
        return fetchFromCache(longitude,latitude);
    }

    private WeatherVO fetchFromCache(double longitude, double latitude){
        JSONObject geo =this.decompressStringToJson(rest.getForObject(
                "https://geoapi.qweather.com/v2/city/lookup?location=" + longitude + "," + latitude + "&key=" + weatherKey, byte[].class));
        if (geo ==null)return null;
        JSONObject location=geo.getJSONArray("location").getJSONObject(0);
        int id= location.getInteger("id");
        String key = Const.FORUM_WEATHER_CACHE+id;
        String cache= template.opsForValue().get(key);
        if (cache !=null){
            log.info("从缓存中获取了天气信息");
            return JSONObject.parseObject(cache).to(WeatherVO.class);
        } else {
            WeatherVO vo = this.fetchFromAPI(id,location);
            log.info("向API请求了天气信息");
            if (vo == null) return null;
            template.opsForValue().set(key,JSONObject.from(vo).toJSONString(),1, TimeUnit.HOURS);
            return vo;
        }
    }

    private WeatherVO fetchFromAPI(int id, JSONObject location){
        WeatherVO vo = new WeatherVO();
        vo.setLocation(location);
        JSONObject now = this.decompressStringToJson(rest.getForObject(
                "https://devapi.qweather.com/v7/weather/now?location="+id+"&key="+weatherKey, byte[].class));
        if(now == null) return null;
        vo.setNow(now.getJSONObject("now"));
        JSONObject hourly = this.decompressStringToJson(rest.getForObject(
                "https://devapi.qweather.com/v7/weather/24h?location="+id+"&key="+weatherKey, byte[].class));
        if(hourly == null) return null;
        vo.setHourly(new JSONArray(hourly.getJSONArray("hourly").stream().limit(5).toList()));
        return vo;
    }

    private JSONObject decompressStringToJson(byte[] data){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
            byte[] buffer = new byte[1024];
            int read;
            while((read = gzip.read(buffer))!=-1)
                stream.write(buffer,0,read);
            gzip.close();
            stream.close();
            return JSONObject.parseObject(stream.toString());
        }catch (IOException e){
            return null;
        }
    }
}
