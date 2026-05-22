package vip.mate.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Flyway auto-repair configuration.
 * <p>
 * Replaces the default {@link FlywayMigrationInitializer} with one that
 * calls {@code flyway.repair()} before {@code flyway.migrate()}.
 * This handles failed migrations and checksum mismatches transparently
 * during version upgrades — especially important for Desktop app users
 * who cannot manually run CLI commands.
 *
 * @author MateClaw Team
 */
@Slf4j
@Configuration
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationInitializer flywayInitializer(Flyway flyway, DataSource dataSource) {
        return new FlywayMigrationInitializer(flyway, f -> {
            prepareLegacyCronSchema(dataSource);
            log.info("[Flyway] Running repair before migrate (auto-fix failed/changed migrations)...");
            f.repair();
            f.migrate();
        });
    }

    private void prepareLegacyCronSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "mate_cron_job")) {
                return;
            }
            String databaseProduct = connection.getMetaData().getDatabaseProductName().toLowerCase();
            if (!columnExists(connection, "mate_cron_job", "workspace_id")) {
                execute(connection, databaseProduct.contains("mysql")
                        ? "ALTER TABLE mate_cron_job ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1"
                        : "ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS workspace_id BIGINT NOT NULL DEFAULT 1");
            }
            if (!columnExists(connection, "mate_cron_job", "working_directory")) {
                execute(connection, databaseProduct.contains("mysql")
                        ? "ALTER TABLE mate_cron_job ADD COLUMN working_directory VARCHAR(1024) NULL"
                        : "ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS working_directory VARCHAR(1024)");
            }
            if (!indexExists(connection, "mate_cron_job", "idx_cron_job_workspace")) {
                execute(connection, "CREATE INDEX idx_cron_job_workspace ON mate_cron_job(workspace_id, deleted)");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to prepare legacy cron schema before Flyway migrate", e);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return exists(metaData.getTables(connection.getCatalog(), null, tableName, null))
                || exists(metaData.getTables(connection.getCatalog(), null, tableName.toUpperCase(), null))
                || exists(metaData.getTables(connection.getCatalog(), null, tableName.toLowerCase(), null));
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return exists(metaData.getColumns(connection.getCatalog(), null, tableName, columnName))
                || exists(metaData.getColumns(connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase()))
                || exists(metaData.getColumns(connection.getCatalog(), null, tableName.toLowerCase(), columnName.toLowerCase()));
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
            while (rs.next()) {
                String existingIndex = rs.getString("INDEX_NAME");
                if (existingIndex != null && existingIndex.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        try (ResultSet rs = metaData.getIndexInfo(connection.getCatalog(), null, tableName.toUpperCase(), false, false)) {
            while (rs.next()) {
                String existingIndex = rs.getString("INDEX_NAME");
                if (existingIndex != null && existingIndex.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean exists(ResultSet rs) throws SQLException {
        try (rs) {
            return rs.next();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        log.info("[Flyway] Legacy cron schema preflight: {}", sql);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
