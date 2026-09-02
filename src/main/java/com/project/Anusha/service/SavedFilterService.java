package com.project.Anusha.service;

import com.project.Anusha.model.SavedFilter;
import com.project.Anusha.repository.SavedFilterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class SavedFilterService {

    private final SavedFilterRepository savedFilterRepository;

    @Transactional(readOnly = true)
    public List<SavedFilter> list(String resource) {
        if (resource == null || resource.isBlank()) {
            return savedFilterRepository.findAll();
        }
        return savedFilterRepository.findByResourceOrderByCreatedAtDesc(normalize(resource));
    }

    public SavedFilter create(Map<String, Object> payload, String createdBy) {
        String name = payload.get("name") != null ? payload.get("name").toString() : null;
        String resource = payload.get("resource") != null ? payload.get("resource").toString() : null;
        String query = payload.get("query") != null ? payload.get("query").toString() : null;

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource is required");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        SavedFilter filter = new SavedFilter();
        filter.setName(name.trim());
        filter.setResource(normalize(resource));
        filter.setQuery(query.trim());
        filter.setCreatedBy(createdBy);
        return savedFilterRepository.save(filter);
    }

    public SavedFilter update(Long id, Map<String, Object> payload) {
        SavedFilter filter = savedFilterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Saved filter not found"));

        if (payload.containsKey("name") && payload.get("name") != null) {
            String name = payload.get("name").toString();
            if (!name.isBlank()) {
                filter.setName(name.trim());
            }
        }
        if (payload.containsKey("resource") && payload.get("resource") != null) {
            String resource = payload.get("resource").toString();
            if (!resource.isBlank()) {
                filter.setResource(normalize(resource));
            }
        }
        if (payload.containsKey("query") && payload.get("query") != null) {
            String query = payload.get("query").toString();
            if (!query.isBlank()) {
                filter.setQuery(query.trim());
            }
        }

        return savedFilterRepository.save(filter);
    }

    public void delete(Long id) {
        if (!savedFilterRepository.existsById(id)) {
            throw new IllegalArgumentException("Saved filter not found");
        }
        savedFilterRepository.deleteById(id);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

