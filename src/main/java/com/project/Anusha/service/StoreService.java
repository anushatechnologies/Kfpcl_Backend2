package com.project.Anusha.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.project.Anusha.dto.StoreRequest;
import com.project.Anusha.dto.StoreResponse;
import com.project.Anusha.exception.ResourceNotFoundException;
import com.project.Anusha.model.Store;
import com.project.Anusha.repository.StoreRepository;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final S3Service s3Service;

    private StoreResponse mapToResponse(Store store) {
        StoreResponse response = new StoreResponse();
        response.setId(store.getId());
        response.setName(store.getName());
        response.setLabel(store.getLabel());
        response.setDisplayOrder(store.getDisplayOrder());
        response.setActive(store.getActive());
        response.setImageUrl(store.getImageUrl());
        response.setAddress(store.getAddress());
        response.setCity(store.getCity());
        response.setPincode(store.getPincode());
        response.setPhoneNumber(store.getPhoneNumber());
        response.setPriceRange(store.getPriceRange());
        response.setTimings(store.getTimings());
        response.setAnnouncement(store.getAnnouncement());
        response.setDelivery(store.getDelivery());
        response.setPackageCost(store.getPackageCost());
        response.setRating(store.getRating());
        response.setPreferredOrder(store.getPreferredOrder());
        response.setLatitude(store.getLatitude());
        response.setLongitude(store.getLongitude());
        response.setCreatedAt(store.getCreatedAt());
        response.setUpdatedAt(store.getUpdatedAt());
        return response;
    }

    private void updateStoreFromRequest(Store store, StoreRequest request) {
        store.setName(request.getName());
        store.setLabel(request.getLabel());
        store.setDisplayOrder(request.getDisplayOrder());
        store.setActive(request.getActive());
        store.setAddress(request.getAddress());
        store.setCity(request.getCity());
        store.setPincode(request.getPincode());
        store.setPhoneNumber(request.getPhoneNumber());
        store.setPriceRange(request.getPriceRange());
        store.setTimings(request.getTimings());
        store.setAnnouncement(request.getAnnouncement());
        store.setDelivery(request.getDelivery());
        store.setPackageCost(request.getPackageCost());
        store.setRating(request.getRating());
        store.setPreferredOrder(request.getPreferredOrder());
        store.setLatitude(request.getLatitude());
        store.setLongitude(request.getLongitude());
    }

    @Transactional
    public StoreResponse createStore(StoreRequest request, MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        Store store = new Store();
        updateStoreFromRequest(store, request);

        String imageUrl = s3Service.uploadFile(imageFile, "stores");
        store.setImageUrl(imageUrl);

        Store saved = storeRepository.save(store);
        return mapToResponse(saved);
    }

    @Transactional
    public StoreResponse updateStore(Long id, StoreRequest request, MultipartFile imageFile) throws IOException {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));

        updateStoreFromRequest(store, request);

        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = s3Service.uploadFile(imageFile, "stores");
            store.setImageUrl(imageUrl);
        }

        Store updated = storeRepository.save(store);
        return mapToResponse(updated);
    }

    public Page<StoreResponse> getAllStores(String name, Pageable pageable) {
        Page<Store> page = storeRepository.searchByName(name, pageable);
        return page.map(this::mapToResponse);
    }

    public StoreResponse getStoreById(Long id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));
        return mapToResponse(store);
    }

    public List<StoreResponse> suggestStores(String query) {
        List<Store> stores = storeRepository.findByNameContainingIgnoreCase(query);
        return stores.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public void deleteStore(Long id) throws Exception {
        if (!storeRepository.existsById(id)) {
            throw new Exception("Store not found with id: " + id);
        }
        storeRepository.deleteById(id);
    }
}