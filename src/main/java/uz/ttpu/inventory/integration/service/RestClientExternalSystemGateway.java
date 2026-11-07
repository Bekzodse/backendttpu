package uz.ttpu.inventory.integration.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.domain.ExternalSystemUnavailableException;

@Component
public class RestClientExternalSystemGateway implements ExternalSystemGateway {

    private final RestClient restClient;
    private final boolean liveHttpEnabled;

    public RestClientExternalSystemGateway(
            @Value("${app.integration.live-http-enabled:false}") boolean liveHttpEnabled
    ) {
        this.restClient = RestClient.builder().build();
        this.liveHttpEnabled = liveHttpEnabled;
    }

    @Override
    public ExternalDispatchResponse dispatch(
            ExternalSystemType targetSystem,
            String endpointUrl,
            String httpMethod,
            Map<String, Object> payload
    ) {
        if (!liveHttpEnabled) {
            return new ExternalDispatchResponse(
                    200,
                    "{\"status\":\"ACK\",\"targetSystem\":\"" + targetSystem + "\"}"
            );
        }

        try {
            HttpMethod method = httpMethod != null && !httpMethod.isBlank()
                    ? HttpMethod.valueOf(httpMethod.toUpperCase())
                    : HttpMethod.POST;
            ResponseEntity<String> response = restClient.method(method)
                    .uri(endpointUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
            return new ExternalDispatchResponse(
                    response.getStatusCode().value(),
                    response.getBody()
            );
        } catch (Exception ex) {
            throw new ExternalSystemUnavailableException(
                    "External system " + targetSystem + " (" + endpointUrl + ") failed: " + ex.getMessage(),
                    ex
            );
        }
    }
}
