package com.farmatodo.challenge.product.infrastructure.in.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.farmatodo.challenge.product.application.ProductSearchService;
import com.farmatodo.challenge.product.domain.Product;
import com.farmatodo.challenge.product.domain.ProductPorts.PageResult;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProductController {

    private final ProductSearchService searchService;

    ProductController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    /** Search by name/description (case-insensitive). Only products above the configured minimum stock. */
    @GetMapping("/api/v1/products")
    PageResponse<ProductResponse> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        PageResult<Product> result = searchService.search(q, page, size, UUID.fromString(jwt.getSubject()));
        return new PageResponse<>(result.content().stream().map(ProductResponse::from).toList(), result.page(),
                result.size(), result.totalElements(), result.totalPages());
    }

    record ProductResponse(UUID id, String sku, String name, String description, BigDecimal price, int stock) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.id(), p.sku(), p.name(), p.description(), p.price(), p.stock());
        }
    }

    record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
