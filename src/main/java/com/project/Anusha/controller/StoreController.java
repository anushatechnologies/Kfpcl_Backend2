package com.project.Anusha.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.project.Anusha.dto.StoreRequest;
import com.project.Anusha.dto.StoreResponse;
import com.project.Anusha.service.StoreService;

import java.io.IOException;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<Page<StoreResponse>> getAllStores(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 10, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(storeService.getAllStores(name, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoreResponse> getStoreById(@PathVariable Long id) {
        return ResponseEntity.ok(storeService.getStoreById(id));
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<StoreResponse>> suggestStores(@RequestParam String q) {
        return ResponseEntity.ok(storeService.suggestStores(q));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> createStore(
            @Valid @RequestPart("store") StoreRequest request,
            @RequestPart("image") MultipartFile imageFile) throws IOException {
        StoreResponse created = storeService.createStore(request, imageFile);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> updateStore(
            @PathVariable Long id,
            @Valid @RequestPart("store") StoreRequest request,
            @RequestPart(value = "image", required = false) MultipartFile imageFile) throws IOException {
        StoreResponse updated = storeService.updateStore(id, request, imageFile);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStore(@PathVariable Long id) throws Exception {
        storeService.deleteStore(id);
        return ResponseEntity.noContent().build();
    }
}