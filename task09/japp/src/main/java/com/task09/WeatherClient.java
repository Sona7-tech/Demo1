package com.task09;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WeatherClient {

    private static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=50.4375"
                    + "&longitude=30.5"
                    + "&current=temperature_2m,wind_speed_10m"
                    + "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m"
                    + "&timezone=Europe%2FKiev";

    private final HttpClient httpClient;

    public WeatherClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String getWeatherForecast() throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WEATHER_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
