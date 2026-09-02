package com.project.Anusha.service;

import com.project.Anusha.exception.ResourceNotFoundException;
import com.project.Anusha.model.Address;
import com.project.Anusha.model.Customer;
import com.project.Anusha.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    public Address getAddressByIdAndCustomer(Long addressId, Customer customer) {
        Address address = addressRepository.findByIdAndArchivedFalse(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));
        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Address does not belong to the customer");
        }
        return address;
    }
}
