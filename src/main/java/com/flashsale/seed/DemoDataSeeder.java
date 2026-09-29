package com.flashsale.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.catalog.entity.Product;
import com.flashsale.catalog.repository.ProductRepository;
import com.flashsale.flashsale.entity.FlashSaleItem;
import com.flashsale.flashsale.entity.FlashSaleSession;
import com.flashsale.flashsale.entity.SessionType;
import com.flashsale.flashsale.repository.FlashSaleItemRepository;
import com.flashsale.flashsale.repository.FlashSaleSessionRepository;
import com.flashsale.inventory.service.InventoryService;
import com.flashsale.region.service.RegionService;
import com.flashsale.user.config.WalletProperties;
import com.flashsale.user.entity.User;
import com.flashsale.user.entity.UserRole;
import com.flashsale.user.entity.Wallet;
import com.flashsale.user.repository.UserRepository;
import com.flashsale.user.repository.WalletRepository;

/**
 * Idempotent demo data: accounts, products + stock, and flash-sale slots for today and tomorrow
 * (6 × 4h slots per region-local day, 3 items each). All flash-sale configuration is plain DB rows —
 * nothing here is read at runtime. Guarded by ShedLock and by unique constraints, so it is safe to
 * run on several instances and repeatedly.
 */
@Slf4j
@Component
public class DemoDataSeeder {

    private static final int SLOT_HOURS = 4;
    private static final int INITIAL_STOCK = 1_000;
    private static final int[] QUOTAS = {5, 20, 100};
    private static final String[] DISCOUNTS = {"0.50", "0.60", "0.70"};

    private record DemoProduct(String code, String name, long price) {
    }

    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct("PHONE-001", "Smartphone X", 10_000_000),
            new DemoProduct("LAPTOP-001", "Ultrabook 14", 25_000_000),
            new DemoProduct("EARBUD-001", "Wireless Earbuds", 2_000_000),
            new DemoProduct("WATCH-001", "Smart Watch", 5_000_000),
            new DemoProduct("SPEAKER-001", "Bluetooth Speaker", 1_500_000),
            new DemoProduct("CAMERA-001", "Action Camera", 8_000_000));

    private final SeedProperties properties;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final ProductRepository products;
    private final InventoryService inventoryService;
    private final FlashSaleSessionRepository sessions;
    private final FlashSaleItemRepository items;
    private final RegionService regionService;
    private final PasswordEncoder passwordEncoder;
    private final WalletProperties walletProperties;
    private final TransactionTemplate transactionTemplate;

    public DemoDataSeeder(SeedProperties properties, UserRepository users, WalletRepository wallets,
                          ProductRepository products, InventoryService inventoryService,
                          FlashSaleSessionRepository sessions, FlashSaleItemRepository items,
                          RegionService regionService, PasswordEncoder passwordEncoder,
                          WalletProperties walletProperties, TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.users = users;
        this.wallets = wallets;
        this.products = products;
        this.inventoryService = inventoryService;
        this.sessions = sessions;
        this.items = items;
        this.regionService = regionService;
        this.passwordEncoder = passwordEncoder;
        this.walletProperties = walletProperties;
        this.transactionTemplate = transactionTemplate;
    }

    @SchedulerLock(name = "demoDataSeeder", lockAtMostFor = "PT5M")
    public void seed() {
        String passwordHash = passwordEncoder.encode(properties.password());
        for (String rawRegion : properties.regions()) {
            String region = regionService.requireSupported(rawRegion);
            transactionTemplate.executeWithoutResult(status -> seedRegion(region, passwordHash));
        }
    }

    private void seedRegion(String region, String passwordHash) {
        String r = region.toLowerCase(Locale.ROOT);
        User admin = account(region, "admin." + r + "@flashsale.dev", passwordHash, UserRole.PLATFORM_ADMIN);
        User seller = account(region, "seller." + r + "@flashsale.dev", passwordHash, UserRole.SELLER);
        int createdBuyers = 0;
        for (int i = 1; i <= properties.buyers(); i++) {
            String email = String.format("buyer%04d%s@demo.flashsale.dev", i, "VN".equals(region) ? "" : "." + r);
            if (!users.existsByEmail(email)) {
                User buyer = users.save(User.activeWithRole(region, email, passwordHash, UserRole.USER));
                wallets.save(Wallet.open(buyer, properties.buyerBalance()));
                createdBuyers++;
            }
        }
        List<Product> catalog = PRODUCTS.stream().map(demo -> product(region, seller, demo)).toList();

        ZoneId zone = regionService.timezone(region);
        LocalDate today = LocalDate.now(zone);
        int createdSlots = 0;
        for (LocalDate day : List.of(today, today.plusDays(1))) {
            for (int slot = 0; slot < 24 / SLOT_HOURS; slot++) {
                if (createSlot(region, zone, day, slot, admin, seller, catalog)) {
                    createdSlots++;
                }
            }
        }
        log.info("Demo seed [{}]: {} new buyers, {} new slots", region, createdBuyers, createdSlots);
    }

    private User account(String region, String email, String passwordHash, UserRole role) {
        return users.findByEmail(email).orElseGet(() -> {
            User user = users.save(User.activeWithRole(region, email, passwordHash, role));
            wallets.save(Wallet.open(user, walletProperties.initialBalance()));
            return user;
        });
    }

    private Product product(String region, User seller, DemoProduct demo) {
        String sku = region + "-" + demo.code();
        return products.findByRegionAndSku(region, sku).orElseGet(() -> {
            Product product = products.saveAndFlush(Product.create(region, seller.getId(), sku, demo.name(),
                    "Demo product", BigDecimal.valueOf(demo.price()).setScale(2, RoundingMode.UNNECESSARY)));
            inventoryService.open(product.getId(), region, INITIAL_STOCK);
            return product;
        });
    }

    private boolean createSlot(String region, ZoneId zone, LocalDate day, int slot, User admin, User seller,
                               List<Product> catalog) {
        ZonedDateTime start = day.atStartOfDay(zone).plusHours((long) slot * SLOT_HOURS);
        Instant startAt = start.toInstant();
        if (sessions.existsByRegionAndTypeAndStartAt(region, SessionType.PLATFORM, startAt)) {
            return false;
        }
        String name = String.format("%02d:00 – %02d:00", start.getHour(), (start.getHour() + SLOT_HOURS) % 24);
        FlashSaleSession session = sessions.saveAndFlush(FlashSaleSession.platformSlot(region, admin.getId(), name,
                day, startAt, start.plusHours(SLOT_HOURS).toInstant()));

        for (int k = 0; k < QUOTAS.length; k++) {
            Product product = catalog.get((slot * QUOTAS.length + k) % catalog.size());
            int quota = QUOTAS[k];
            if (!inventoryService.reserve(product.getId(), region, quota)) {
                inventoryService.restock(product.getId(), region, INITIAL_STOCK);
                inventoryService.reserve(product.getId(), region, quota);
            }
            BigDecimal salePrice = product.getPrice().multiply(new BigDecimal(DISCOUNTS[k]))
                    .setScale(2, RoundingMode.HALF_UP);
            items.save(FlashSaleItem.approved(session, product.getId(), seller.getId(), salePrice, quota));
        }
        return true;
    }
}
