package com.farmatodo.challenge.cart.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.farmatodo.challenge.cart.ShoppingCart;
import com.farmatodo.challenge.cart.domain.Cart;
import com.farmatodo.challenge.cart.domain.Cart.CartItem;
import com.farmatodo.challenge.cart.domain.Cart.CartRepository;
import com.farmatodo.challenge.product.InsufficientStockException;
import com.farmatodo.challenge.product.ProductInventory;
import com.farmatodo.challenge.product.ProductInventory.ProductSnapshot;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cart use cases. Stock is checked when adding (fast feedback) but not reserved: reservation happens only at
 * checkout, so carts never "hold" stock that other customers could buy.
 */
@Service
public class CartService implements ShoppingCart {

    private final CartRepository carts;
    private final ProductInventory inventory;

    CartService(CartRepository carts, ProductInventory inventory) {
        this.carts = carts;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public CartView view(UUID customerId) {
        return carts.findByCustomer(customerId).map(this::toView).orElse(CartView.EMPTY);
    }

    @Transactional
    public CartView add(UUID customerId, UUID productId, int quantity) {
        ProductSnapshot product = inventory.find(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product " + productId + " does not exist"));
        Cart cart = carts.findByCustomer(customerId).orElseGet(() -> Cart.empty(customerId));

        int requested = cart.quantityOf(productId) + quantity;
        if (requested > product.stock()) {
            throw new InsufficientStockException(productId, product.name(), product.stock());
        }
        cart.add(productId, quantity);
        return toView(carts.save(cart));
    }

    @Transactional
    public CartView remove(UUID customerId, UUID productId) {
        Cart cart = carts.findByCustomer(customerId)
                .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Product " + productId + " is not in the cart"));
        cart.remove(productId);
        return toView(carts.save(cart));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartLine> linesOf(UUID customerId) {
        return carts.findByCustomer(customerId)
                .map(cart -> cart.items().stream().map(i -> new CartLine(i.productId(), i.quantity())).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public void clear(UUID customerId) {
        carts.findByCustomer(customerId).ifPresent(cart -> {
            cart.clear();
            carts.save(cart);
        });
    }

    private CartView toView(Cart cart) {
        Map<UUID, ProductSnapshot> products = inventory.findAll(cart.items().stream().map(CartItem::productId).toList());
        List<CartView.Line> lines = cart.items().stream()
                .filter(item -> products.containsKey(item.productId()))
                .map(item -> {
                    ProductSnapshot p = products.get(item.productId());
                    return new CartView.Line(p.id(), p.name(), p.price(), item.quantity(),
                            p.price().multiply(BigDecimal.valueOf(item.quantity())));
                })
                .sorted(Comparator.comparing(CartView.Line::name))
                .toList();
        BigDecimal total = lines.stream().map(CartView.Line::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(lines, total);
    }

    /** Cart priced with current catalog prices. */
    public record CartView(List<Line> items, BigDecimal total) {
        static final CartView EMPTY = new CartView(List.of(), BigDecimal.ZERO);

        public record Line(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
        }
    }
}
