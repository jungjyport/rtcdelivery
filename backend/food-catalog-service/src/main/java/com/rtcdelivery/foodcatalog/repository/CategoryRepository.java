package com.rtcdelivery.foodcatalog.repository;

import com.rtcdelivery.foodcatalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByIsActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<Category> findByCode(String code);

    boolean existsByCode(String code);
}
