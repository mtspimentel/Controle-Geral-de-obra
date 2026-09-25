package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface TransactionWork<T> {

    T execute(Connection connection) throws SQLException;
}
