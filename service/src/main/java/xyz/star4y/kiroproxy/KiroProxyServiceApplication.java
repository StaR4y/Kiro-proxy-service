package xyz.star4y.kiroproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KiroProxyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KiroProxyServiceApplication.class, args);
    }
}
