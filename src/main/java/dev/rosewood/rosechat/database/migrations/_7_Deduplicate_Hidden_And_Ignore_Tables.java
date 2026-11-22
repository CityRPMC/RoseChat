package dev.rosewood.rosechat.database.migrations;

import dev.rosewood.rosegarden.database.DataMigration;
import dev.rosewood.rosegarden.database.DatabaseConnector;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class _7_Deduplicate_Hidden_And_Ignore_Tables extends DataMigration {

    public _7_Deduplicate_Hidden_And_Ignore_Tables() {
        super(7);
    }

    @Override
    public void migrate(DatabaseConnector connector, Connection connection, String tablePrefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + tablePrefix + "hidden_channels WHERE rowid NOT IN (" +
                    "SELECT MIN(rowid) FROM " + tablePrefix + "hidden_channels GROUP BY uuid, channel)");
            statement.executeUpdate("DELETE FROM " + tablePrefix + "player_data_ignore WHERE rowid NOT IN (" +
                    "SELECT MIN(rowid) FROM " + tablePrefix + "player_data_ignore GROUP BY ignoring_uuid, ignored_uuid)");
        } catch (SQLException ignored) {
            // Non-SQLite backends without rowid
        }
    }

}
