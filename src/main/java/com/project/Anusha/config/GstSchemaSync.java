package com.project.Anusha.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Component
public class GstSchemaSync implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GstSchemaSync.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public GstSchemaSync(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        if (!isMySqlDatabase()) {
            return;
        }

        addColumnIfMissing("products", "hsn_code", "VARCHAR(20) NULL");
        addColumnIfMissing("products", "gst_rate", "DECIMAL(5,2) DEFAULT 0.00");

        addColumnIfMissing("orders", "taxable_amount", "DECIMAL(10,2) DEFAULT 0.00");
        addColumnIfMissing("orders", "cgst_amount", "DECIMAL(10,2) DEFAULT 0.00");
        addColumnIfMissing("orders", "sgst_amount", "DECIMAL(10,2) DEFAULT 0.00");
        addColumnIfMissing("orders", "igst_amount", "DECIMAL(10,2) DEFAULT 0.00");

        addColumnIfMissing("order_items", "hsn_code", "VARCHAR(20) NULL");
        addColumnIfMissing("order_items", "gst_rate", "DECIMAL(5,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "taxable_amount", "DECIMAL(10,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "cgst_rate", "DECIMAL(5,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "sgst_rate", "DECIMAL(5,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "igst_rate", "DECIMAL(5,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "cgst_amount", "DECIMAL(10,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "sgst_amount", "DECIMAL(10,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "igst_amount", "DECIMAL(10,2) DEFAULT 0.00");
        addColumnIfMissing("order_items", "total_tax_amount", "DECIMAL(10,2) DEFAULT 0.00");
    }

    private boolean isMySqlDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("mysql");
        } catch (Exception e) {
            log.warn("Unable to inspect database type for GST schema sync: {}", e.getMessage());
            return false;
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        try (Connection connection = dataSource.getConnection()) {
            if (columnExists(connection, tableName, columnName)) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            log.info("Added missing GST column {}.{}", tableName, columnName);
        } catch (Exception e) {
            log.warn("Could not add GST column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        String catalog = connection.getCatalog();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
