package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.vo.response.WeatherVO;
import com.example.service.WeatherService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天气服务：QWeather + Redis 缓存。
 */
@RestController
@RequestMapping("/api/forum")
public class WeatherController {

    @Resource
    WeatherService service;

    @GetMapping("/weather")
    public RestBean<WeatherVO> weather(double longitude, double latitude) {
        WeatherVO vo = service.fetchWeather(longitude, latitude);
        return vo == null
                ? RestBean.failure(400, "获取天气数据失败，请联系管理员")
                : RestBean.success(vo);
    }
}
