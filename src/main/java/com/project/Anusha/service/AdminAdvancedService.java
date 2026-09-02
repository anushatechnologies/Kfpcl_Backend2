package com.project.Anusha.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Anusha.exception.ResourceNotFoundException;
import com.project.Anusha.model.*;
import com.project.Anusha.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminAdvancedService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserLogRepository userLogRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final CampaignDraftRepository campaignDraftRepository;
    private final ObjectMapper objectMapper;
    private final UserLogService userLogService;

    public Product updateProductLifecycle(Long productId, Map<String, Object> payload, String actorEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (payload.containsKey("isDraft")) {
            product.setIsDraft(asBoolean(payload.get("isDraft"), product.getIsDraft()));
        }
        if (payload.containsKey("publishAt")) {
            product.setPublishAt(asDateTime(payload.get("publishAt")));
        }
        if (payload.containsKey("unpublishAt")) {
            product.setUnpublishAt(asDateTime(payload.get("unpublishAt")));
        }
        if (payload.containsKey("flashSaleEnabled")) {
            product.setFlashSaleEnabled(asBoolean(payload.get("flashSaleEnabled"), product.getFlashSaleEnabled()));
        }
        if (payload.containsKey("flashSaleStartAt")) {
            product.setFlashSaleStartAt(asDateTime(payload.get("flashSaleStartAt")));
        }
        if (payload.containsKey("flashSaleEndAt")) {
            product.setFlashSaleEndAt(asDateTime(payload.get("flashSaleEndAt")));
        }
        if (payload.containsKey("flashSalePrice")) {
            product.setFlashSalePrice(asDouble(payload.get("flashSalePrice")));
        }
        if (payload.containsKey("isActive")) {
            product.setIsActive(asBoolean(payload.get("isActive"), product.getIsActive()));
        }

        Product saved = productRepository.save(product);
        userLogService.log(null, "ADMIN", "PRODUCT_LIFECYCLE_UPDATE", "productId=" + productId + ", actor=" + actorEmail, null);
        return saved;
    }

    public ApprovalRequest createApprovalRequest(Map<String, Object> payload, String actorEmail) {
        ApprovalRequest request = new ApprovalRequest();
        request.setApprovalType(stringValue(payload.get("approvalType"), "GENERAL"));
        request.setTargetType(stringValue(payload.get("targetType"), "UNKNOWN"));
        request.setTargetId(payload.get("targetId") instanceof Number number ? number.longValue() : null);
        request.setReason(stringValue(payload.get("reason"), ""));
        request.setRequestedByEmail(actorEmail);
        request.setPayloadJson(writeJson(payload.get("payload")));
        ApprovalRequest saved = approvalRequestRepository.save(request);
        userLogService.log(null, "ADMIN", "APPROVAL_REQUEST_CREATED", "approvalRequestId=" + saved.getId(), null);
        return saved;
    }

    public List<ApprovalRequest> listApprovalRequests() {
        return approvalRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public ApprovalRequest decideApproval(Long id, Map<String, Object> payload, String actorEmail) {
        ApprovalRequest request = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found"));
        request.setStatus(stringValue(payload.get("status"), "APPROVED").toUpperCase(Locale.ROOT));
        request.setDecisionNotes(stringValue(payload.get("decisionNotes"), ""));
        request.setApprovedByEmail(actorEmail);
        request.setDecidedAt(LocalDateTime.now());
        ApprovalRequest saved = approvalRequestRepository.save(request);
        userLogService.log(null, "ADMIN", "APPROVAL_REQUEST_DECIDED", "approvalRequestId=" + id + ", status=" + saved.getStatus(), null);
        return saved;
    }

    public DeliveryZone saveZone(Map<String, Object> payload) {
        DeliveryZone zone = new DeliveryZone();
        zone.setName(stringValue(payload.get("name"), "Unnamed Zone"));
        zone.setColor(stringValue(payload.get("color"), "#2563eb"));
        zone.setVehicleGroup(stringValue(payload.get("vehicleGroup"), "ALL"));
        zone.setGeoJson(stringValue(payload.get("geoJson"), ""));
        zone.setNotes(stringValue(payload.get("notes"), ""));
        zone.setActive(asBoolean(payload.get("active"), true));
        DeliveryZone saved = deliveryZoneRepository.save(zone);
        userLogService.log(null, "ADMIN", "DELIVERY_ZONE_CREATED", "zoneId=" + saved.getId(), null);
        return saved;
    }

    public DeliveryZone updateZone(Long id, Map<String, Object> payload) {
        DeliveryZone zone = deliveryZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery zone not found"));
        if (payload.containsKey("name")) zone.setName(stringValue(payload.get("name"), zone.getName()));
        if (payload.containsKey("color")) zone.setColor(stringValue(payload.get("color"), zone.getColor()));
        if (payload.containsKey("vehicleGroup")) zone.setVehicleGroup(stringValue(payload.get("vehicleGroup"), zone.getVehicleGroup()));
        if (payload.containsKey("geoJson")) zone.setGeoJson(stringValue(payload.get("geoJson"), zone.getGeoJson()));
        if (payload.containsKey("notes")) zone.setNotes(stringValue(payload.get("notes"), zone.getNotes()));
        if (payload.containsKey("active")) zone.setActive(asBoolean(payload.get("active"), zone.getActive()));
        DeliveryZone saved = deliveryZoneRepository.save(zone);
        userLogService.log(null, "ADMIN", "DELIVERY_ZONE_UPDATED", "zoneId=" + saved.getId(), null);
        return saved;
    }

    public List<DeliveryZone> listZones() {
        return deliveryZoneRepository.findAllByOrderByUpdatedAtDesc();
    }

    public CampaignDraft saveCampaignDraft(Map<String, Object> payload) {
        CampaignDraft draft = new CampaignDraft();
        draft.setName(stringValue(payload.get("name"), "Untitled Campaign"));
        draft.setChannel(stringValue(payload.get("channel"), "WHATSAPP"));
        draft.setSegmentKey(stringValue(payload.get("segmentKey"), "all_customers"));
        draft.setMessage(stringValue(payload.get("message"), ""));
        draft.setNotes(stringValue(payload.get("notes"), ""));
        draft.setStatus(stringValue(payload.get("status"), "DRAFT"));
        draft.setScheduledAt(asDateTime(payload.get("scheduledAt")));
        CampaignDraft saved = campaignDraftRepository.save(draft);
        userLogService.log(null, "ADMIN", "CAMPAIGN_DRAFT_CREATED", "campaignId=" + saved.getId(), null);
        return saved;
    }

    public List<CampaignDraft> listCampaignDrafts() {
        return campaignDraftRepository.findAllByOrderByUpdatedAtDesc();
    }

    public Map<String, Object> getCampaignTargeting() {
        List<Customer> customers = customerRepository.findAll();
        List<Order> orders = orderRepository.findAll();
        Map<Long, List<Order>> ordersByCustomer = orders.stream()
                .filter(order -> order.getCustomer() != null && order.getCustomer().getId() != null)
                .collect(Collectors.groupingBy(order -> order.getCustomer().getId()));

        List<Map<String, Object>> segments = new ArrayList<>();
        segments.add(buildSegment("new_customers", "New customers", customers.stream()
                .filter(customer -> customer.getCreatedAt() != null && customer.getCreatedAt().isAfter(LocalDateTime.now().minusDays(14)))
                .toList()));
        segments.add(buildSegment("repeat_buyers", "Repeat buyers", customers.stream()
                .filter(customer -> ordersByCustomer.getOrDefault(customer.getId(), List.of()).size() >= 3)
                .toList()));
        segments.add(buildSegment("dormant_customers", "Dormant customers", customers.stream()
                .filter(customer -> {
                    LocalDateTime lastOrder = ordersByCustomer.getOrDefault(customer.getId(), List.of()).stream()
                            .map(Order::getPlacedAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
                    return lastOrder == null || lastOrder.isBefore(LocalDateTime.now().minusDays(30));
                }).toList()));
        segments.add(buildSegment("high_cancellation_risk", "High cancellation risk", customers.stream()
                .filter(customer -> ordersByCustomer.getOrDefault(customer.getId(), List.of()).stream()
                        .filter(order -> "cancelled".equalsIgnoreCase(order.getOrderStatus()) || "rejected".equalsIgnoreCase(order.getOrderStatus()))
                        .count() >= 2)
                .toList()));

        return Map.of("segments", segments);
    }

    public Map<String, Object> getFraudScores() {
        List<Order> orders = orderRepository.findAllWithCustomerAndAddress();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Long> addressReuse = orders.stream()
                .map(this::addressKey)
                .filter(key -> !key.isBlank())
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()));

        for (Order order : orders) {
            int score = 0;
            List<String> flags = new ArrayList<>();
            if ("failed".equalsIgnoreCase(order.getPaymentStatus())) {
                score += 20;
                flags.add("failed_payment");
            }
            if ("cancelled".equalsIgnoreCase(order.getOrderStatus())) {
                score += 20;
                flags.add("cancelled_order");
            }
            long addressCount = addressReuse.getOrDefault(addressKey(order), 0L);
            if (addressCount >= 3) {
                score += 30;
                flags.add("shared_address");
            }
            long customerOrderCount = orders.stream()
                    .filter(o -> o.getCustomer() != null && order.getCustomer() != null && Objects.equals(o.getCustomer().getId(), order.getCustomer().getId()))
                    .count();
            if (customerOrderCount >= 5 && "pending".equalsIgnoreCase(order.getOrderStatus())) {
                score += 10;
                flags.add("high_velocity_customer");
            }
            if (score <= 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", order.getId());
            row.put("orderNumber", order.getOrderNumber());
            row.put("customerName", order.getCustomer() != null ? order.getCustomer().getName() : "");
            row.put("score", score);
            row.put("riskLevel", score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW");
            row.put("flags", flags);
            row.put("placedAt", order.getPlacedAt());
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> (Integer) row.get("score")).reversed());
        return Map.of("rows", rows.stream().limit(25).toList());
    }

    public Map<String, Object> getSmartInsights(String period) {
        List<Product> products = productRepository.findAll();
        List<Order> orders = orderRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = resolvePeriodStart(period, now);
        LocalDateTime previousStart = currentStart.minus(ChronoUnit.DAYS.between(currentStart, now) + 1, ChronoUnit.DAYS);

        List<Order> currentOrders = orders.stream().filter(order -> order.getPlacedAt() != null && !order.getPlacedAt().isBefore(currentStart)).toList();
        List<Order> previousOrders = orders.stream().filter(order -> order.getPlacedAt() != null && !order.getPlacedAt().isBefore(previousStart) && order.getPlacedAt().isBefore(currentStart)).toList();

        BigDecimal currentRevenue = currentOrders.stream().map(Order::getGrandTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousRevenue = previousOrders.stream().map(Order::getGrandTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> salesDropReasons = new ArrayList<>();
        if (previousRevenue.compareTo(BigDecimal.ZERO) > 0 && currentRevenue.compareTo(previousRevenue) < 0) {
            salesDropReasons.add("Revenue is down compared with the previous period.");
        }
        long lowStock = products.stream().filter(product -> product.getVariants() != null && product.getVariants().stream()
                .filter(variant -> Boolean.TRUE.equals(variant.getIsActive()))
                .map(Variant::getStock).filter(Objects::nonNull).mapToInt(Integer::intValue).sum() <= 5).count();
        if (lowStock > 0) {
            salesDropReasons.add(lowStock + " products are low on stock and may be reducing conversion.");
        }
        long hiddenProducts = products.stream().filter(product -> !Boolean.TRUE.equals(product.getIsActive()) || Boolean.TRUE.equals(product.getIsDraft())).count();
        if (hiddenProducts > 0) {
            salesDropReasons.add(hiddenProducts + " products are hidden or still drafts.");
        }

        List<Map<String, Object>> reorderSuggestions = products.stream()
                .map(product -> {
                    int stock = product.getVariants() == null ? 0 : product.getVariants().stream()
                            .filter(variant -> Boolean.TRUE.equals(variant.getIsActive()))
                            .map(Variant::getStock).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
                    long soldUnits = orders.stream()
                            .flatMap(order -> order.getItems() == null ? java.util.stream.Stream.empty() : order.getItems().stream())
                            .filter(item -> item.getVariant() != null && item.getVariant().getProduct() != null
                                    && Objects.equals(item.getVariant().getProduct().getId(), product.getId()))
                            .map(OrderItem::getQuantity).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", product.getId());
                    row.put("productName", product.getName());
                    row.put("currentStock", stock);
                    row.put("unitsSold", soldUnits);
                    row.put("recommendation", soldUnits > stock ? "Reorder now" : stock <= 5 ? "Restock soon" : "Stock healthy");
                    return row;
                })
                .sorted(Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("recommendation"))))
                .limit(20)
                .toList();

        return Map.of(
                "salesDropInsights", salesDropReasons,
                "reorderSuggestions", reorderSuggestions
        );
    }

    public Map<String, Object> generateSmartProductContent(Map<String, Object> payload) {
        String name = stringValue(payload.get("name"), "Product");
        String category = stringValue(payload.get("category"), "General");
        String subCategory = stringValue(payload.get("subCategory"), "");
        String keywords = stringValue(payload.get("keywords"), "");

        String description = name + " is a " + category.toLowerCase(Locale.ROOT)
                + (subCategory.isBlank() ? "" : " " + subCategory.toLowerCase(Locale.ROOT))
                + " item designed for everyday convenience. "
                + (keywords.isBlank() ? "Best suited for quick commerce and repeat purchases."
                : "Highlights: " + keywords + ".");

        List<String> tags = new ArrayList<>();
        tags.add(category.toLowerCase(Locale.ROOT).replace(" ", "_"));
        if (!subCategory.isBlank()) {
            tags.add(subCategory.toLowerCase(Locale.ROOT).replace(" ", "_"));
        }
        tags.add(name.toLowerCase(Locale.ROOT).replace(" ", "_"));
        if (!keywords.isBlank()) {
            tags.addAll(Arrays.stream(keywords.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toLowerCase(Locale.ROOT).replace(" ", "_"))
                    .limit(5)
                    .toList());
        }

        return Map.of(
                "description", description,
                "tags", tags.stream().distinct().toList(),
                "imageChecklist", List.of(
                        "Use a bright front-facing image",
                        "Keep background clean and uncluttered",
                        "Ensure product label is readable"
                )
        );
    }

    public ExportPayload exportData(String type, String format) {
        String normalizedType = type == null ? "products" : type.toLowerCase(Locale.ROOT);
        String normalizedFormat = format == null ? "csv" : format.toLowerCase(Locale.ROOT);
        List<String[]> rows = switch (normalizedType) {
            case "orders" -> buildOrderRows();
            case "approvals" -> buildApprovalRows();
            case "audit" -> buildAuditRows();
            default -> buildProductRows();
        };

        String title = normalizedType.toUpperCase(Locale.ROOT) + " EXPORT";
        if ("pdf".equals(normalizedFormat)) {
            return new ExportPayload(title + ".pdf", "application/pdf", buildSimplePdf(title, rows));
        }
        return new ExportPayload(title + ".csv", "text/csv", buildCsv(rows).getBytes(StandardCharsets.UTF_8));
    }

    private List<String[]> buildProductRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Name", "Store", "Active", "Draft", "Publish At", "Flash Sale", "Flash Sale Price"});
        for (Product product : productRepository.findAll()) {
            rows.add(new String[]{
                    String.valueOf(product.getId()),
                    stringValue(product.getName(), ""),
                    product.getStore() != null ? stringValue(product.getStore().getName(), "") : "",
                    String.valueOf(product.getIsActive()),
                    String.valueOf(product.getIsDraft()),
                    String.valueOf(product.getPublishAt()),
                    String.valueOf(product.getFlashSaleEnabled()),
                    String.valueOf(product.getFlashSalePrice())
            });
        }
        return rows;
    }

    private List<String[]> buildOrderRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Order Number", "Customer", "Status", "Payment Status", "Total", "Placed At"});
        for (Order order : orderRepository.findAll()) {
            rows.add(new String[]{
                    String.valueOf(order.getId()),
                    stringValue(order.getOrderNumber(), ""),
                    order.getCustomer() != null ? stringValue(order.getCustomer().getName(), "") : "",
                    stringValue(order.getOrderStatus(), ""),
                    stringValue(order.getPaymentStatus(), ""),
                    String.valueOf(order.getGrandTotal()),
                    String.valueOf(order.getPlacedAt())
            });
        }
        return rows;
    }

    private List<String[]> buildApprovalRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Type", "Target Type", "Target ID", "Status", "Requested By", "Approved By", "Created At"});
        for (ApprovalRequest request : approvalRequestRepository.findAllByOrderByCreatedAtDesc()) {
            rows.add(new String[]{
                    String.valueOf(request.getId()),
                    stringValue(request.getApprovalType(), ""),
                    stringValue(request.getTargetType(), ""),
                    String.valueOf(request.getTargetId()),
                    stringValue(request.getStatus(), ""),
                    stringValue(request.getRequestedByEmail(), ""),
                    stringValue(request.getApprovedByEmail(), ""),
                    String.valueOf(request.getCreatedAt())
            });
        }
        return rows;
    }

    private List<String[]> buildAuditRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Role", "Action", "Details", "IP", "Timestamp"});
        for (UserLog log : userLogRepository.findAll()) {
            rows.add(new String[]{
                    String.valueOf(log.getId()),
                    stringValue(log.getUserRole(), ""),
                    stringValue(log.getAction(), ""),
                    stringValue(log.getDetails(), ""),
                    stringValue(log.getIpAddress(), ""),
                    String.valueOf(log.getTimestamp())
            });
        }
        return rows;
    }

    private Map<String, Object> buildSegment(String key, String label, List<Customer> customers) {
        return Map.of(
                "key", key,
                "label", label,
                "count", customers.size(),
                "sampleCustomers", customers.stream().limit(5).map(customer -> Map.of(
                        "id", customer.getId(),
                        "name", customer.getName(),
                        "phone", customer.getPhoneNumber()
                )).toList()
        );
    }

    private String buildCsv(List<String[]> rows) {
        return rows.stream()
                .map(row -> Arrays.stream(row)
                        .map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
    }

    private byte[] buildSimplePdf(String title, List<String[]> rows) {
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.add("");
        rows.stream().limit(25).forEach(row -> lines.add(String.join(" | ", row)));

        StringBuilder content = new StringBuilder();
        content.append("BT\n/F1 10 Tf\n50 780 Td\n");
        boolean first = true;
        for (String line : lines) {
            if (!first) {
                content.append("T*\n");
            }
            content.append("(").append(escapePdf(line)).append(") Tj\n");
            first = false;
        }
        content.append("ET");

        String stream = content.toString();
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>");
        objects.add("<< /Length " + stream.getBytes(StandardCharsets.UTF_8).length + " >>\nstream\n" + stream + "\nendstream");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.UTF_8).length);
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.UTF_8).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String addressKey(Order order) {
        if (order.getAddress() == null) {
            return "";
        }
        return String.join("|",
                stringValue(order.getAddress().getAddressLine1(), "").toLowerCase(Locale.ROOT),
                stringValue(order.getAddress().getPostalCode(), ""),
                stringValue(order.getAddress().getContactPhone(), "")
        );
    }

    private String writeJson(Object payload) {
        try {
            return payload == null ? null : objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String result = String.valueOf(value);
        return result.isBlank() ? fallback : result;
    }

    private boolean asBoolean(Object value, Boolean fallback) {
        if (value == null) {
            return Boolean.TRUE.equals(fallback);
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Double asDouble(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Double.valueOf(String.valueOf(value));
    }

    private LocalDateTime asDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    private LocalDateTime resolvePeriodStart(String period, LocalDateTime now) {
        String normalized = period == null ? "month" : period.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "today" -> now.minusDays(1);
            case "week" -> now.minusWeeks(1);
            case "year" -> now.minusYears(1);
            default -> now.minusMonths(1);
        };
    }

    public record ExportPayload(String fileName, String contentType, byte[] data) {
    }
}
