package com.project.Anusha.controller;

import com.project.Anusha.model.Banner;
import com.project.Anusha.repository.BannerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/website/banners")
@CrossOrigin(origins = "*")
public class WebsiteBannerController {

    @Autowired
    private BannerRepository bannerRepository;

    @GetMapping
    public ResponseEntity<?> getActiveWebsiteBanners() {
        List<Banner> banners = bannerRepository.findActiveWebsiteBanners();
        return ResponseEntity.ok(Map.of("success", true, "banners", banners));
    }
}
