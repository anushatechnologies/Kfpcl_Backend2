package com.project.Anusha.controller;

import com.project.Anusha.model.Banner;
import com.project.Anusha.service.BannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/banners")
@CrossOrigin(origins = "*")
public class BannerController {

    @Autowired
    private BannerService bannerService;

    @GetMapping
    public ResponseEntity<List<Banner>> getBanners() {
        return ResponseEntity.ok(bannerService.getActiveBanners());
    }
}
