package uz.ttpu.inventory.integration.service;

import java.util.Map;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;

public interface ExternalSystemGateway {

    ExternalDispatchResponse dispatch(
            ExternalSystemType targetSystem,
            String endpointUrl,
            String httpMethod,
            Map<String, Object> payload
    );
}
