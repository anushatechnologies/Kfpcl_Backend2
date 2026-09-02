package com.project.Anusha.dto;

import java.util.List;

public class AdminPermissionsRequest {
    private List<String> permissions;

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
