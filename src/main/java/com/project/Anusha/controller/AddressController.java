package com.project.Anusha.controller;

import com.project.Anusha.dto.AddressDto;
import com.project.Anusha.exception.ResourceNotFoundException;
import com.project.Anusha.model.Address;
import com.project.Anusha.model.Customer;
import com.project.Anusha.repository.AddressRepository;
import com.project.Anusha.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressRepository addressRepository;
    private final CustomerService customerService;

    public AddressController(AddressRepository addressRepository, CustomerService customerService) {
        this.addressRepository = addressRepository;
        this.customerService = customerService;
    }

    private Customer getCustomer(UserDetails userDetails) {
        return customerService.getCustomerByPhone(userDetails.getUsername());
    }

    @GetMapping
    public ResponseEntity<List<AddressDto>> getAddresses(@AuthenticationPrincipal UserDetails userDetails) {
        Customer customer = getCustomer(userDetails);
        List<Address> addresses = addressRepository.findByCustomerAndArchivedFalseOrderByIsDefaultDescCreatedAtDesc(customer);
        List<AddressDto> dtos = addresses.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<AddressDto> addAddress(@AuthenticationPrincipal UserDetails userDetails,
                                                  @Valid @RequestBody AddressDto dto) {
        Customer customer = getCustomer(userDetails);
        Address address = new Address();
        address.setCustomer(customer);
        updateEntityFromDto(address, dto);

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            // clear other defaults
            addressRepository.findByCustomerAndIsDefaultTrueAndArchivedFalse(customer)
                    .ifPresent(existing -> {
                        existing.setIsDefault(false);
                        addressRepository.save(existing);
                    });
        }
        Address saved = addressRepository.save(address);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressDto> updateAddress(@AuthenticationPrincipal UserDetails userDetails,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody AddressDto dto) {
        Customer customer = getCustomer(userDetails);
        Address address = addressRepository.findByIdAndArchivedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!address.getCustomer().equals(customer)) {
            throw new AccessDeniedException("Address does not belong to the customer");
        }
        updateEntityFromDto(address, dto);
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            addressRepository.findByCustomerAndIsDefaultTrueAndArchivedFalse(customer)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            existing.setIsDefault(false);
                            addressRepository.save(existing);
                        }
                    });
        }
        Address saved = addressRepository.save(address);
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAddress(@AuthenticationPrincipal UserDetails userDetails,
                                            @PathVariable Long id) {
        Customer customer = getCustomer(userDetails);
        Address address = addressRepository.findByIdAndArchivedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        if (!address.getCustomer().equals(customer)) {
            throw new AccessDeniedException("Address does not belong to the customer");
        }

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        address.setArchived(true);
        address.setIsDefault(false);
        addressRepository.save(address);

        if (wasDefault) {
            addressRepository.findByCustomerAndArchivedFalseOrderByIsDefaultDescCreatedAtDesc(customer)
                    .stream()
                    .findFirst()
                    .ifPresent(nextDefault -> {
                        nextDefault.setIsDefault(true);
                        addressRepository.save(nextDefault);
                    });
        }

        return ResponseEntity.ok().build();
    }

    private AddressDto toDto(Address address) {
        AddressDto dto = new AddressDto();
        dto.setId(address.getId());
        dto.setAddressType(address.getAddressType());
        dto.setFlatNumber(address.getFlatNumber());
        dto.setAddressLine1(address.getAddressLine1());
        dto.setAddressLine2(address.getAddressLine2());
        dto.setLandmark(address.getLandmark());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPostalCode(address.getPostalCode());
        dto.setLatitude(address.getLatitude());
        dto.setLongitude(address.getLongitude());
        dto.setIsDefault(address.getIsDefault());
        dto.setContactName(address.getContactName());
        dto.setContactPhone(address.getContactPhone());
        return dto;
    }

    private void updateEntityFromDto(Address address, AddressDto dto) {
        address.setAddressType(dto.getAddressType());
        address.setFlatNumber(dto.getFlatNumber());
        address.setAddressLine1(dto.getAddressLine1());
        address.setAddressLine2(dto.getAddressLine2());
        address.setLandmark(dto.getLandmark());
        address.setCity(dto.getCity());
        address.setState(dto.getState());
        address.setPostalCode(dto.getPostalCode());
        address.setLatitude(dto.getLatitude());
        address.setLongitude(dto.getLongitude());
        address.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()));
        address.setArchived(false);
        address.setContactName(dto.getContactName());
        address.setContactPhone(dto.getContactPhone());
    }
}
