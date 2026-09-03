package com.project.Anusha.dto;

import com.project.Anusha.model.Category;

public class CategoryDetailResponse {
    private Category category;
    private long activeSubcategoryCount;

    public CategoryDetailResponse(Category category, long activeSubcategoryCount) {
        this.category = category;
        this.activeSubcategoryCount = activeSubcategoryCount;
    }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public long getActiveSubcategoryCount() { return activeSubcategoryCount; }
    public void setActiveSubcategoryCount(long activeSubcategoryCount) { this.activeSubcategoryCount = activeSubcategoryCount; }
}
