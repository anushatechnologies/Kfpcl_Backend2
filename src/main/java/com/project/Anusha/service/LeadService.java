package com.project.Anusha.service;

import com.project.Anusha.dto.ContactSellerRequest;
import com.project.Anusha.model.ContactLead;
import com.project.Anusha.model.Product;
import com.project.Anusha.model.Supplier;
import com.project.Anusha.repository.ContactLeadRepository;
import com.project.Anusha.repository.ProductRepository;
import com.project.Anusha.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LeadService {

    @Autowired
    private ContactLeadRepository contactLeadRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    public ContactLead recordLead(ContactSellerRequest request) {
        Optional<Product> productOpt = productRepository.findById(request.getProductId());
        Optional<Supplier> supplierOpt = supplierRepository.findById(request.getSupplierId());

        if (productOpt.isEmpty()) {
            throw new IllegalArgumentException("Product not found with id: " + request.getProductId());
        }
        if (supplierOpt.isEmpty()) {
            throw new IllegalArgumentException("Supplier not found with id: " + request.getSupplierId());
        }

        Long buyerId = request.getBuyerId() != null ? request.getBuyerId() : 1L; // Fallback without JWT

        ContactLead lead = new ContactLead(
                buyerId,
                productOpt.get(),
                supplierOpt.get(),
                request.getContactType() != null ? request.getContactType().toUpperCase() : "CALL"
        );

        return contactLeadRepository.save(lead);
    }
}
