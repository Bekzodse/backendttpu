package uz.ttpu.inventory.common.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class OpenApiContractController {

    @GetMapping(value = "/openapi.yaml", produces = "application/vnd.oai.openapi")
    public void getOpenApiYaml(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/v3/api-docs.yaml").forward(request, response);
    }
}
