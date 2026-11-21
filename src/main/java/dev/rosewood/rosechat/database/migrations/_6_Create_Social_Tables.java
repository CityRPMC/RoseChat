package dev.rosewood.rosechat.database.migrations;

import dev.rosewood.rosegarden.database.DataMigration;
import dev.rosewood.rosegarden.database.DatabaseConnector;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class _6_Create_Social_Tables extends DataMigration {

    public _6_Create_Social_Tables() {
        super(6);
    }

    @Override
    public void migrate(DatabaseConnector connector, Connection connection, String tablePrefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_data_block (" +
                    "blocking_uuid VARCHAR(36) NOT NULL, " +
                    "blocked_uuid VARCHAR(36) NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "UNIQUE (blocking_uuid, blocked_uuid)" +
                    ")");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_data_friend (" +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "friend_uuid VARCHAR(36) NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "UNIQUE (player_uuid, friend_uuid)" +
                    ")");
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tablePrefix + "player_data ADD COLUMN dm_privacy VARCHAR(16) NOT NULL DEFAULT 'EVERYONE'");
        } catch (SQLException ignored) {
            // Column already exists
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tablePrefix + "player_data ADD COLUMN is_dnd BOOLEAN NOT NULL DEFAULT FALSE");
        } catch (SQLException ignored) {
            // Column already exists
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tablePrefix + "player_data ADD COLUMN status VARCHAR(128)");
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

}
