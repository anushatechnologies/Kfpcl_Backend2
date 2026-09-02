package com.project.Anusha.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Anusha.model.Customer;
import com.project.Anusha.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class WhatsAppCampaignService {

    private static final Pattern VARIABLE_HEADER_PATTERN = Pattern.compile("^var(\\d+)$");
    private static final int MAX_STORED_RESULTS = 500;

    private final WhatsAppService whatsAppService;
    private final ObjectMapper objectMapper;
    private final CustomerRepository customerRepository;
    private final ReferralService referralService;
    private final ConcurrentMap<String, CampaignExecution> campaigns = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MessageIndex> messageIndex = new ConcurrentHashMap<>();
    private final ExecutorService campaignExecutor = Executors.newFixedThreadPool(2);

    @Value("${app.referral.fallback-link:https://play.google.com/store/apps/details?id=com.anusha.deliveryapp}")
    private String referralFallbackLink;

    /** Default country code (no +) used when a phone number is supplied without one. */
    @Value("${app.whatsapp.default-country-code:91}")
    private String defaultCountryCode;

    public WhatsAppCampaignService(WhatsAppService whatsAppService,
                                   ObjectMapper objectMapper,
                                   CustomerRepository customerRepository,
                                   ReferralService referralService) {
        this.whatsAppService = whatsAppService;
        this.objectMapper = objectMapper;
        this.customerRepository = customerRepository;
        this.referralService = referralService;
    }

    @PreDestroy
    public void shutdownExecutor() {
        campaignExecutor.shutdownNow();
    }

    public CampaignStartResponse startCampaign(
            MultipartFile file,
            String templateName,
            String headerImageUrl,
            boolean activeOnly
    ) throws IOException {
        return startCampaign(file, templateName, headerImageUrl, activeOnly, false);
    }

    /**
     * Refer & earn blast — Meta template `refer_and_earn_invite`.
     *
     * The CSV only needs Phone Number + Name (Active is optional). The {{1}} body
     * variable (the personalized referral link) is auto-resolved per row by:
     *   1. Looking up the customer by phone in the DB
     *   2. Ensuring they have a referral code (creates one if not)
     *   3. Building https://app.anushatechnologies.com/r/{CODE} as {{1}}
     *
     * If a phone number isn't a registered customer, {{1}} falls back to the plain
     * Play Store link so the recipient still gets a usable message.
     */
    /**
     * Generic referral-link blast.
     *
     * Works for ANY approved Meta template whose body has exactly one {{1}}
     * variable = the per-recipient referral link. Header may be an image,
     * a video, or absent.
     *
     * Used by the Lucky template (image header) and any future referral
     * campaign that follows the same {{1}} = link pattern.
     *
     * @param templateName     Meta-approved template (e.g. "lucky")
     * @param headerType       "image" | "video" | "none"
     * @param headerMediaUrl   public URL for the header media (skip if file given)
     * @param headerMediaFile  inline file upload (skip if URL given)
     * @param activeOnly       whether to honour the CSV's "Active" column
     */
    public CampaignStartResponse startReferralTemplateCampaign(
            MultipartFile file,
            String templateName,
            String headerType,
            String headerMediaUrl,
            MultipartFile headerMediaFile,
            boolean activeOnly
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a .xlsx or .csv file with Phone Number + Name.");
        }
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("Template name is required.");
        }
        String resolvedHeaderType = headerType == null || headerType.isBlank()
                ? "none" : headerType.trim().toLowerCase(Locale.ROOT);
        if (!resolvedHeaderType.equals("image")
                && !resolvedHeaderType.equals("video")
                && !resolvedHeaderType.equals("none")) {
            throw new IllegalArgumentException("headerType must be image, video, or none.");
        }

        // Upload the header media if any.
        String headerMediaId = null;
        if (!resolvedHeaderType.equals("none")) {
            if (headerMediaFile != null && !headerMediaFile.isEmpty()) {
                String contentType = headerMediaFile.getContentType() == null ? ""
                        : headerMediaFile.getContentType().toLowerCase(Locale.ROOT);
                if (resolvedHeaderType.equals("image") && !contentType.startsWith("image/")) {
                    throw new IllegalArgumentException("Header file must be an image (matches headerType=image).");
                }
                if (resolvedHeaderType.equals("video") && !contentType.startsWith("video/")) {
                    throw new IllegalArgumentException("Header file must be a video (matches headerType=video).");
                }
                headerMediaId = whatsAppService.uploadMediaToMeta(headerMediaFile);
            } else if (headerMediaUrl != null && !headerMediaUrl.isBlank()) {
                headerMediaId = whatsAppService.uploadMediaUrlToMeta(headerMediaUrl);
            }
        }

        // Parse CSV / XLSX → recipients
        List<CampaignRecipient> rawRecipients = parseRecipients(file);
        if (rawRecipients.isEmpty()) {
            throw new IllegalArgumentException("No valid rows found in uploaded file.");
        }

        // Per-recipient: look up referral code by phone and inject as Var1.
        List<CampaignRecipient> personalized = new ArrayList<>(rawRecipients.size());
        for (CampaignRecipient row : rawRecipients) {
            String link = resolveReferralLinkForPhone(row.phoneNumber());
            personalized.add(new CampaignRecipient(
                    row.rowNumber(),
                    row.name(),
                    row.phoneNumber(),
                    row.active(),
                    List.of(link)
            ));
        }

        String campaignId = UUID.randomUUID().toString();
        CampaignExecution execution = new CampaignExecution(
                campaignId,
                templateName.trim(),
                sanitizeText(file.getOriginalFilename()),
                personalized.size(),
                activeOnly,
                sanitizeText(headerMediaUrl),
                sanitizeText(headerMediaId),
                resolvedHeaderType.equals("none") ? null : resolvedHeaderType,
                false,
                LocalDateTime.now()
        );
        campaigns.put(campaignId, execution);
        campaignExecutor.submit(() -> runCampaign(execution, personalized));

        return new CampaignStartResponse(
                campaignId,
                execution.getStatus(),
                personalized.size(),
                "Referral campaign queued — " + personalized.size() + " recipients (template: " + templateName.trim() + ")."
        );
    }

    public CampaignStartResponse startReferAndEarnCampaign(
            MultipartFile file,
            String headerMediaUrl,
            MultipartFile headerMediaFile,
            boolean activeOnly
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a .xlsx or .csv file with Phone Number + Name.");
        }

        // Upload the video header to Meta (URL or file).
        String headerMediaId = null;
        String resolvedHeaderUrl = headerMediaUrl;
        if (headerMediaFile != null && !headerMediaFile.isEmpty()) {
            String contentType = headerMediaFile.getContentType() == null ? ""
                    : headerMediaFile.getContentType().toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("video/")) {
                throw new IllegalArgumentException("Header must be a video file.");
            }
            headerMediaId = whatsAppService.uploadMediaToMeta(headerMediaFile);
        } else if (headerMediaUrl != null && !headerMediaUrl.isBlank()) {
            headerMediaId = whatsAppService.uploadMediaUrlToMeta(headerMediaUrl);
        }

        // Parse CSV / XLSX
        List<CampaignRecipient> rawRecipients = parseRecipients(file);
        if (rawRecipients.isEmpty()) {
            throw new IllegalArgumentException("No valid rows found in uploaded file.");
        }

        // For each row, look up referral code by phone and inject as Var1.
        List<CampaignRecipient> personalized = new ArrayList<>(rawRecipients.size());
        for (CampaignRecipient row : rawRecipients) {
            String link = resolveReferralLinkForPhone(row.phoneNumber());
            personalized.add(new CampaignRecipient(
                    row.rowNumber(),
                    row.name(),
                    row.phoneNumber(),
                    row.active(),
                    List.of(link)
            ));
        }

        String campaignId = UUID.randomUUID().toString();
        CampaignExecution execution = new CampaignExecution(
                campaignId,
                "refer_and_earn_invite",
                sanitizeText(file.getOriginalFilename()),
                personalized.size(),
                activeOnly,
                sanitizeText(resolvedHeaderUrl),
                sanitizeText(headerMediaId),
                "video",
                false,
                LocalDateTime.now()
        );
        campaigns.put(campaignId, execution);
        campaignExecutor.submit(() -> runCampaign(execution, personalized));

        return new CampaignStartResponse(
                campaignId,
                execution.getStatus(),
                personalized.size(),
                "Refer & earn campaign queued — " + personalized.size() + " recipients."
        );
    }

    public CampaignStartResponse startGlobalHiringCampaign(
            MultipartFile file,
            String headerMediaUrl,
            MultipartFile headerMediaFile,
            boolean activeOnly
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a .xlsx or .csv file with Phone Number.");
        }

        String templateName = "anusha_nexus_global_hiring";
        String headerMediaId = null;
        String headerMediaType = "video";

        if (headerMediaFile != null && !headerMediaFile.isEmpty()) {
            String contentType = headerMediaFile.getContentType() == null ? ""
                    : headerMediaFile.getContentType().toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("video/")) {
                throw new IllegalArgumentException("Header must be a video file.");
            }
            headerMediaId = whatsAppService.uploadMediaToMeta(headerMediaFile);
        } else if (headerMediaUrl != null && !headerMediaUrl.isBlank()) {
            headerMediaId = whatsAppService.uploadMediaUrlToMeta(headerMediaUrl);
        }

        List<CampaignRecipient> recipients = parseRecipients(file);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("No valid rows found in uploaded file.");
        }

        String campaignId = UUID.randomUUID().toString();
        CampaignExecution execution = new CampaignExecution(
                campaignId,
                templateName,
                sanitizeText(file.getOriginalFilename()),
                recipients.size(),
                activeOnly,
                sanitizeText(headerMediaUrl),
                sanitizeText(headerMediaId),
                headerMediaType,
                false,
                LocalDateTime.now()
        );
        campaigns.put(campaignId, execution);
        campaignExecutor.submit(() -> runCampaign(execution, recipients));

        return new CampaignStartResponse(
                campaignId,
                execution.getStatus(),
                recipients.size(),
                "Global hiring campaign queued — " + recipients.size() + " recipients."
        );
    }

    /**
     * Diagnostic single-send. Sends the refer_and_earn template to ONE phone
     * and returns the FULL Meta response (success body or raw error JSON) —
     * no error simplification. Use this to debug 131049 etc. without burning
     * your 24-hour marketing cap on more numbers.
     */
    public Map<String, Object> sendSingleTest(String phone, String templateName, String headerMediaUrl) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (phone == null || phone.isBlank()) {
            result.put("success", false);
            result.put("error", "Phone is required");
            return result;
        }
        String normalizedPhone = normalizePhoneCell(phone);
        String resolvedLink = resolveReferralLinkForPhone(normalizedPhone);
        String tmpl = (templateName == null || templateName.isBlank()) ? "refer_and_earn_invite" : templateName;
        List<String> bodyParameters = diagnosticBodyParameters(tmpl, resolvedLink);
        String urlButtonParameter = diagnosticUrlButtonParameter(tmpl, resolvedLink);

        result.put("phoneSent", normalizedPhone);
        result.put("templateUsed", tmpl);
        result.put("var1Resolved", resolvedLink);
        result.put("bodyParametersSent", bodyParameters.size());
        result.put("urlButtonParameterSent", urlButtonParameter == null ? null : urlButtonParameter);
        try {
            String headerMediaId = null;
            String headerType = "video"; // default
            if (headerMediaUrl != null && !headerMediaUrl.isBlank()) {
                headerMediaId = whatsAppService.uploadMediaUrlToMeta(headerMediaUrl);
                result.put("headerMediaId", headerMediaId);
                
                // Smart detect type from URL for the test send
                String lower = headerMediaUrl.toLowerCase(Locale.ROOT);
                if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")) {
                    headerType = "image";
                }
            }
            
            // If the template is "lucky", it's definitely an image
            if ("lucky".equalsIgnoreCase(tmpl)) {
                headerType = "image";
            }

            String response = urlButtonParameter != null
                    ? whatsAppService.sendTemplateMessageWithHeaderMediaIdAndUrlButton(
                            normalizedPhone, tmpl, bodyParameters, headerMediaId, headerType, urlButtonParameter)
                    : headerMediaId != null
                            ? whatsAppService.sendTemplateMessageWithHeaderMediaId(
                                    normalizedPhone, tmpl, bodyParameters, headerMediaId, headerType)
                            : whatsAppService.sendTemplateMessage(
                                    normalizedPhone, tmpl, bodyParameters, null);
            result.put("success", true);
            try {
                result.put("metaResponse", objectMapper.readTree(response));
            } catch (Exception ignored) {
                result.put("metaResponseRaw", response);
            }
        } catch (Exception ex) {
            result.put("success", false);
            String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            result.put("error", message);
            // Try to parse the JSON body Meta returned (it has error code + subcode + diagnostic).
            int jsonStart = message.indexOf('{');
            if (jsonStart >= 0) {
                String jsonPart = message.substring(jsonStart);
                try {
                    result.put("metaError", objectMapper.readTree(jsonPart));
                } catch (Exception ignored) {
                    // not JSON — leave as plain error
                }
            }
        }
        return result;
    }

    private List<String> diagnosticBodyParameters(String templateName, String resolvedLink) {
        if (isGlobalHiringTemplate(templateName)) {
            return Collections.emptyList();
        }
        return List.of(resolvedLink);
    }

    private String diagnosticUrlButtonParameter(String templateName, String resolvedLink) {
        return null;
    }

    private boolean isGlobalHiringTemplate(String templateName) {
        return "anusha_nexus_global_hiring".equalsIgnoreCase(templateName);
    }

    /**
     * Builds https://app.anushatechnologies.com/r/{CODE} for a customer phone.
     * Tries multiple phone formats since the DB may store either "+91XXXX" or "91XXXX".
     * Falls back to the plain Play Store link if no customer match.
     */
    private String resolveReferralLinkForPhone(String phone) {
        if (phone == null || phone.isBlank()) return referralFallbackLink;
        String digits = phone.replaceAll("\\D", "");

        // Try several common storage formats so we match whichever the DB used.
        String[] candidates = new String[] {
                "+" + digits,                    // "+919948598350"
                digits,                          // "919948598350"
                digits.length() > 10 ? "+" + digits.substring(digits.length() - 10) : null, // "+9948598350" — odd but try
                digits.length() > 10 ? digits.substring(digits.length() - 10) : null        // "9948598350"
        };

        Customer customer = null;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            customer = customerRepository.findByPhoneNumber(candidate).orElse(null);
            if (customer != null) break;
        }
        if (customer == null) return referralFallbackLink;
        String code = referralService.ensureCodeFor(customer);
        return referralService.shareLinkFor(code);
    }

    public CampaignStartResponse startAppVideoCampaign(
            MultipartFile file,
            String headerMediaUrl,
            boolean activeOnly
    ) throws IOException {
        if (headerMediaUrl == null || headerMediaUrl.isBlank()) {
            throw new IllegalArgumentException("S3 video URL is required.");
        }
        String headerMediaId = whatsAppService.uploadMediaUrlToMeta(headerMediaUrl);
        return startCampaign(file, "pg_app_video_v1", headerMediaUrl, headerMediaId, "video", activeOnly, true);
    }

    public CampaignStartResponse startAppVideoCampaign(
            MultipartFile file,
            String headerMediaUrl,
            MultipartFile headerMediaFile,
            boolean activeOnly
    ) throws IOException {
        String headerMediaId = null;
        String headerMediaType = "video";
        if (headerMediaFile != null && !headerMediaFile.isEmpty()) {
            String contentType = headerMediaFile.getContentType() == null ? "" : headerMediaFile.getContentType().toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("video/")) {
                throw new IllegalArgumentException("WhatsApp template header must be a video file.");
            }
            headerMediaId = whatsAppService.uploadMediaToMeta(headerMediaFile);
        } else if (headerMediaUrl != null && !headerMediaUrl.isBlank()) {
            headerMediaId = whatsAppService.uploadMediaUrlToMeta(headerMediaUrl);
        }
        return startCampaign(file, "pg_app_video_v1", headerMediaUrl, headerMediaId, headerMediaType, activeOnly, true);
    }

    private CampaignStartResponse startCampaign(
            MultipartFile file,
            String templateName,
            String headerMediaUrl,
            boolean activeOnly,
            boolean useNameAsFirstVariable
    ) throws IOException {
        return startCampaign(file, templateName, headerMediaUrl, null, null, activeOnly, useNameAsFirstVariable);
    }

    private CampaignStartResponse startCampaign(
            MultipartFile file,
            String templateName,
            String headerMediaUrl,
            String headerMediaId,
            String headerMediaType,
            boolean activeOnly,
            boolean useNameAsFirstVariable
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a .xlsx or .csv file.");
        }
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("Template name is required.");
        }

        List<CampaignRecipient> recipients = parseRecipients(file);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file did not contain any valid data rows.");
        }

        String campaignId = UUID.randomUUID().toString();
        CampaignExecution execution = new CampaignExecution(
                campaignId,
                templateName.trim(),
                sanitizeText(file.getOriginalFilename()),
                recipients.size(),
                activeOnly,
                sanitizeText(headerMediaUrl),
                sanitizeText(headerMediaId),
                sanitizeText(headerMediaType),
                useNameAsFirstVariable,
                LocalDateTime.now()
        );
        campaigns.put(campaignId, execution);

        campaignExecutor.submit(() -> runCampaign(execution, recipients));

        return new CampaignStartResponse(
                campaignId,
                execution.getStatus(),
                recipients.size(),
                "WhatsApp campaign queued successfully."
        );
    }

    public CampaignStatusResponse getCampaignStatus(String campaignId) {
        CampaignExecution execution = campaigns.get(campaignId);
        if (execution == null) {
            throw new IllegalArgumentException("Campaign not found.");
        }
        return execution.snapshot();
    }

    public void recordMetaStatus(String messageId, String status, String detail) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        MessageIndex index = messageIndex.get(messageId);
        if (index == null) {
            return;
        }
        CampaignExecution execution = campaigns.get(index.campaignId());
        if (execution != null) {
            execution.updateResultStatus(index.rowNumber(), normalizeMetaStatus(status), detail, messageId);
        }
    }

    private void runCampaign(CampaignExecution execution, List<CampaignRecipient> recipients) {
        execution.markRunning();

        try {
            for (CampaignRecipient recipient : recipients) {
                if (execution.isActiveOnly() && !recipient.active()) {
                    execution.addResult(new RecipientResult(
                            recipient.rowNumber(),
                            recipient.name(),
                            recipient.phoneNumber(),
                            "SKIPPED",
                            "Row skipped because Active was not enabled.",
                            null
                    ));
                    continue;
                }

                if (recipient.phoneNumber() == null || recipient.phoneNumber().isBlank()) {
                    execution.addResult(new RecipientResult(
                            recipient.rowNumber(),
                            recipient.name(),
                            "",
                            "FAILED",
                            "Phone number is missing.",
                            null
                    ));
                    continue;
                }

                try {
                    List<String> bodyParameters = recipient.bodyParameters();
                    String urlButtonParameter = null;
                    if (isGlobalHiringTemplate(execution.getTemplateName())) {
                        bodyParameters = Collections.emptyList();
                    } else if (execution.isUseNameAsFirstVariable() && bodyParameters.isEmpty()) {
                        bodyParameters = List.of(recipient.name() == null || recipient.name().isBlank()
                                ? "Customer"
                                : recipient.name());
                    }

                    String metaResponse = urlButtonParameter != null
                            ? whatsAppService.sendTemplateMessageWithHeaderMediaIdAndUrlButton(
                                    recipient.phoneNumber(),
                                    execution.getTemplateName(),
                                    bodyParameters,
                                    execution.getHeaderMediaId(),
                                    execution.getHeaderMediaType(),
                                    urlButtonParameter)
                            : execution.hasHeaderMediaId()
                                    ? whatsAppService.sendTemplateMessageWithHeaderMediaId(
                                            recipient.phoneNumber(),
                                            execution.getTemplateName(),
                                            bodyParameters,
                                            execution.getHeaderMediaId(),
                                            execution.getHeaderMediaType())
                                    : whatsAppService.sendTemplateMessage(
                                            recipient.phoneNumber(),
                                            execution.getTemplateName(),
                                            bodyParameters,
                                            execution.getHeaderMediaUrl());
                    String messageId = extractMetaMessageId(metaResponse);
                    if (messageId != null && !messageId.isBlank()) {
                        messageIndex.put(messageId, new MessageIndex(execution.getCampaignId(), recipient.rowNumber()));
                    }
                    execution.addResult(new RecipientResult(
                            recipient.rowNumber(),
                            recipient.name(),
                            recipient.phoneNumber(),
                            "ACCEPTED",
                            messageId == null
                                    ? "Accepted by Meta, waiting for delivery webhook."
                                    : "Accepted by Meta. Message id: " + messageId,
                            messageId
                    ));
                } catch (Exception ex) {
                    execution.addResult(new RecipientResult(
                            recipient.rowNumber(),
                            recipient.name(),
                            recipient.phoneNumber(),
                            "FAILED",
                            simplifyError(ex),
                            null
                    ));
                }
            }

            execution.markCompleted();
        } catch (Exception ex) {
            execution.markFailed(simplifyError(ex));
        }
    }

    private List<CampaignRecipient> parseRecipients(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> rows;

        if (fileName.endsWith(".csv")) {
            rows = readCsvRows(file.getInputStream());
        } else if (fileName.endsWith(".xlsx")) {
            rows = readXlsxRows(file.getInputStream());
        } else {
            throw new IllegalArgumentException("Only .xlsx and .csv files are supported.");
        }

        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        int headerIndex = findHeaderRowIndex(rows);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("Could not find a header row with Phone Number.");
        }

        SpreadsheetColumns columns = resolveColumns(rows.get(headerIndex));
        List<CampaignRecipient> recipients = new ArrayList<>();

        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isRowEmpty(row)) {
                continue;
            }

            String phoneNumber = normalizePhoneCell(getCell(row, columns.phoneColumn()));
            String name = sanitizeText(getCell(row, columns.nameColumn()));
            boolean active = resolveActiveValue(getCell(row, columns.activeColumn()));
            List<String> variables = extractVariables(row, columns, name);

            if (phoneNumber.isBlank() && variables.isEmpty()) {
                continue;
            }

            recipients.add(new CampaignRecipient(
                    i + 1,
                    name,
                    phoneNumber,
                    active,
                    variables
            ));
        }

        return recipients;
    }

    private String extractMetaMessageId(String metaResponse) {
        if (metaResponse == null || metaResponse.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metaResponse);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && messages.size() > 0) {
                String id = messages.get(0).path("id").asText(null);
                return id == null || id.isBlank() ? null : id;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String normalizeMetaStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private List<List<String>> readCsvRows(InputStream inputStream) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (ch == ',' && !insideQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        values.add(current.toString().trim());
        return values;
    }

    private List<List<String>> readXlsxRows(InputStream inputStream) throws IOException {
        List<String> sharedStrings = new ArrayList<>();
        Map<String, byte[]> worksheetBytes = new LinkedHashMap<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                byte[] bytes = zipInputStream.readAllBytes();
                if ("xl/sharedStrings.xml".equals(entryName)) {
                    sharedStrings = readSharedStrings(bytes);
                } else if (entryName.startsWith("xl/worksheets/sheet") && entryName.endsWith(".xml")) {
                    worksheetBytes.put(entryName, bytes);
                }
            }
        }

        if (worksheetBytes.isEmpty()) {
            throw new IllegalArgumentException("Could not read any worksheet from the Excel file.");
        }

        byte[] firstSheet = worksheetBytes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not read the first worksheet."));

        return extractRowsFromWorksheet(firstSheet, sharedStrings);
    }

    private List<String> readSharedStrings(byte[] bytes) {
        List<String> sharedStrings = new ArrayList<>();
        Document document = parseXml(bytes);
        NodeList items = document.getElementsByTagName("si");

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            NodeList textNodes = item.getElementsByTagName("t");
            StringBuilder builder = new StringBuilder();
            for (int j = 0; j < textNodes.getLength(); j++) {
                builder.append(textNodes.item(j).getTextContent());
            }
            sharedStrings.add(builder.toString().trim());
        }

        return sharedStrings;
    }

    private List<List<String>> extractRowsFromWorksheet(byte[] bytes, List<String> sharedStrings) {
        Document document = parseXml(bytes);
        NodeList rowNodes = document.getElementsByTagName("row");
        List<List<String>> rows = new ArrayList<>();

        for (int i = 0; i < rowNodes.getLength(); i++) {
            Element rowElement = (Element) rowNodes.item(i);
            NodeList cellNodes = rowElement.getElementsByTagName("c");
            Map<Integer, String> indexedValues = new HashMap<>();
            int maxIndex = -1;

            for (int j = 0; j < cellNodes.getLength(); j++) {
                Element cell = (Element) cellNodes.item(j);
                String reference = cell.getAttribute("r");
                int columnIndex = columnNameToIndex(reference.replaceAll("\\d", ""));
                String value = readCellValue(cell, sharedStrings);
                indexedValues.put(columnIndex, value);
                maxIndex = Math.max(maxIndex, columnIndex);
            }

            if (maxIndex < 0) {
                rows.add(Collections.emptyList());
                continue;
            }

            List<String> row = new ArrayList<>(Collections.nCopies(maxIndex + 1, ""));
            for (Map.Entry<Integer, String> entry : indexedValues.entrySet()) {
                row.set(entry.getKey(), entry.getValue());
            }
            rows.add(row);
        }

        return rows;
    }

    private String readCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList inlineNodes = cell.getElementsByTagName("t");
            return inlineNodes.getLength() > 0 ? inlineNodes.item(0).getTextContent().trim() : "";
        }

        NodeList valueNodes = cell.getElementsByTagName("v");
        if (valueNodes.getLength() == 0) {
            return "";
        }

        String raw = valueNodes.item(0).getTextContent().trim();
        if ("s".equals(type)) {
            int sharedIndex = Integer.parseInt(raw);
            return sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : "";
        }
        return raw;
    }

    private Document parseXml(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to read the uploaded spreadsheet.", ex);
        }
    }

    private int findHeaderRowIndex(List<List<String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            for (String cell : row) {
                String normalized = normalizeHeader(cell);
                if (Objects.equals(normalized, "phonenumber")
                        || Objects.equals(normalized, "phone")
                        || Objects.equals(normalized, "mobilenumber")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private SpreadsheetColumns resolveColumns(List<String> headerRow) {
        int phoneColumn = -1;
        Integer nameColumn = null;
        Integer activeColumn = null;
        Map<Integer, Integer> variableColumns = new TreeMap<>();

        for (int i = 0; i < headerRow.size(); i++) {
            String normalized = normalizeHeader(headerRow.get(i));
            if (normalized.isBlank()) {
                continue;
            }

            if (phoneColumn < 0 && (normalized.equals("phonenumber")
                    || normalized.equals("phone")
                    || normalized.equals("mobilenumber")
                    || normalized.equals("mobile")
                    || normalized.equals("number"))) {
                phoneColumn = i;
                continue;
            }

            if (nameColumn == null && (normalized.equals("name")
                    || normalized.equals("customername")
                    || normalized.equals("username"))) {
                nameColumn = i;
                continue;
            }

            if (activeColumn == null && (normalized.equals("active")
                    || normalized.equals("isactive")
                    || normalized.equals("enabled")
                    || normalized.equals("status"))) {
                activeColumn = i;
                continue;
            }

            Matcher matcher = VARIABLE_HEADER_PATTERN.matcher(normalized);
            if (matcher.matches()) {
                variableColumns.put(Integer.parseInt(matcher.group(1)), i);
            }
        }

        if (phoneColumn < 0) {
            throw new IllegalArgumentException("Phone Number column is required.");
        }
        // Phone column is the only hard requirement; body params are optional
        // (templates with no {{variables}} in the body send fine without params)

        return new SpreadsheetColumns(phoneColumn, nameColumn, activeColumn, variableColumns);
    }

    private List<String> extractVariables(List<String> row, SpreadsheetColumns columns, String fallbackName) {
        if (!columns.variableColumns().isEmpty()) {
            int maxVariable = columns.variableColumns().keySet().stream().max(Integer::compareTo).orElse(0);
            List<String> variables = new ArrayList<>();
            for (int i = 1; i <= maxVariable; i++) {
                Integer columnIndex = columns.variableColumns().get(i);
                variables.add(columnIndex == null ? "" : sanitizeText(getCell(row, columnIndex)));
            }
            return trimTrailingEmptyValues(variables);
        }
        // Name column is for display/tracking only — not a template body param.
        // For templates that need {{1}}, use a Var1 column in the spreadsheet.
        return Collections.emptyList();
    }

    private List<String> trimTrailingEmptyValues(List<String> values) {
        int end = values.size();
        while (end > 0 && (values.get(end - 1) == null || values.get(end - 1).isBlank())) {
            end--;
        }
        return end <= 0 ? Collections.emptyList() : new ArrayList<>(values.subList(0, end));
    }

    private boolean resolveActiveValue(String value) {
        String normalized = sanitizeText(value).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return true;
        }
        return !normalized.equals("no")
                && !normalized.equals("false")
                && !normalized.equals("0")
                && !normalized.equals("inactive")
                && !normalized.equals("disabled");
    }

    /**
     * Normalises spreadsheet phone cells into Meta-friendly E.164 (no plus).
     *  - Excel may give "9948598350.0" → strip the ".0"
     *  - "+91 9948598350" / "91-9948-598-350" → strip non-digits
     *  - "9948598350" (10 digits) → prepend default country code (91 for India)
     *  - "919948598350" (12 digits already) → leave as is
     */
    private String normalizePhoneCell(String value) {
        String text = sanitizeText(value);
        if (text.isBlank()) {
            return "";
        }

        // Excel sometimes stores the cell as a number — strip ".0" / scientific notation.
        try {
            BigDecimal decimal = new BigDecimal(text);
            text = decimal.stripTrailingZeros().toPlainString().replace(".0", "");
        } catch (NumberFormatException ignored) {
            // Already a string — fall through.
        }

        // Strip everything that is not a digit (drops + - spaces parens).
        String digits = text.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }

        // 10-digit Indian mobile (starts with 6/7/8/9) — auto-prepend country code.
        if (digits.length() == 10 && "6789".indexOf(digits.charAt(0)) >= 0) {
            return defaultCountryCode + digits;
        }

        // Already has a country code (12+ digits typically) — return as is.
        return digits;
    }

    private String simplifyError(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
        return sanitizeText(message);
    }

    private boolean isRowEmpty(List<String> row) {
        return row.stream().map(this::sanitizeText).allMatch(String::isBlank);
    }

    private String getCell(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "").trim();
    }

    private String normalizeHeader(String value) {
        return sanitizeText(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private int columnNameToIndex(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return 0;
        }
        int index = 0;
        for (int i = 0; i < columnName.length(); i++) {
            index = (index * 26) + (Character.toUpperCase(columnName.charAt(i)) - 'A' + 1);
        }
        return index - 1;
    }

    public record CampaignStartResponse(
            String campaignId,
            String status,
            int totalRows,
            String message
    ) {
    }

    public record CampaignStatusResponse(
            String campaignId,
            String templateName,
            String sourceFileName,
            boolean activeOnly,
            String headerMediaUrl,
            String status,
            String errorMessage,
            int totalRows,
            int processed,
            int sent,
            int failed,
            int skipped,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            List<RecipientResult> results
    ) {
    }

    public record RecipientResult(
            int rowNumber,
            String name,
            String phoneNumber,
            String status,
            String message,
            String messageId
    ) {
    }

    private record MessageIndex(
            String campaignId,
            int rowNumber
    ) {
    }

    private record CampaignRecipient(
            int rowNumber,
            String name,
            String phoneNumber,
            boolean active,
            List<String> bodyParameters
    ) {
    }

    private record SpreadsheetColumns(
            int phoneColumn,
            Integer nameColumn,
            Integer activeColumn,
            Map<Integer, Integer> variableColumns
    ) {
    }

    private static final class CampaignExecution {
        private final String campaignId;
        private final String templateName;
        private final String sourceFileName;
        private final int totalRows;
        private final boolean activeOnly;
        private final String headerMediaUrl;
        private final String headerMediaId;
        private final String headerMediaType;
        private final boolean useNameAsFirstVariable;
        private final LocalDateTime createdAt;
        private final List<RecipientResult> results = new ArrayList<>();

        private String status = "QUEUED";
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private int processed;
        private int sent;
        private int failed;
        private int skipped;

        private CampaignExecution(
                String campaignId,
                String templateName,
                String sourceFileName,
                int totalRows,
                boolean activeOnly,
                String headerMediaUrl,
                String headerMediaId,
                String headerMediaType,
                boolean useNameAsFirstVariable,
                LocalDateTime createdAt
        ) {
            this.campaignId = campaignId;
            this.templateName = templateName;
            this.sourceFileName = sourceFileName;
            this.totalRows = totalRows;
            this.activeOnly = activeOnly;
            this.headerMediaUrl = headerMediaUrl;
            this.headerMediaId = headerMediaId;
            this.headerMediaType = headerMediaType;
            this.useNameAsFirstVariable = useNameAsFirstVariable;
            this.createdAt = createdAt;
        }

        private synchronized void markRunning() {
            status = "RUNNING";
            startedAt = LocalDateTime.now();
        }

        private synchronized void markCompleted() {
            status = "COMPLETED";
            completedAt = LocalDateTime.now();
        }

        private synchronized void markFailed(String message) {
            status = "FAILED";
            errorMessage = message;
            completedAt = LocalDateTime.now();
        }

        private synchronized void addResult(RecipientResult result) {
            if (results.size() < MAX_STORED_RESULTS) {
                results.add(result);
            }

            processed++;
            switch (result.status()) {
                case "ACCEPTED", "SENT", "DELIVERED", "READ" -> sent++;
                case "SKIPPED" -> skipped++;
                default -> failed++;
            }
        }

        private synchronized CampaignStatusResponse snapshot() {
            int snapshotSent = 0;
            int snapshotFailed = 0;
            int snapshotSkipped = 0;
            for (RecipientResult result : results) {
                switch (result.status()) {
                    case "ACCEPTED", "SENT", "DELIVERED", "READ" -> snapshotSent++;
                    case "SKIPPED" -> snapshotSkipped++;
                    default -> snapshotFailed++;
                }
            }

            return new CampaignStatusResponse(
                    campaignId,
                    templateName,
                    sourceFileName,
                    activeOnly,
                    headerMediaUrl,
                    status,
                    errorMessage,
                    totalRows,
                    processed,
                    snapshotSent,
                    snapshotFailed,
                    snapshotSkipped,
                    createdAt,
                    startedAt,
                    completedAt,
                    new ArrayList<>(results)
            );
        }

        private String getStatus() {
            return status;
        }

        private String getCampaignId() {
            return campaignId;
        }

        private String getTemplateName() {
            return templateName;
        }

        private boolean isActiveOnly() {
            return activeOnly;
        }

        private boolean isUseNameAsFirstVariable() {
            return useNameAsFirstVariable;
        }

        private String getHeaderMediaUrl() {
            return headerMediaUrl;
        }

        private boolean hasHeaderMediaId() {
            return headerMediaId != null && !headerMediaId.isBlank();
        }

        private String getHeaderMediaId() {
            return headerMediaId;
        }

        private String getHeaderMediaType() {
            return headerMediaType == null || headerMediaType.isBlank() ? "video" : headerMediaType;
        }

        private synchronized void updateResultStatus(int rowNumber, String nextStatus, String detail, String messageId) {
            for (int i = 0; i < results.size(); i++) {
                RecipientResult result = results.get(i);
                if (result.rowNumber() == rowNumber) {
                    results.set(i, new RecipientResult(
                            result.rowNumber(),
                            result.name(),
                            result.phoneNumber(),
                            nextStatus,
                            detail == null || detail.isBlank() ? "Meta status: " + nextStatus : detail,
                            messageId
                    ));
                    return;
                }
            }
        }
    }
}
