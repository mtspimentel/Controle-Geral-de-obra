package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Migration {

    int version();

    String description();

    void apply(Connection connection) throws SQLException;

    default boolean requiresForeignKeysOff() {
        return false;
    }
}
