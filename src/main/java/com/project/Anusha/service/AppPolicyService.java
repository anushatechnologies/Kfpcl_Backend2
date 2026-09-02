package com.project.Anusha.service;

import com.project.Anusha.model.AppPolicy;
import com.project.Anusha.repository.AppPolicyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class AppPolicyService {

    @Autowired
    private AppPolicyRepository policyRepository;

    /**
     * Get policy by type
     */
    public Optional<AppPolicy> getPolicyByType(String type) {
        return policyRepository.findByType(type);
    }

    /**
     * Update policy content
     */
    public AppPolicy updatePolicy(String type, String content) {
        AppPolicy policy = policyRepository.findByType(type)
                .orElse(new AppPolicy(null, type, content, LocalDateTime.now()));
        
        policy.setContent(content);
        policy.setUpdatedAt(LocalDateTime.now());
        
        return policyRepository.save(policy);
    }

    /**
     * Initialize default values for policies if not present
     */
    @PostConstruct
    public void initPolicies() {
        initPolicy("PRIVACY_POLICY", "{\"section1\": \"In order to access the services of the Platform, You will have to register and create an account on the Platform by providing required details.\", \"section2\": \"You are solely responsible for the information provided. You must ensure that your account information is accurate and updated.\", \"section3\": \"Confidentiality of your account credentials shall be your responsibility.\", \"payments1\": \"All payments shall be made in Indian Rupees only.\", \"payments2\": \"Anusha Bazaar may use third-party payment gateways to process payments.\", \"payments3\": \"You confirm that you are authorized to use the provided payment details.\", \"payments4\": \"The payment facility is not a banking service but only a facilitator.\"}");
        
        initPolicy("RETURNS_REFUNDS", "{\"cancellationAndRefund\": \"Last updated on 10-07-2026 13:43:22\\nANUSHA BAZAAR believes in helping its customers as far as possible, and has therefore a liberal\\ncancellation policy. Under this policy:\\n• Cancellations will be considered only if the request is made immediately after placing the order.\\nHowever, the cancellation request may not be entertained if the orders have been communicated to the\\nvendors/merchants and they have initiated the process of shipping them.\\n• ANUSHA BAZAAR does not accept cancellation requests for perishable items like flowers, eatables\\netc. However, refund/replacement can be made if the customer establishes that the quality of product\\ndelivered is not good.\\n• In case of receipt of damaged or defective items please report the same to our Customer Service team.\\nThe request will, however, be entertained once the merchant has checked and determined the same at his\\nown end. This should be reported within Only same day days of receipt of the products. In case you feel\\nthat the product received is not as shown on the site or as per your expectations, you must bring it to the\\nnotice of our customer service within Only same day days of receiving the product. The Customer\\nService Team after looking into your complaint will take an appropriate decision.\\n• In case of complaints regarding products that come with a warranty from manufacturers, please refer\\nthe issue to them. In case of any Refunds approved by the ANUSHA BAZAAR, it’ll take 1-2 Days days\\nfor the refund to be processed to the end customer.\", \"taxes\": \"In respect of the order placed by you, tax invoices will be issued as per applicable law. Your order may have the following components and corresponding documents:\\n\\n• Sale of goods – Tax invoice cum bill of supply issued by/on behalf of the relevant seller;\\n• Supply of services – Tax invoice issued by/on behalf of the relevant service provider.\\n• For Third Party Offerings, Tax Invoice/bill of supply shall be issued on behalf of the relevant Third Party Seller.\\n• The above documents can be seen on the order summary page once the goods have been delivered to you.\\n\\nYou acknowledge and agree that entitlement to any GST benefits shall be subject to GST terms and submission of valid GST number. Not all products/services are eligible for GST invoice.\", \"cancellation\": \"You acknowledge that cancellation or attempted cancellation may amount to breach of Terms and shall be permitted subject to acceptance by Anusha Bazaar.\\n\\nOrders may be cancelled by Anusha Bazaar if:\\n(a) fraudulent transaction suspected\\n(b) violation of Terms\\n(c) product unavailability\\n(d) technical or logistical issues\\n\\nRefunds for such cancellations will be initiated within approximately 72 hours.\\n\\nAnusha Bazaar reserves the right to cancel orders and initiate refunds in the form of credit/cashback/coupon/promotional codes.\\n\\nWe reserve the right to deny access to fraudulent or non-complying users.\", \"returns\": \"Products once delivered/services once fulfilled are non-returnable except:\\n\\n(a) damaged, defective, expired, or incorrectly delivered\\n(b) if the product policy expressly permits return\\n\\nNo refunds will be permitted due to:\\n• incorrect location provided\\n• unresponsive customer\\n• building restrictions\\n\\nFor digital goods (gift cards/vouchers), no return/refund applies.\\n\\nAll refunds for permitted returns and cancellations will be processed within 7 working days.\\n\\nRefunds for COD purchases will be issued via promotional codes (valid 30 days).\\n\\nRefunds cannot be transferred back to another payment method once initiated.\\n\\nAll refunds shall be made in Indian Rupees only.\", \"returnPolicy\": \"We have a 7-day return policy, which means you have 7 days after receiving your item to request a return.\", \"replacement\": \"After inspecting the returned/damaged items we shall provide replacement / exchange within 7–10 business days.\", \"refundProcess\": \"We will notify you once we’ve received and inspected your return.\\n\\nIf approved, you’ll be automatically refunded and credited on your original payment method within 7 business days.\", \"shipping\": \"Products will get shipped and delivered in 6 to 8 days.\"}");
        
        initPolicy("TERMS_CONDITIONS", "{\"introduction\": \"These Terms govern your use of the Platform. By accessing the Platform, you agree to be bound by these Terms.\", \"access\": \"Anusha Bazaar facilitates transactions between Users and Sellers across serviceable areas.\", \"partners\": \"Delivery services are provided by independent contractors.\", \"comments\": \"By submitting Comments, you grant Anusha Bazaar a worldwide license to use them.\", \"law\": \"These Terms shall be governed by the laws of Hyderabad, India.\"}");
    }

    private void initPolicy(String type, String defaultContent) {
        if (!policyRepository.existsByType(type)) {
            AppPolicy policy = new AppPolicy();
            policy.setType(type);
            policy.setContent(defaultContent);
            policy.setUpdatedAt(LocalDateTime.now());
            policyRepository.save(policy);
        }
    }
}
