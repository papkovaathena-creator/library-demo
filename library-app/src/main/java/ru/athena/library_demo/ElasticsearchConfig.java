package ru.athena.library_demo;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;

@Configuration
public class ElasticsearchConfig {

    ElasticsearchTransport elasticsearchTransport;

    ElasticsearchClient elasticsearchClient;

    @Bean
    public ElasticsearchClient getElasticsearchClient() {
        ElasticsearchClient client = new ElasticsearchClient(getElasticsearchTransport());
        System.out.println("---------------------------");
        System.out.println("---------------------------");
        try {
            System.out.println("exists -- " + client.cluster().health());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("---------------------------");
        System.out.println("---------------------------");
        return client;
    }

    @Bean
    public ElasticsearchTransport getElasticsearchTransport() {
        return new RestClientTransport(
                getRestClient(), new JacksonJsonpMapper());
    }

    @Bean
    public RestClient getRestClient() {
        return RestClient.builder(new HttpHost("172.17.0.1", 9200, "http")).build();
    }
}
