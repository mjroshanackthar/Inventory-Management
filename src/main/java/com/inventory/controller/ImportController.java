package com.inventory.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.inventory.model.Product;
import com.inventory.model.User;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.UserRepository;
import com.inventory.security.UserDetailsImpl;

import org.springframework.core.io.ClassPathResource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;

@RestController
@RequestMapping("/api/products/import")
public class ImportController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    

    // ✅ Import products from Excel (supports add and deduct modes)
    @PostMapping("/excel")
    public ResponseEntity<?> importFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "add") String mode) {
        Long userId = getCurrentUserId();
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Product> productsToSave = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            // Default column mappings
            int nameColIndex = 0;
            int priceColIndex = 1;
            int qtyColIndex = 2;
            int startRow = 0;

            if (lastRowNum >= 0) {
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                    boolean foundHeaders = false;
                    for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                        Cell cell = headerRow.getCell(i);
                        if (cell != null) {
                            String headerValue = getCellValueAsString(cell).trim().toLowerCase();
                            if (headerValue.equals("name") || headerValue.equals("product") || headerValue.equals("item") || headerValue.equals("title") || headerValue.contains("product name") || headerValue.contains("product_name") || headerValue.contains("item name") || headerValue.contains("item_name")) {
                                nameColIndex = i;
                                foundHeaders = true;
                            } else if (headerValue.equals("price") || headerValue.equals("rate") || headerValue.equals("cost") || headerValue.equals("mrp") || headerValue.contains("unit price") || headerValue.contains("unit_price")) {
                                priceColIndex = i;
                                foundHeaders = true;
                            } else if (headerValue.equals("quantity") || headerValue.equals("qty") || headerValue.equals("stock") || headerValue.equals("count") || headerValue.contains("quantity on hand") || headerValue.contains("qty on hand")) {
                                qtyColIndex = i;
                                foundHeaders = true;
                            }
                        }
                    }
                    if (foundHeaders) {
                        startRow = 1; // Skip header row
                    }
                }
            }

            // Load existing products from database
            List<Product> existingProducts = productRepository.findAllByUser_Id(userId);
            Map<String, Product> dbProductMap = new HashMap<>();
            for (Product p : existingProducts) {
                dbProductMap.put(p.getName().trim().toLowerCase(), p);
            }

            Map<String, Product> productMap = new HashMap<>();
            for (int r = startRow; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                Cell nameCell = row.getCell(nameColIndex);
                Cell priceCell = row.getCell(priceColIndex);
                Cell qtyCell = row.getCell(qtyColIndex);

                if (nameCell == null || nameCell.getCellType() == CellType.BLANK) {
                    continue; // Skip blank rows
                }

                String name = getCellValueAsString(nameCell);
                double price = getCellValueAsDouble(priceCell);
                int quantity = getCellValueAsInt(qtyCell);

                String key = name.trim().toLowerCase();
                Product existing = productMap.get(key);
                if (existing != null) {
                    // Aggregate quantity for duplicate product names in the excel sheet
                    if ("deduct".equalsIgnoreCase(mode)) {
                        existing.setQuantity(Math.max(0, existing.getQuantity() - quantity));
                    } else {
                        existing.setQuantity(existing.getQuantity() + quantity);
                    }
                } else {
                    // Check if it exists in the database
                    Product dbProduct = dbProductMap.get(key);
                    if (dbProduct != null) {
                        if ("deduct".equalsIgnoreCase(mode)) {
                            dbProduct.setQuantity(Math.max(0, dbProduct.getQuantity() - quantity));
                        } else {
                            dbProduct.setQuantity(dbProduct.getQuantity() + quantity);
                        }
                        dbProduct.setPrice(price); // update to latest price
                        productMap.put(key, dbProduct);
                    } else {
                        Product product = new Product();
                        product.setName(name.trim());
                        product.setPrice(price);
                        // If it's a new product:
                        // - in "deduct" mode, it has 0 stock
                        // - in "add" mode, it has the imported quantity as initial stock
                        product.setQuantity("deduct".equalsIgnoreCase(mode) ? 0 : quantity);
                        product.setUser(owner);
                        productMap.put(key, product);
                    }
                }
            }
            productsToSave.addAll(productMap.values());

            if (productsToSave.isEmpty()) {
                return ResponseEntity.badRequest().body("No valid product data found in the spreadsheet.");
            }

            List<Product> savedProducts = productRepository.saveAll(productsToSave);
            return ResponseEntity.ok(savedProducts);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to read the Excel file: " + e.getMessage());
        }
    }

    // ✅ Bulk save products (used after preview/scanning approval, supports add and deduct modes)
    @PostMapping("/bulk")
    public ResponseEntity<?> importBulk(
            @RequestBody List<Product> products,
            @RequestParam(value = "mode", defaultValue = "add") String mode) {
        Long userId = getCurrentUserId();
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Load existing products from database
        List<Product> existingProducts = productRepository.findAllByUser_Id(userId);
        Map<String, Product> dbProductMap = new HashMap<>();
        for (Product p : existingProducts) {
            dbProductMap.put(p.getName().trim().toLowerCase(), p);
        }

        List<Product> productsToSave = new ArrayList<>();
        Map<String, Product> newProductMap = new HashMap<>();

        for (Product product : products) {
            if (product.getName() == null || product.getName().isBlank()) {
                continue;
            }
            String key = product.getName().trim().toLowerCase();

            // First check if we already processed it in this bulk list
            Product existingInBulk = newProductMap.get(key);
            if (existingInBulk != null) {
                if ("deduct".equalsIgnoreCase(mode)) {
                    existingInBulk.setQuantity(Math.max(0, existingInBulk.getQuantity() - product.getQuantity()));
                } else {
                    existingInBulk.setQuantity(existingInBulk.getQuantity() + product.getQuantity());
                }
            } else {
                // Check if it exists in the database
                Product dbProduct = dbProductMap.get(key);
                if (dbProduct != null) {
                    if ("deduct".equalsIgnoreCase(mode)) {
                        dbProduct.setQuantity(Math.max(0, dbProduct.getQuantity() - product.getQuantity()));
                    } else {
                        dbProduct.setQuantity(dbProduct.getQuantity() + product.getQuantity());
                    }
                    dbProduct.setPrice(product.getPrice()); // update to latest price
                    newProductMap.put(key, dbProduct);
                } else {
                    product.setUser(owner);
                    product.setName(product.getName().trim());
                    // If it's a new product:
                    // - in "deduct" mode, it has 0 stock
                    // - in "add" mode, it has the imported quantity as initial stock
                    product.setQuantity("deduct".equalsIgnoreCase(mode) ? 0 : product.getQuantity());
                    newProductMap.put(key, product);
                }
            }
        }

        productsToSave.addAll(newProductMap.values());
        List<Product> saved = productRepository.saveAll(productsToSave);
        return ResponseEntity.ok(saved);
    }

    private Product createMockProduct(String name, double price, int qty) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setQuantity(qty);
        return p;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return "";
    }

    private double getCellValueAsDouble(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return 0.0;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Double.parseDouble(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private int getCellValueAsInt(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return 0;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                String val = cell.getStringCellValue().trim();
                return (int) Math.round(Double.parseDouble(val));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }



    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof UserDetailsImpl u) {
            return u.getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
}
