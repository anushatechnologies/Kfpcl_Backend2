package com.project.Anusha.config;

import com.project.Anusha.model.*;
import com.project.Anusha.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BannerRepository bannerRepository;

    @Override
    public void run(String... args) throws Exception {
        if (categoryRepository.count() == 0) {
            Category grains = categoryRepository.save(new Category("Grains & Pulses", "https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b", 1, true));
            Category spices = categoryRepository.save(new Category("Spices & Condiments", "https://images.unsplash.com/photo-1596040033229-a9821ebd058d", 2, true));
            Category fruits = categoryRepository.save(new Category("Fresh Fruits & Vegetables", "https://images.unsplash.com/photo-1610832958506-aa56368176cf", 3, true));

            Subcategory basmati = subcategoryRepository.save(new Subcategory(grains, "Basmati Rice", "https://images.unsplash.com/photo-1586201375761-83865001e31c"));
            Subcategory sonaMasoori = subcategoryRepository.save(new Subcategory(grains, "Sona Masoori", "https://images.unsplash.com/photo-1536304929831-ee1ca9d44906"));
            Subcategory redChilli = subcategoryRepository.save(new Subcategory(spices, "Red Chilli", "https://images.unsplash.com/photo-1588879460618-9249e7d947d1"));

            Supplier sup1 = supplierRepository.save(new Supplier("sup_101", "KFPCL Farmer Co-op", true, "+919876543210", "+919876543210", "Guntur", "Andhra Pradesh"));
            Supplier sup2 = supplierRepository.save(new Supplier("sup_102", "Anusha Agro Traders", true, "+919988776655", "+919988776655", "Nizamabad", "Telangana"));

            Product p1 = new Product();
            p1.setName("Premium Royal Basmati Rice (1121)");
            p1.setBrand("KFPCL Gold");
            p1.setCategory(grains);
            p1.setSubcategory(basmati);
            p1.setSupplier(sup1);
            p1.setMainImageUrl("https://images.unsplash.com/photo-1586201375761-83865001e31c");
            p1.setGalleryImages(List.of("https://images.unsplash.com/photo-1586201375761-83865001e31c", "https://images.unsplash.com/photo-1536304929831-ee1ca9d44906"));
            p1.setMinOrderQuantity("1,000 KG");
            p1.setIndicativePrice("₹85 / KG");
            p1.setNumericPrice(new BigDecimal("85.00"));
            p1.setSpecifications("Moisture: max 12%, Grain length: 8.35mm, Broken: max 1%");
            p1.setIsActive(true);
            productRepository.save(p1);

            Product p2 = new Product();
            p2.setName("Guntur Teja Red Chilli (Stemless)");
            p2.setBrand("Spicex");
            p2.setCategory(spices);
            p2.setSubcategory(redChilli);
            p2.setSupplier(sup2);
            p2.setMainImageUrl("https://images.unsplash.com/photo-1588879460618-9249e7d947d1");
            p2.setGalleryImages(List.of("https://images.unsplash.com/photo-1588879460618-9249e7d947d1"));
            p2.setMinOrderQuantity("500 KG");
            p2.setIndicativePrice("₹210 / KG");
            p2.setNumericPrice(new BigDecimal("210.00"));
            p2.setSpecifications("Pungency: High (ASTA 70+), Moisture: max 10%");
            p2.setIsActive(true);
            productRepository.save(p2);

            bannerRepository.save(new Banner("Kharif B2B Bulk Commodity Sale", "https://images.unsplash.com/photo-1500937386664-56d1dfef3854", "/api/products?categoryId=1", 1, true));
            bannerRepository.save(new Banner("Direct Farmer-to-Wholesaler Hub", "https://images.unsplash.com/photo-1592417817098-8f3d6eb231fc", "/api/products", 2, true));
        }
    }
}
