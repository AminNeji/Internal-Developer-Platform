package idpbackend.idp_backend.controller;


import idpbackend.idp_backend.dto.CreateEnvironmentReq;
import idpbackend.idp_backend.dto.EnvironmentResponse;
import idpbackend.idp_backend.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController

@RequestMapping("/api/environment")
public class EnvironmentController {

    @Autowired
    private EnvironmentService environmentService;
    @Autowired
    private ArgoCdService argoCdService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private CleanupService cleanupService;
    @Autowired
    private GitOpsService gitOpsService;

    EnvironmentController(EnvironmentService environmentService, ArgoCdService argoCdService, TemplateService templateService, CleanupService cleanupService, GitOpsService gitOpsService) {
        this.environmentService = environmentService;
        this.argoCdService = argoCdService;
        this.templateService = templateService;
        this.cleanupService = cleanupService;
        this.gitOpsService = gitOpsService;
    }

    @PostMapping("/create")
    public ResponseEntity<EnvironmentResponse> createEnvironment(@RequestBody CreateEnvironmentReq environmentRequest,
                                                                 @AuthenticationPrincipal OAuth2User user) throws Exception {
        EnvironmentResponse environmentResponse = environmentService.createEnvironment(environmentRequest, user.getAttribute("login"));

        return ResponseEntity.ok(environmentResponse);
    }

    @GetMapping
    public List<EnvironmentResponse> listAll(){
        return environmentService.listAll();
    }

    @GetMapping("/{envid}")
    public ResponseEntity<EnvironmentResponse> getEnvironmentById(@PathVariable String envid) {
        EnvironmentResponse environment = environmentService.getEnvironmentByEnvId(envid);
        if (environment != null) {
            return ResponseEntity.ok(environment);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @DeleteMapping("/{envid}")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable String envid) throws Exception {
        environmentService.destroy(envid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{envId}/status")
    public ResponseEntity<ArgoCdService.ApplicationStatus> getEnvironmentStatus(@PathVariable String envId) {
        ArgoCdService.ApplicationStatus status = environmentService.refreshStatus(envId);
        if (status != null) {
            return ResponseEntity.ok(status);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
