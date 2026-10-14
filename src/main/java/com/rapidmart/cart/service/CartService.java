package com.rapidmart.cart.service;

import com.rapidmart.cart.dto.*;
import com.rapidmart.cart.entity.Cart;
import com.rapidmart.cart.entity.CartItem;
import com.rapidmart.cart.repository.CartItemRepository;
import com.rapidmart.cart.repository.CartRepository;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.common.exception.ResourceNotFoundException;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.product.entity.Product;
import com.rapidmart.product.repository.ProductRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       InventoryRepository inventoryRepository,
                       UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional
    public Cart getOrCreateCartEntity(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(new Cart(user)));
    }

    @Transactional
    public CartDto getMyCart() {
        User user = getAuthenticatedUser();
        Cart cart = getOrCreateCartEntity(user);
        return mapToDto(cart);
    }

    @Transactional
    public CartDto addToCart(AddToCartRequest request) {
        User user = getAuthenticatedUser();
        Cart cart = getOrCreateCartEntity(user);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        if (!product.isActive()) {
            throw new BusinessException("PRODUCT_INACTIVE", "Cannot add inactive product to cart.");
        }

        Inventory inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", product.getId()));

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());
        int targetQuantity = request.getQuantity();

        if (existingItemOpt.isPresent()) {
            targetQuantity += existingItemOpt.get().getQuantity();
        }

        if (targetQuantity > inventory.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    String.format("Requested quantity (%d) exceeds available stock (%d).", targetQuantity, inventory.getQuantity()));
        }

        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            existing.setQuantity(targetQuantity);
            cartItemRepository.save(existing);
        } else {
            CartItem newItem = new CartItem(cart, product, request.getQuantity());
            cartItemRepository.save(newItem);
            cart.getItems().add(newItem);
        }

        return mapToDto(cartRepository.save(cart));
    }

    @Transactional
    public CartDto updateItemQuantity(Long itemId, UpdateCartQuantityRequest request) {
        User user = getAuthenticatedUser();
        Cart cart = getOrCreateCartEntity(user);

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw BusinessException.forbidden("Cart item does not belong to your cart.");
        }

        Inventory inventory = inventoryRepository.findByProductId(item.getProduct().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", item.getProduct().getId()));

        if (request.getQuantity() > inventory.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    String.format("Requested quantity (%d) exceeds available stock (%d).", request.getQuantity(), inventory.getQuantity()));
        }

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        return mapToDto(cartRepository.findById(cart.getId()).get());
    }

    @Transactional
    public CartDto removeItem(Long itemId) {
        User user = getAuthenticatedUser();
        Cart cart = getOrCreateCartEntity(user);

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw BusinessException.forbidden("Cart item does not belong to your cart.");
        }

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        return mapToDto(cartRepository.save(cart));
    }

    @Transactional
    public void clearCart(Cart cart) {
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    public CartDto mapToDto(Cart cart) {
        List<CartItemDto> itemDtos = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int itemCount = 0;

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            BigDecimal unitPrice = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(lineTotal);
            itemCount += item.getQuantity();

            Integer stock = inventoryRepository.findByProductId(product.getId())
                    .map(Inventory::getQuantity)
                    .orElse(0);

            itemDtos.add(new CartItemDto(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    product.getSku(),
                    product.getUnit(),
                    unitPrice,
                    product.getImageUrl(),
                    item.getQuantity(),
                    lineTotal,
                    stock
            ));
        }

        return new CartDto(cart.getId(), itemDtos, subtotal, itemCount);
    }
}
