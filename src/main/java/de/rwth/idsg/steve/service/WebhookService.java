package de.rwth.idsg.steve.service;

import com.google.common.base.Strings;
import de.rwth.idsg.steve.SteveException;
import de.rwth.idsg.steve.repository.SteveSettingsRepository;
import de.rwth.idsg.steve.repository.dto.WebhookSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.json.JSONObject;


@Service
public class WebhookService {
    @Autowired private SteveSettingsRepository settingsRepository;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    private WebhookSettings settings;

    // @PostConstruct
    // public void loadSettingsFromDB() {
    //     writeLock.lock();
    //     try {
    //         settings = settingsRepository.getFlowSettings();
    //     } finally {
    //         writeLock.unlock();
    //     }
    // }

    public WebhookSettings getSettings() {
        readLock.lock();
        try {
            return this.settings;
        } finally {
            readLock.unlock();
        }
    }

    private RestTemplate restTemplate;

    private static final int API_TIMEOUT_IN_MILLIS = 4_000;
    
    @PostConstruct
    private void init() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setReadTimeout(API_TIMEOUT_IN_MILLIS);
        factory.setConnectTimeout(API_TIMEOUT_IN_MILLIS);

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setPropertyNamingStrategy(new PropertyNamingStrategy.SnakeCaseStrategy());

        restTemplate = new RestTemplate(Collections.singletonList(new MappingJackson2HttpMessageConverter(mapper)));
        restTemplate.setRequestFactory(factory);
    }

    public void sendAsync(String endpoint, JSONObject body) {

        writeLock.lock();
        try {
            settings = settingsRepository.getWebhookSettings();
        } finally {
            writeLock.unlock();
        }

        WebhookSettings settings1 = getSettings();
        if(!settings1.isWebhookEnabled()) return;
        String webhook_link = settings1.getWebhook();
        // System.out.println(webhook_link);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-API-KEY", "6ovuIrVd1vVGdVnE5TtwTxPhlzZf+Dmkf6mIQw6IBMk=");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity entity = new HttpEntity(body.toMap(), headers);
        // System.out.println(body);
        
        try {
            String response = new String();
            response = restTemplate.postForObject(webhook_link + "/" + endpoint, entity, String.class);
            System.out.println(response);
        } catch (RestClientException e) {
            //System.out.println("WebhookService Error");
            //System.out.println(e);
        }
    }

}


       

