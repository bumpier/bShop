package net.bumpier.bshop.database;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for all database implementations.
 * Provides a contract for connecting, disconnecting, and accessing the database.
 */
public interface Database {

    /**
     * Establishes the connection to the database.
     * Implementations should handle all setup logic, including connection pooling if applicable.
     */
    void connect();

    /**
     * Gracefully disconnects from the database.
     * Should close all connections and release resources.
     */
    void disconnect();

    /**
     * Asynchronously retrieves a connection from the pool or source.
     *
     * @return A CompletableFuture that will complete with a SQL Connection.
     */
    CompletableFuture<Connection> getConnection();

    /**
     * Initializes the required database tables asynchronously.
     * This should be called after a successful connection.
     */
    void createTables();
}