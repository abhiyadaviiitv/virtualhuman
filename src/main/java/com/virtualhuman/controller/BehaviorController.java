package com.virtualhuman.controller;

import com.virtualhuman.model.BehaviorRequest;
import com.virtualhuman.model.BehaviorResponse;
import com.virtualhuman.service.BehaviorPlannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/behavior")
public class BehaviorController {

    private final BehaviorPlannerService behaviorPlannerService;

    public BehaviorController(BehaviorPlannerService behaviorPlannerService) {
        this.behaviorPlannerService = behaviorPlannerService;
    }

    @PostMapping("/generate")
    public ResponseEntity<BehaviorResponse> generateBehavior(@RequestBody BehaviorRequest request) {
        BehaviorResponse response = behaviorPlannerService.planBehavior(request);
        return ResponseEntity.ok(response);
    }
}
