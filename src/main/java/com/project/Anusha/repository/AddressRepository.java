package com.project.Anusha.repository;

import com.project.Anusha.model.Address;
import com.project.Anusha.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByCustomer(Customer customer);
    Optional<Address> findByCustomerAndIsDefaultTrue(Customer customer);
    List<Address> findByCustomerAndArchivedFalseOrderByIsDefaultDescCreatedAtDesc(Customer customer);
    Optional<Address> findByCustomerAndIsDefaultTrueAndArchivedFalse(Customer customer);
    Optional<Address> findByIdAndArchivedFalse(Long id);
}
