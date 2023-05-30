package com.example.pharmacyrecomandation.pharmacy.repository;

import com.example.pharmacyrecomandation.pharmacy.entity.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {
}
