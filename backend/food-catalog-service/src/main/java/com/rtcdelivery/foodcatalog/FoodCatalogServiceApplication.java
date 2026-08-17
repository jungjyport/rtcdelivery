package com.rtcdelivery.foodcatalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class FoodCatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodCatalogServiceApplication.class, args);
    }
}
