package com.project.Anusha.service;

import com.project.Anusha.model.Banner;
import com.project.Anusha.repository.BannerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BannerService {

    @Autowired
    private BannerRepository bannerRepository;

    public List<Banner> getActiveBanners() {
        return bannerRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }
}
