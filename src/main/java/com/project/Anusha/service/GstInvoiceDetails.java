package com.project.Anusha.service;

public final class GstInvoiceDetails {
    public static final String SELLER_NAME = "ANUSHA BAZAAR";
    public static final String LEGAL_NAME = "NALLAPANENI ANJIBABU CHOUDARY";
    public static final String GSTIN = "36AIJPN3614J1Z4";
    public static final String STATE = "Telangana";
    public static final String STATE_CODE = "36";
    public static final String ADDRESS = "Manjeera Trinity 501, Manjeera Trinity Corporate, eSeva Ln, Kukatpally Housing Board Colony, K P H B Phase 3, Kukatpally, Hyderabad, Telangana 500072";
    public static final boolean REVERSE_CHARGE = false;

    private GstInvoiceDetails() {
    }

    public static boolean isIntraState(String buyerState) {
        return buyerState == null || buyerState.isBlank() || STATE.equalsIgnoreCase(buyerState.trim());
    }

    public static String stateCodeFor(String state) {
        if (state == null || state.isBlank() || STATE.equalsIgnoreCase(state.trim())) {
            return STATE_CODE;
        }
        return "";
    }
}
