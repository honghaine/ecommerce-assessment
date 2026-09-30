package com.flashsale.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.flashsale.catalog.entity.Product;
import com.flashsale.catalog.repository.ProductRepository;
import com.flashsale.flashsale.entity.RuleStatus;
import com.flashsale.flashsale.entity.SellerFlashSaleRule;
import com.flashsale.flashsale.repository.SellerFlashSaleRuleRepository;
import com.flashsale.flashsale.service.SlotGenerationService;
import com.flashsale.flashsale.support.DaysOfWeek;
import com.flashsale.inventory.repository.InventoryRepository;
import com.flashsale.inventory.service.InventoryService;
import com.flashsale.region.service.RegionService;
import com.flashsale.user.config.WalletProperties;
import com.flashsale.user.entity.User;
import com.flashsale.user.entity.UserRole;
import com.flashsale.user.entity.Wallet;
import com.flashsale.user.repository.UserRepository;
import com.flashsale.user.repository.WalletRepository;

/**
 * Idempotent demo data: accounts, products + stock, and seller rules that put 3 products into every
 * hourly window of every day. Slots and items are then produced by the regular generator from the
 * region's DB config — the seeder never creates slots itself. Guarded by ShedLock + unique constraints.
 */
@Slf4j
@Component
public class DemoDataSeeder {

    private static final int DEMO_STOCK = 10_000;
    private static final int LOW_STOCK = 2_000;
    private static final int[] QUOTAS = {5, 20, 100};
    private static final String[] DISCOUNTS = {"0.50", "0.60", "0.70"};

    private record DemoProduct(String code, String name, long price) {
    }

    private static final List<DemoProduct> PRODUCTS = List.of(
            // Phones
            new DemoProduct("PHONE-001", "Smartphone X", 10_000_000),
            new DemoProduct("PHONE-002", "Smartphone X Pro", 15_000_000),
            new DemoProduct("PHONE-003", "Smartphone X Max", 20_000_000),
            new DemoProduct("PHONE-004", "Smartphone Lite", 5_000_000),
            new DemoProduct("PHONE-005", "Foldable Phone Z", 35_000_000),
            new DemoProduct("PHONE-006", "Flip Phone Mini", 22_000_000),
            new DemoProduct("PHONE-007", "Budget Phone A1", 2_500_000),
            new DemoProduct("PHONE-008", "Gaming Phone G5", 18_000_000),
            new DemoProduct("PHONE-009", "Rugged Phone R2", 7_500_000),
            new DemoProduct("PHONE-010", "Camera Phone C3", 16_500_000),

            // Laptops
            new DemoProduct("LAPTOP-001", "Ultrabook 14", 25_000_000),
            new DemoProduct("LAPTOP-002", "Ultrabook 16", 32_000_000),
            new DemoProduct("LAPTOP-003", "Gaming Laptop 15", 38_000_000),
            new DemoProduct("LAPTOP-004", "Gaming Laptop 17", 48_000_000),
            new DemoProduct("LAPTOP-005", "Student Laptop 13", 12_000_000),
            new DemoProduct("LAPTOP-006", "Business Laptop 14", 28_000_000),
            new DemoProduct("LAPTOP-007", "2-in-1 Convertible", 22_000_000),
            new DemoProduct("LAPTOP-008", "Creator Laptop 16", 55_000_000),
            new DemoProduct("LAPTOP-009", "Chromebook 11", 6_500_000),
            new DemoProduct("LAPTOP-010", "Workstation Laptop 17", 65_000_000),

            // Earbuds & Headphones
            new DemoProduct("EARBUD-001", "Wireless Earbuds", 2_000_000),
            new DemoProduct("EARBUD-002", "Wireless Earbuds Pro", 4_500_000),
            new DemoProduct("EARBUD-003", "Sport Earbuds", 1_800_000),
            new DemoProduct("EARBUD-004", "Noise-Cancelling Earbuds", 5_500_000),
            new DemoProduct("EARBUD-005", "Budget Earbuds", 600_000),
            new DemoProduct("EARBUD-006", "Over-Ear Headphones", 6_000_000),
            new DemoProduct("EARBUD-007", "On-Ear Headphones", 2_800_000),
            new DemoProduct("EARBUD-008", "Gaming Headset", 3_200_000),
            new DemoProduct("EARBUD-009", "Bone Conduction Headphones", 3_500_000),
            new DemoProduct("EARBUD-010", "Studio Monitor Headphones", 4_000_000),

            // Watches & Wearables
            new DemoProduct("WATCH-001", "Smart Watch", 5_000_000),
            new DemoProduct("WATCH-002", "Smart Watch Pro", 9_000_000),
            new DemoProduct("WATCH-003", "Smart Watch Ultra", 18_000_000),
            new DemoProduct("WATCH-004", "Fitness Band", 1_200_000),
            new DemoProduct("WATCH-005", "Kids Smart Watch", 1_500_000),
            new DemoProduct("WATCH-006", "GPS Running Watch", 7_500_000),
            new DemoProduct("WATCH-007", "Hybrid Smart Watch", 4_200_000),
            new DemoProduct("WATCH-008", "Dive Computer Watch", 12_000_000),
            new DemoProduct("WATCH-009", "Smart Ring", 8_000_000),
            new DemoProduct("WATCH-010", "Health Monitor Band", 2_300_000),

            // Speakers
            new DemoProduct("SPEAKER-001", "Bluetooth Speaker", 1_500_000),
            new DemoProduct("SPEAKER-002", "Portable Speaker Mini", 800_000),
            new DemoProduct("SPEAKER-003", "Waterproof Speaker", 2_200_000),
            new DemoProduct("SPEAKER-004", "Party Speaker", 7_000_000),
            new DemoProduct("SPEAKER-005", "Smart Speaker", 2_500_000),
            new DemoProduct("SPEAKER-006", "Smart Display Speaker", 4_000_000),
            new DemoProduct("SPEAKER-007", "Soundbar 2.1", 6_500_000),
            new DemoProduct("SPEAKER-008", "Soundbar 5.1", 14_000_000),
            new DemoProduct("SPEAKER-009", "Bookshelf Speakers", 5_000_000),
            new DemoProduct("SPEAKER-010", "Subwoofer 10-inch", 4_800_000),

            // Cameras
            new DemoProduct("CAMERA-001", "Action Camera", 8_000_000),
            new DemoProduct("CAMERA-002", "Action Camera 360", 11_000_000),
            new DemoProduct("CAMERA-003", "Mirrorless Camera", 28_000_000),
            new DemoProduct("CAMERA-004", "Full-Frame Mirrorless", 52_000_000),
            new DemoProduct("CAMERA-005", "Compact Camera", 9_500_000),
            new DemoProduct("CAMERA-006", "Instant Camera", 2_000_000),
            new DemoProduct("CAMERA-007", "Vlogging Camera", 15_000_000),
            new DemoProduct("CAMERA-008", "Camera Drone", 24_000_000),
            new DemoProduct("CAMERA-009", "Dash Cam", 1_900_000),
            new DemoProduct("CAMERA-010", "Home Security Camera", 1_100_000),

            // Tablets
            new DemoProduct("TABLET-001", "Tablet 10", 7_000_000),
            new DemoProduct("TABLET-002", "Tablet 11 Pro", 20_000_000),
            new DemoProduct("TABLET-003", "Tablet 13 Pro", 30_000_000),
            new DemoProduct("TABLET-004", "Tablet Mini 8", 11_000_000),
            new DemoProduct("TABLET-005", "Kids Tablet", 2_500_000),
            new DemoProduct("TABLET-006", "E-Reader", 3_500_000),
            new DemoProduct("TABLET-007", "E-Reader Color", 5_200_000),
            new DemoProduct("TABLET-008", "Drawing Tablet", 4_500_000),
            new DemoProduct("TABLET-009", "Pen Display 16", 13_000_000),
            new DemoProduct("TABLET-010", "Rugged Tablet", 9_000_000),

            // Monitors
            new DemoProduct("MONITOR-001", "Monitor 24 FHD", 3_000_000),
            new DemoProduct("MONITOR-002", "Monitor 27 QHD", 6_500_000),
            new DemoProduct("MONITOR-003", "Monitor 27 4K", 9_500_000),
            new DemoProduct("MONITOR-004", "Monitor 32 4K", 13_000_000),
            new DemoProduct("MONITOR-005", "Ultrawide 34", 12_000_000),
            new DemoProduct("MONITOR-006", "Gaming Monitor 240Hz", 11_500_000),
            new DemoProduct("MONITOR-007", "OLED Monitor 27", 21_000_000),
            new DemoProduct("MONITOR-008", "Portable Monitor 15", 4_200_000),
            new DemoProduct("MONITOR-009", "Curved Monitor 49", 28_000_000),
            new DemoProduct("MONITOR-010", "Professional Monitor 5K", 35_000_000),

            // Keyboards
            new DemoProduct("KEYBOARD-001", "Mechanical Keyboard", 2_200_000),
            new DemoProduct("KEYBOARD-002", "Wireless Keyboard", 900_000),
            new DemoProduct("KEYBOARD-003", "Low-Profile Keyboard", 1_800_000),
            new DemoProduct("KEYBOARD-004", "Gaming Keyboard RGB", 2_900_000),
            new DemoProduct("KEYBOARD-005", "75% Custom Keyboard", 4_500_000),
            new DemoProduct("KEYBOARD-006", "Ergonomic Split Keyboard", 5_500_000),
            new DemoProduct("KEYBOARD-007", "Tablet Keyboard Case", 3_200_000),
            new DemoProduct("KEYBOARD-008", "Numeric Keypad", 450_000),
            new DemoProduct("KEYBOARD-009", "Compact 60% Keyboard", 1_600_000),
            new DemoProduct("KEYBOARD-010", "Keyboard & Mouse Combo", 750_000),

            // Mice & Accessories
            new DemoProduct("MOUSE-001", "Wireless Mouse", 500_000),
            new DemoProduct("MOUSE-002", "Gaming Mouse", 1_400_000),
            new DemoProduct("MOUSE-003", "Ergonomic Vertical Mouse", 1_100_000),
            new DemoProduct("MOUSE-004", "Productivity Mouse Pro", 2_500_000),
            new DemoProduct("MOUSE-005", "Travel Mouse", 650_000),
            new DemoProduct("MOUSE-006", "Trackball Mouse", 1_700_000),
            new DemoProduct("MOUSE-007", "Trackpad", 3_000_000),
            new DemoProduct("MOUSE-008", "Stylus Pen", 2_700_000),
            new DemoProduct("MOUSE-009", "USB-C Hub 7-in-1", 1_200_000),
            new DemoProduct("MOUSE-010", "Wireless Charging Pad", 700_000));


    private final SeedProperties properties;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final InventoryService inventoryService;
    private final SellerFlashSaleRuleRepository rules;
    private final SlotGenerationService generator;
    private final RegionService regionService;
    private final PasswordEncoder passwordEncoder;
    private final WalletProperties walletProperties;
    private final TransactionTemplate transactionTemplate;

    public DemoDataSeeder(SeedProperties properties, UserRepository users, WalletRepository wallets,
                          ProductRepository products, InventoryRepository inventories,
                          InventoryService inventoryService, SellerFlashSaleRuleRepository rules,
                          SlotGenerationService generator, RegionService regionService,
                          PasswordEncoder passwordEncoder, WalletProperties walletProperties,
                          TransactionTemplate transactionTemplate) {
        this.properties = properties;
        this.users = users;
        this.wallets = wallets;
        this.products = products;
        this.inventories = inventories;
        this.inventoryService = inventoryService;
        this.rules = rules;
        this.generator = generator;
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
            generator.generate(region);
        }
    }

    private void seedRegion(String region, String passwordHash) {
        String r = region.toLowerCase(Locale.ROOT);
        account(region, "admin." + r + "@flashsale.dev", passwordHash, UserRole.PLATFORM_ADMIN);
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

        // 3 products in every hourly window, every day (matches the default 60-minute config).
        int createdRules = 0;
        for (int hour = 0; hour < 24; hour++) {
            LocalTime start = LocalTime.of(hour, 0);
            for (int k = 0; k < QUOTAS.length; k++) {
                Product product = catalog.get((hour * QUOTAS.length + k) % catalog.size());
                if (rules.existsByProductIdAndSlotStartTimeAndStatusNot(product.getId(), start, RuleStatus.ARCHIVED)) {
                    continue;
                }
                BigDecimal salePrice = product.getPrice().multiply(new BigDecimal(DISCOUNTS[k]))
                        .setScale(2, RoundingMode.HALF_UP);
                rules.save(SellerFlashSaleRule.create(region, seller.getId(), product.getId(), start,
                        DaysOfWeek.EVERY_DAY, salePrice, QUOTAS[k]));
                createdRules++;
            }
        }
        log.info("Demo seed [{}]: {} new buyers, {} new rules", region, createdBuyers, createdRules);
    }

    private User account(String region, String email, String passwordHash, UserRole role) {
        return users.findByEmail(email).orElseGet(() -> {
            User user = users.save(User.activeWithRole(region, email, passwordHash, role));
            wallets.save(Wallet.open(user, walletProperties.initialBalance()));
            return user;
        });
    }

    /** Creates the demo product once; tops its stock up when flash-sale reservations ran it low. */
    private Product product(String region, User seller, DemoProduct demo) {
        String sku = region + "-" + demo.code();
        Product product = products.findByRegionAndSku(region, sku).orElseGet(() -> {
            Product created = products.saveAndFlush(Product.create(region, seller.getId(), sku, demo.name(),
                    "Demo product", BigDecimal.valueOf(demo.price()).setScale(2, RoundingMode.UNNECESSARY)));
            inventoryService.open(created.getId(), region, DEMO_STOCK);
            return created;
        });
        inventories.findById(product.getId())
                .filter(stock -> stock.getAvailable() < LOW_STOCK)
                .ifPresent(stock -> inventoryService.restock(product.getId(), region, DEMO_STOCK));
        return product;
    }
}
