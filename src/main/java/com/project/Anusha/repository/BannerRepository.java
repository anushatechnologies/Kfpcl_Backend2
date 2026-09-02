package com.project.Anusha.repository;

import com.project.Anusha.model.Banner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {
    List<Banner> findByIsActiveOrderByDisplayOrderAsc(Boolean isActive);

    /** Active banners for a specific app — used by CUSTOMER and DELIVERY endpoints */
    List<Banner> findByIsActiveAndTargetAppOrderByDisplayOrderAsc(Boolean isActive, String targetApp);

    /** Active banners that target a specific app OR target BOTH.
     *  NULL / empty targetApp is treated as CUSTOMER (legacy rows created before the column existed). */
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT b FROM Banner b WHERE b.isActive = true " +
        "AND (b.targetApp = :targetApp " +
        "     OR b.targetApp = 'BOTH' " +
        "     OR (:targetApp = 'CUSTOMER' AND (b.targetApp IS NULL OR b.targetApp = ''))) " +
        "ORDER BY b.displayOrder ASC"
    )
    List<Banner> findActiveBannersForApp(@org.springframework.data.repository.query.Param("targetApp") String targetApp);

    /** Active banners for the website storefront.
     *  Only explicit WEBSITE-targeted banners are returned here. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT b FROM Banner b WHERE b.isActive = true " +
        "AND b.targetApp = 'WEBSITE' " +
        "ORDER BY b.displayOrder ASC"
    )
    List<Banner> findActiveWebsiteBanners();
}
