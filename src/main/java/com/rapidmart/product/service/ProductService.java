package com.rapidmart.product.service;

import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.common.enums.StockMovementType;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.entity.StockMovementHistory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.inventory.repository.StockMovementHistoryRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import com.rapidmart.product.dto.ProductDto;
import com.rapidmart.product.dto.ProductRequest;
import com.rapidmart.product.entity.Category;
import com.rapidmart.product.entity.Product;
import com.rapidmart.product.repository.CategoryRepository;
import com.rapidmart.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final int DEFAULT_MAXIMUM_LEVEL = 1000;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementHistoryRepository stockMovementHistoryRepository;
    private final UserRepository userRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          InventoryRepository inventoryRepository,
                          StockMovementHistoryRepository stockMovementHistoryRepository,
                          UserRepository userRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockMovementHistoryRepository = stockMovementHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> getActiveProducts(Long categoryId, String search, Pageable pageable) {
        Page<Product> products;

        if (categoryId != null && search != null && !search.isBlank()) {
            products = productRepository.searchActiveProductsByCategory(search.trim(), categoryId, pageable);
        } else if (categoryId != null) {
            products = productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
        } else if (search != null && !search.isBlank()) {
            products = productRepository.searchActiveProducts(search.trim(), pageable);
        } else {
            products = productRepository.findByActiveTrue(pageable);
        }

        return products.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return mapToDto(product);
    }

    @Transactional(readOnly = true)
    public ProductDto getActiveProductById(Long id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return mapToDto(product);
    }

    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.getSku().trim())) {
            throw new BusinessException("SKU_EXISTS", "Product with this SKU already exists.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
        if (!category.isActive()) {
            throw new BusinessException("CATEGORY_INACTIVE", "Products cannot be added to an inactive category.");
        }

        int stock = request.getInitialStock() != null ? request.getInitialStock() : 0;
        if (stock > DEFAULT_MAXIMUM_LEVEL) {
            throw new BusinessException("EXCEEDS_MAX_LEVEL", "Initial stock cannot exceed the default maximum level of " + DEFAULT_MAXIMUM_LEVEL + ".");
        }

        Product product = new Product(
                request.getSku().toUpperCase().trim(),
                request.getName().trim(),
                request.getDescription(),
                request.getPrice(),
                request.getUnit().trim(),
                request.getImageUrl(),
                category,
                request.isActive()
        );

        Product savedProduct = productRepository.save(product);

        // Create initial inventory record and log the opening stock as a movement
        Inventory inventory = new Inventory(savedProduct, stock, 10, DEFAULT_MAXIMUM_LEVEL);
        inventoryRepository.save(inventory);
        if (stock > 0) {
            String actorEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            Long actorId = userRepository.findByEmailIgnoreCase(actorEmail).map(User::getId).orElse(null);
            stockMovementHistoryRepository.save(new StockMovementHistory(savedProduct, stock, StockMovementType.INITIAL_STOCK,
                    "PRODUCT_CREATED_" + savedProduct.getSku(), actorId, actorEmail));
        }

        return mapToDto(savedProduct);
    }

    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (!product.getSku().equalsIgnoreCase(request.getSku()) &&
                productRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new BusinessException("SKU_EXISTS", "Product with this SKU already exists.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        product.setSku(request.getSku().toUpperCase().trim());
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setUnit(request.getUnit().trim());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(category);
        product.setActive(request.isActive());

        Product updated = productRepository.save(product);
        return mapToDto(updated);
    }

    @Transactional
    public ProductDto deactivateProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        product.setActive(false);
        Product updated = productRepository.save(product);
        return mapToDto(updated);
    }

    public ProductDto mapToDto(Product product) {
        Integer stock = inventoryRepository.findByProductId(product.getId())
                .map(Inventory::getQuantity)
                .orElse(0);

        return new ProductDto(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getUnit(),
                product.getImageUrl(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.isActive(),
                stock
        );
    }
}
