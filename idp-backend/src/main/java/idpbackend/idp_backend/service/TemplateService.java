package idpbackend.idp_backend.service;

import idpbackend.idp_backend.dto.CreateEnvironmentReq;
import idpbackend.idp_backend.dto.ServiceConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemplateService {

    @Value("${idp.domain}")
    private String domain;

    public Map<String, String> generateTemplate(String envId, CreateEnvironmentReq envReq) throws Exception {

        List<Map<String, Object>> services = new ArrayList<>();
        if (envReq.getServices() != null) {
            for (ServiceConfig svc : envReq.getServices()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("serviceName", svc.getServiceName());
                entry.put("image", svc.getImage());
                entry.put("port", svc.getPort());
                entry.put("path", svc.getPath());
                entry.put("healthPath", svc.getHealthPath());
                entry.put("env", svc.getEnv());
                services.add(entry);
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        // every environment gets its own subdomain on the shared load balancer
        values.put("ingress", Map.of("host", envId + "." + domain));
        values.put("services", services);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        String valuesYaml = new Yaml(options).dump(values);

        Path outputFolder = Path.of("generated-manifests", envId);
        Files.createDirectories(outputFolder);
        Files.writeString(outputFolder.resolve("values.yaml"), valuesYaml, StandardCharsets.UTF_8);

        return Map.of("values.yaml", valuesYaml);
    }
}
