package com.project.Anusha.config;

import com.project.Anusha.model.DeliveryOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class DeliveryOrderSchemaSync implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DeliveryOrderSchemaSync.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DeliveryOrderSchemaSync(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        if (!isMySqlDatabase()) {
            return;
        }

        syncEnumColumn(
                "delivery_orders",
                "status",
                enumDefinition(DeliveryOrder.OrderStatus.values()));

        syncEnumColumn(
                "delivery_orders",
                "payment_type",
                enumDefinition(DeliveryOrder.PaymentType.values()));
    }

    private boolean isMySqlDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("mysql");
        } catch (Exception e) {
            log.warn("Unable to inspect database type for delivery order schema sync: {}", e.getMessage());
            return false;
        }
    }

    private void syncEnumColumn(String tableName, String columnName, String enumSql) {
        String sql = "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " " + enumSql + " NOT NULL";
        try {
            jdbcTemplate.execute(sql);
            log.info("Synchronized enum column {}.{}", tableName, columnName);
        } catch (Exception e) {
            log.warn("Could not synchronize enum column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    private String enumDefinition(Enum<?>[] values) {
        String joinedValues = Arrays.stream(values)
                .map(Enum::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(","));
        return "ENUM(" + joinedValues + ")";
    }
}
