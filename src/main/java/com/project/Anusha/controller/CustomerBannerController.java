package com.project.Anusha.controller;

import com.project.Anusha.model.Banner;
import com.project.Anusha.repository.BannerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer/banners")
@CrossOrigin(origins = "*")
public class CustomerBannerController {

    @Autowired
    private BannerRepository bannerRepository;

    @GetMapping
    public ResponseEntity<?> getActiveBanners() {
        // Fetch only active banners targeting CUSTOMER or BOTH
        List<Banner> banners = bannerRepository.findActiveBannersForApp("CUSTOMER");
        return ResponseEntity.ok(Map.of("success", true, "banners", banners));
    }
}
