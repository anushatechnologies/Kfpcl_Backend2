package com.project.Anusha.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdminPermissions {

    private static final List<String> ALL = List.of(
            "dashboard",
            "command_center",
            "operations_studio",
            "product_performance",
            "orders",
            "categories",
            "subcategories",
            "products",
            "payments",
            "cod",
            "stores",
            "store_dashboard",
            "delivery_dashboard",
            "delivery_personnel",
            "delivery_documents",
            "delivery_fare_settings",
            "delivery_live_map",
            "delivery_assignments",
            "payouts",
            "banners",
            "coupons",
            "marquee",
            "user_logs",
            "policies",
            "users",
            "settings",
            "notifications"
    );

    private static final Set<String> ALLOWED = new LinkedHashSet<>(ALL);

    private AdminPermissions() {
    }

    public static List<String> all() {
        return new ArrayList<>(ALL);
    }

    public static List<String> normalize(List<String> permissions) {
        if (permissions == null) {
            return List.of();
        }

        return permissions.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase())
                .filter(ALLOWED::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    public static List<String> parse(String permissionKeys) {
        if (permissionKeys == null || permissionKeys.isBlank()) {
            return List.of();
        }

        return normalize(Arrays.asList(permissionKeys.split(",")));
    }

    public static String serialize(List<String> permissions) {
        return String.join(",", normalize(permissions));
    }
}
