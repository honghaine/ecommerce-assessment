package com.flashsale.catalog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.catalog.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByRegionAndSku(String region, String sku);
}
