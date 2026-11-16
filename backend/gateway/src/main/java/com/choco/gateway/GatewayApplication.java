package com.choco.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single, controlled entry point for every frontend request.
 *
 * <p>Routing, edge authentication and throttling live here so the services behind it can assume a
 * request that reached them already carried a valid token from a known client. The gateway holds no
 * business rules: it never decides what an order costs or whether a PIN code is serviceable, it only
 * forwards to the service that owns that decision.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
