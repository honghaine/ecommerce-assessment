package com.flashsale.catalog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashsale.catalog.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByRegionAndSku(String region, String sku);

    boolean existsByRegionAndSku(String region, String sku);

    List<Product> findBySellerIdOrderByIdDesc(long sellerId);

    Optional<Product> findByIdAndSellerId(long id, long sellerId);
}
