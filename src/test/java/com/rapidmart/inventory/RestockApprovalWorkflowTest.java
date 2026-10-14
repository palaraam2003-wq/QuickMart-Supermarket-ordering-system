package com.rapidmart.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rapidmart.common.enums.RestockRequestStatus;
import com.rapidmart.common.enums.Role;
import com.rapidmart.common.enums.StockMovementType;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.inventory.dto.CreateRestockRequest;
import com.rapidmart.inventory.entity.Inventory;
import com.rapidmart.inventory.entity.RestockRequest;
import com.rapidmart.inventory.entity.StockMovementHistory;
import com.rapidmart.inventory.repository.InventoryRepository;
import com.rapidmart.inventory.repository.RestockRequestRepository;
import com.rapidmart.inventory.repository.StockMovementHistoryRepository;
import com.rapidmart.inventory.service.InventoryService;
import com.rapidmart.product.entity.Category;
import com.rapidmart.product.entity.Product;
import com.rapidmart.product.repository.CategoryRepository;
import com.rapidmart.product.repository.ProductRepository;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Separation of duties in the restock workflow: Inventory Officers (INVENTORY_STAFF) create and receive,
 * Managers approve or reject. Runs without a test transaction so row locks and concurrency are real;
 * every row it creates is removed afterwards (scoped by the restock.* emails and RSTK- SKUs).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestockApprovalWorkflowTest {

    private static final String BASE = "/api/v1/inventory/restock-requests";
    private static final String OFFICER = "restock.officer@rapidmart.local";
    private static final String MANAGER = "restock.manager@rapidmart.local";
    private static final String DUAL_ROLE = "restock.dual@rapidmart.local";
    private static final String CASHIER = "restock.cashier@rapidmart.local";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private InventoryService inventoryService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private RestockRequestRepository restockRequestRepository;
    @Autowired private StockMovementHistoryRepository movementRepository;

    private Product product;
    private Product otherProduct;

    @BeforeEach
    void setUp() {
        cleanUp();
        userRepository.save(new User(OFFICER, "x", "Restock Officer", null, true, Set.of(Role.INVENTORY_STAFF)));
        userRepository.save(new User(MANAGER, "x", "Restock Manager", null, true, Set.of(Role.MANAGER)));
        userRepository.save(new User(DUAL_ROLE, "x", "Dual Role", null, true, Set.of(Role.MANAGER, Role.INVENTORY_STAFF)));
        userRepository.save(new User(CASHIER, "x", "Restock Cashier", null, true, Set.of(Role.CASHIER)));

        Category category = categoryRepository.save(new Category("Restock Test Category", "Restock workflow tests", true));
        product = productRepository.save(new Product("RSTK-001", "Restock Rice", "Test rice", new BigDecimal("100.00"), "1 kg", null, category, true));
        otherProduct = productRepository.save(new Product("RSTK-002", "Restock Tea", "Test tea", new BigDecimal("50.00"), "100 g", null, category, true));
        inventoryRepository.save(new Inventory(product, 10, 5, 100));
        inventoryRepository.save(new Inventory(otherProduct, 0, 5, 100));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanUp();
    }

    private void cleanUp() {
        String products = "SELECT id FROM products WHERE sku LIKE 'RSTK-%'";
        String users = "SELECT id FROM app_users WHERE email LIKE 'restock.%@rapidmart.local'";
        jdbcTemplate.update("DELETE FROM audit_logs WHERE actor_email LIKE 'restock.%@rapidmart.local'");
        jdbcTemplate.update("DELETE FROM stock_movement_history WHERE product_id IN (" + products + ")");
        jdbcTemplate.update("DELETE FROM restock_requests WHERE product_id IN (" + products + ") OR requested_by_id IN (" + users + ")");
        jdbcTemplate.update("DELETE FROM inventory WHERE product_id IN (" + products + ")");
        jdbcTemplate.update("DELETE FROM products WHERE sku LIKE 'RSTK-%'");
        jdbcTemplate.update("DELETE FROM categories WHERE name = 'Restock Test Category'");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (" + users + ")");
        jdbcTemplate.update("DELETE FROM app_users WHERE email LIKE 'restock.%@rapidmart.local'");
    }

    // ---------------------------------------------------------------- helpers

    private static RequestPostProcessor as(String email, String... roles) {
        return user(email).roles(roles);
    }

    private static RequestPostProcessor officer() {
        return as(OFFICER, "INVENTORY_STAFF");
    }

    private static RequestPostProcessor manager() {
        return as(MANAGER, "MANAGER");
    }

    private static RequestPostProcessor dualRole() {
        return as(DUAL_ROLE, "MANAGER", "INVENTORY_STAFF");
    }

    private static MockHttpServletRequestBuilder createCall(Long productId, int quantity) {
        return post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"requestedQuantity\":" + quantity + "}");
    }

    private static MockHttpServletRequestBuilder approveCall(Long id) {
        return put(BASE + "/" + id + "/approve").with(csrf());
    }

    private static MockHttpServletRequestBuilder approveCall(Long id, int approvedQuantity) {
        return approveCall(id).contentType(MediaType.APPLICATION_JSON).content("{\"approvedQuantity\":" + approvedQuantity + "}");
    }

    private static MockHttpServletRequestBuilder rejectCall(Long id, String reason) {
        return put(BASE + "/" + id + "/reject").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}");
    }

    private static MockHttpServletRequestBuilder receiveCall(Long id, int quantity) {
        return post(BASE + "/" + id + "/receive").with(csrf()).param("quantityReceived", String.valueOf(quantity));
    }

    private Long createAs(RequestPostProcessor who, Long productId, int quantity) throws Exception {
        String body = mockMvc.perform(createCall(productId, quantity).with(who))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asLong();
    }

    private Long createPending(int quantity) throws Exception {
        return createAs(officer(), product.getId(), quantity);
    }

    private Long createApproved(int quantity) throws Exception {
        Long id = createPending(quantity);
        mockMvc.perform(approveCall(id).with(manager())).andExpect(status().isOk());
        return id;
    }

    private int stockOf(Product p) {
        return inventoryRepository.findByProductId(p.getId()).orElseThrow().getQuantity();
    }

    private RestockRequest reload(Long id) {
        return restockRequestRepository.findById(id).orElseThrow();
    }

    private long receiptMovements(Long requestId) {
        return movementRepository.countByReferenceIdAndMovementType(
                InventoryService.restockReference(requestId), StockMovementType.RECEIVED_STOCK);
    }

    // ---------------------------------------------------------------- creation

    @Test
    void inventoryOfficerCanCreateRequest() throws Exception {
        mockMvc.perform(createCall(product.getId(), 40).with(officer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedQuantity").value(40))
                .andExpect(jsonPath("$.requestedByEmail").value(OFFICER))
                .andExpect(jsonPath("$.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.currentStock").value(10))
                .andExpect(jsonPath("$.approvedByEmail").doesNotExist());

        assertEquals(10, stockOf(product), "creating a request must not change stock");
    }

    @Test
    void requestedQuantityMustBePositive() throws Exception {
        mockMvc.perform(createCall(product.getId(), 0).with(officer())).andExpect(status().isBadRequest());
        mockMvc.perform(createCall(product.getId(), -5).with(officer())).andExpect(status().isBadRequest());
        assertTrue(restockRequestRepository.findAll().stream().noneMatch(r -> r.getProduct().getId().equals(product.getId())));
    }

    @Test
    void creatingForUnknownProductReturns404() throws Exception {
        mockMvc.perform(createCall(987654321L, 10).with(officer())).andExpect(status().isNotFound());
    }

    @Test
    void duplicateOpenRequestsForSameProductAreRejected() throws Exception {
        Long first = createPending(20);
        mockMvc.perform(createCall(product.getId(), 5).with(officer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_OPEN_REQUEST"));

        mockMvc.perform(approveCall(first).with(manager())).andExpect(status().isOk());
        mockMvc.perform(createCall(product.getId(), 5).with(officer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_OPEN_REQUEST"));

        // Other products are unaffected, and once the open request is closed a new one is allowed.
        createAs(officer(), otherProduct.getId(), 5);
        mockMvc.perform(receiveCall(first, 20).with(officer())).andExpect(status().isOk());
        createPending(5);
    }

    @Test
    void rejectedRequestNoLongerBlocksANewRequest() throws Exception {
        Long first = createPending(20);
        mockMvc.perform(rejectCall(first, "Supplier out of stock").with(manager())).andExpect(status().isOk());
        createPending(15);
    }

    @Test
    void concurrentCreatesForSameProductProduceOneOpenRequest() throws Exception {
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                authenticate(OFFICER, "ROLE_INVENTORY_STAFF");
                CreateRestockRequest body = new CreateRestockRequest();
                body.setProductId(product.getId());
                body.setRequestedQuantity(10);
                barrier.await();
                try {
                    inventoryService.createRestockRequest(body);
                    created.incrementAndGet();
                } catch (BusinessException e) {
                    assertEquals(HttpStatus.CONFLICT, e.getStatus());
                    duplicates.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, created.get());
        assertEquals(threads - 1, duplicates.get());
    }

    // ---------------------------------------------------------------- approval / rejection

    @Test
    void inventoryOfficerGets403OnApproveAndReject() throws Exception {
        Long id = createPending(20);
        mockMvc.perform(approveCall(id).with(officer())).andExpect(status().isForbidden());
        mockMvc.perform(rejectCall(id, "no").with(officer())).andExpect(status().isForbidden());
        // Even an invalid body is refused on role first, never reaching validation.
        mockMvc.perform(rejectCall(id, null).with(officer())).andExpect(status().isForbidden());
        assertEquals(RestockRequestStatus.PENDING, reload(id).getStatus());
    }

    @Test
    void serviceLayerAuthorizationDoesNotRelyOnUrlRules() throws Exception {
        Long id = createPending(20);
        authenticate(OFFICER, "ROLE_INVENTORY_STAFF");
        assertThrows(AccessDeniedException.class, () -> inventoryService.approveRestockRequest(id, null));
        assertThrows(AccessDeniedException.class, () -> inventoryService.rejectRestockRequest(id, "no"));
        authenticate(MANAGER, "ROLE_MANAGER");
        assertThrows(AccessDeniedException.class, () -> inventoryService.receiveStock(id, 1));
        CreateRestockRequest body = new CreateRestockRequest();
        body.setProductId(otherProduct.getId());
        body.setRequestedQuantity(5);
        assertThrows(AccessDeniedException.class, () -> inventoryService.createRestockRequest(body));
        SecurityContextHolder.clearContext();
        assertEquals(RestockRequestStatus.PENDING, reload(id).getStatus());
    }

    @Test
    void managerCanApprovePendingRequestWithoutChangingInventory() throws Exception {
        Long id = createPending(30);
        mockMvc.perform(approveCall(id).with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedByEmail").value(MANAGER))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.approvedQuantity").value(30));

        assertEquals(10, stockOf(product), "approval must not modify inventory");
        assertEquals(0, receiptMovements(id));
    }

    @Test
    void managerCanApproveAReducedQuantityWithinBounds() throws Exception {
        Long id = createPending(30);
        mockMvc.perform(approveCall(id, 31).with(manager())).andExpect(status().isBadRequest());
        mockMvc.perform(approveCall(id, 0).with(manager())).andExpect(status().isBadRequest());
        assertEquals(RestockRequestStatus.PENDING, reload(id).getStatus());

        mockMvc.perform(approveCall(id, 12).with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedQuantity").value(12))
                .andExpect(jsonPath("$.requestedQuantity").value(30));
    }

    @Test
    void managerCanRejectPendingRequestWithReason() throws Exception {
        Long id = createPending(30);
        mockMvc.perform(rejectCall(id, null).with(manager())).andExpect(status().isBadRequest());
        mockMvc.perform(rejectCall(id, "   ").with(manager())).andExpect(status().isBadRequest());

        mockMvc.perform(rejectCall(id, "Budget exhausted").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectedByEmail").value(MANAGER))
                .andExpect(jsonPath("$.rejectedAt").isNotEmpty())
                .andExpect(jsonPath("$.rejectionReason").value("Budget exhausted"))
                .andExpect(jsonPath("$.approvedByEmail").doesNotExist());
        assertEquals(10, stockOf(product));
    }

    @Test
    void creatorCannotApproveOrRejectOwnRequestEvenWithManagerRole() throws Exception {
        Long id = createAs(dualRole(), product.getId(), 25);
        mockMvc.perform(approveCall(id).with(dualRole()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SELF_APPROVAL_FORBIDDEN"));
        mockMvc.perform(rejectCall(id, "changed my mind").with(dualRole()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SELF_APPROVAL_FORBIDDEN"));
        assertEquals(RestockRequestStatus.PENDING, reload(id).getStatus());

        // A different manager can review it, and the dual-role user may then receive it as an officer.
        mockMvc.perform(approveCall(id).with(manager())).andExpect(status().isOk());
        mockMvc.perform(receiveCall(id, 25).with(dualRole())).andExpect(status().isOk());
    }

    @Test
    void approvingUnknownRequestReturns404() throws Exception {
        mockMvc.perform(approveCall(987654321L).with(manager())).andExpect(status().isNotFound());
        mockMvc.perform(rejectCall(987654321L, "x").with(manager())).andExpect(status().isNotFound());
        mockMvc.perform(receiveCall(987654321L, 1).with(officer())).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- invalid transitions

    @Test
    void invalidStatusTransitionsReturn409() throws Exception {
        Long approved = createApproved(20);
        mockMvc.perform(approveCall(approved).with(manager()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        mockMvc.perform(rejectCall(approved, "late").with(manager()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        mockMvc.perform(receiveCall(approved, 20).with(officer())).andExpect(status().isOk());
        mockMvc.perform(approveCall(approved).with(manager())).andExpect(status().isConflict());
        mockMvc.perform(rejectCall(approved, "late").with(manager())).andExpect(status().isConflict());

        Long rejected = createAs(officer(), otherProduct.getId(), 10);
        mockMvc.perform(rejectCall(rejected, "No budget").with(manager())).andExpect(status().isOk());
        mockMvc.perform(approveCall(rejected).with(manager()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        mockMvc.perform(rejectCall(rejected, "again").with(manager())).andExpect(status().isConflict());

        RestockRequest r = reload(rejected);
        assertEquals(RestockRequestStatus.REJECTED, r.getStatus());
        assertEquals("No budget", r.getRejectionReason());
    }

    // ---------------------------------------------------------------- receiving

    @Test
    void managerCannotReceiveStock() throws Exception {
        Long id = createApproved(20);
        mockMvc.perform(receiveCall(id, 20).with(manager())).andExpect(status().isForbidden());
        assertEquals(RestockRequestStatus.APPROVED, reload(id).getStatus());
        assertEquals(10, stockOf(product));
    }

    @Test
    void stockCannotBeReceivedBeforeApproval() throws Exception {
        Long id = createPending(20);
        mockMvc.perform(receiveCall(id, 20).with(officer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        assertEquals(10, stockOf(product));
        assertEquals(0, receiptMovements(id));
    }

    @Test
    void rejectedRequestCannotBeReceived() throws Exception {
        Long id = createPending(20);
        mockMvc.perform(rejectCall(id, "Not needed").with(manager())).andExpect(status().isOk());
        mockMvc.perform(receiveCall(id, 20).with(officer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
        assertEquals(10, stockOf(product));
        assertEquals(0, receiptMovements(id));
    }

    @Test
    void receivingApprovedStockUpdatesInventoryExactlyOnce() throws Exception {
        Long id = createApproved(25);
        mockMvc.perform(receiveCall(id, 25).with(officer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.receivedQuantity").value(25))
                .andExpect(jsonPath("$.receivedByEmail").value(OFFICER))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty())
                .andExpect(jsonPath("$.currentStock").value(35));

        assertEquals(35, stockOf(product));
        List<StockMovementHistory> movements = movementRepository
                .findByProductIdOrderByCreatedAtDesc(product.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(1, movements.size());
        StockMovementHistory m = movements.get(0);
        assertEquals(StockMovementType.RECEIVED_STOCK, m.getMovementType());
        assertEquals(25, m.getQuantityDelta());
        assertEquals("RESTOCK_REQ_" + id, m.getReferenceId());
        assertEquals(OFFICER, m.getActorEmail());
        assertNotNull(m.getActorId());
        assertNotNull(m.getCreatedAt());
    }

    @Test
    void duplicateReceiptDoesNotIncreaseStockTwice() throws Exception {
        Long id = createApproved(25);
        mockMvc.perform(receiveCall(id, 25).with(officer())).andExpect(status().isOk());
        mockMvc.perform(receiveCall(id, 25).with(officer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_RECEIVED"));

        assertEquals(35, stockOf(product));
        assertEquals(1, receiptMovements(id));
    }

    @Test
    void receivedQuantityIsValidated() throws Exception {
        Long id = createPending(50);
        mockMvc.perform(approveCall(id, 40).with(manager())).andExpect(status().isOk());

        mockMvc.perform(receiveCall(id, 0).with(officer())).andExpect(status().isBadRequest());
        mockMvc.perform(receiveCall(id, -1).with(officer())).andExpect(status().isBadRequest());
        mockMvc.perform(receiveCall(id, 41).with(officer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_QUANTITY"));

        // 10 in stock, maximum 100: lowering the maximum makes a 40-unit receipt overflow it.
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        inventory.setMaximumLevel(45);
        inventoryRepository.save(inventory);
        mockMvc.perform(receiveCall(id, 40).with(officer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EXCEEDS_MAX_LEVEL"));

        assertEquals(RestockRequestStatus.APPROVED, reload(id).getStatus());
        assertEquals(10, stockOf(product));
        assertEquals(0, receiptMovements(id));

        // A partial receipt within both limits succeeds.
        mockMvc.perform(receiveCall(id, 35).with(officer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedQuantity").value(35));
        assertEquals(45, stockOf(product));
    }

    @Test
    void concurrentReceiptsUpdateInventoryAndHistoryOnlyOnce() throws Exception {
        Long id = createApproved(30);
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                authenticate(OFFICER, "ROLE_INVENTORY_STAFF");
                barrier.await();
                try {
                    inventoryService.receiveStock(id, 30);
                    successes.incrementAndGet();
                } catch (BusinessException e) {
                    assertEquals(HttpStatus.CONFLICT, e.getStatus());
                    conflicts.incrementAndGet();
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, successes.get());
        assertEquals(threads - 1, conflicts.get());
        assertEquals(40, stockOf(product));
        assertEquals(1, receiptMovements(id));
        assertEquals(RestockRequestStatus.RECEIVED, reload(id).getStatus());
    }

    // ---------------------------------------------------------------- listing and unauthorised access

    @Test
    void managerSeesPendingQueueWithStockPosition() throws Exception {
        Long pending = createPending(30);
        Long approved = createAs(officer(), otherProduct.getId(), 10);
        mockMvc.perform(approveCall(approved).with(manager())).andExpect(status().isOk());

        String body = mockMvc.perform(get(BASE).param("status", "PENDING").param("size", "200").with(manager()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode rows = objectMapper.readTree(body).get("content");
        JsonNode mine = null;
        for (JsonNode row : rows) {
            assertEquals("PENDING", row.get("status").asText());
            if (row.get("id").asLong() == pending) mine = row;
            assertNotEquals(approved.longValue(), row.get("id").asLong());
        }
        assertNotNull(mine, "pending request should be listed");
        assertEquals("RSTK-001", mine.get("productSku").asText());
        assertEquals(10, mine.get("currentStock").asInt());
        assertEquals(5, mine.get("minimumLevel").asInt());
        assertEquals(100, mine.get("maximumLevel").asInt());
        assertEquals(OFFICER, mine.get("requestedByEmail").asText());
        assertFalse(mine.get("requestedAt").isNull());

        mockMvc.perform(get(BASE).with(officer())).andExpect(status().isOk());
    }

    @Test
    void unauthorizedUsersCannotAccessRestockOperations() throws Exception {
        Long id = createApproved(20);

        // Anonymous: 401 everywhere.
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(createCall(product.getId(), 5)).andExpect(status().isUnauthorized());
        mockMvc.perform(approveCall(id)).andExpect(status().isUnauthorized());
        mockMvc.perform(rejectCall(id, "x")).andExpect(status().isUnauthorized());
        mockMvc.perform(receiveCall(id, 20)).andExpect(status().isUnauthorized());

        // Authenticated but unrelated role: 403 everywhere.
        RequestPostProcessor cashier = as(CASHIER, "CASHIER");
        mockMvc.perform(get(BASE).with(cashier)).andExpect(status().isForbidden());
        mockMvc.perform(createCall(product.getId(), 5).with(cashier)).andExpect(status().isForbidden());
        mockMvc.perform(approveCall(id).with(cashier)).andExpect(status().isForbidden());
        mockMvc.perform(rejectCall(id, "x").with(cashier)).andExpect(status().isForbidden());
        mockMvc.perform(receiveCall(id, 20).with(cashier)).andExpect(status().isForbidden());

        // Managers cannot create requests (that is the officer's duty).
        mockMvc.perform(createCall(otherProduct.getId(), 5).with(manager())).andExpect(status().isForbidden());

        assertEquals(RestockRequestStatus.APPROVED, reload(id).getStatus());
        assertEquals(10, stockOf(product));
    }

    private static void authenticate(String email, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(authority))));
    }
}
