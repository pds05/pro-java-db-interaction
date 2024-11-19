package ru.flamexander.db.interaction.ex05_repositories;

import ru.flamexander.db.interaction.exceptions.ApplicationInitializationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DbMigrator {
    private static final String RESOURCE_QUERIES_FILE = "init.sql";
    public static final String MIGRATION_TABLE_NAME = "migration_history";
    public static final String CREATE_MIGRATION_TABLE_QUERY =
            "CREATE TABLE IF NOT EXISTS migration_history (" +
                    "            id BIGSERIAL PRIMARY KEY AUTO_INCREMENT," +
                    "            created_date DATETIME," +
                    "            query VARCHAR(255)," +
                    "            status VARCHAR(255)," +
                    "            cause VARCHAR(255)" +
                    "            )";
    public static final String INSERT_TO_MIGRATION_TABLE_QUERY = "INSERT INTO migration_history (created_date, query, status, cause) VALUES (?, ?, ?, ?);";
    public static final String SELECT_FROM_MIGRATION_TABLE_QUERY = "SELECT * FROM migration_history WHERE query = ?;";

    private DataSource dataSource;

    public DbMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate() {
        List<String> queries = readQueryFromResource();
        Statement statement = dataSource.getStatement();
        try {
            statement.executeUpdate(CREATE_MIGRATION_TABLE_QUERY);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ApplicationInitializationException("Не удалось выполнить запрос query=" + CREATE_MIGRATION_TABLE_QUERY);
        }
        queries.forEach(query -> {
            if (query.trim().toUpperCase().startsWith("CREATE TABLE")) {
                if (checkDuplicate(query)) {
                    try {
                        statement.executeUpdate(query);
                        saveQueryResult(query, "OK", null);
                        System.out.println("Запрос выполнен - ОК, query=" + query);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        saveQueryResult(query, "FAIL", e.getMessage());
                        System.out.println("Запрос не выполнен - NОК, query=" + query);
                    }
                } else {
                    System.out.println("Запрос уже выполнялся, query=" + query);
                }
            } else {
                System.out.println("Запрос не выполнен - NOK, query=" + query + "\r\nЗапрос должен начинаться с 'CREATE TABLE'");
            }
        });
        printQueryResults(statement);
    }

    private boolean checkDuplicate(String query) {
        try (PreparedStatement ps = dataSource.getConnection().prepareStatement(SELECT_FROM_MIGRATION_TABLE_QUERY)) {
            ps.setString(1, query);
            ResultSet rs = ps.executeQuery();
            return !rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> readQueryFromResource() {
        List<String> queries = new ArrayList<>();
        Optional<URL> resource = Optional.ofNullable(DbMigrator.class.getClassLoader().getResource(RESOURCE_QUERIES_FILE));
        resource.ifPresent(resourceUrl -> {
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = Files.newBufferedReader(Path.of(resourceUrl.toURI()))) {
                reader.lines().forEach(line -> {
                    builder.append(line);
                    builder.append(System.lineSeparator());
                });
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
                throw new ApplicationInitializationException("Не удалось прочитать файл init.sql");
            }
            int startIndex = 0;
            int endIndex = 0;
            String query;
            do {
                endIndex = builder.indexOf(";", startIndex);
                if (endIndex == -1) {
                    query = builder.substring(startIndex);
                } else {
                    query = builder.substring(startIndex, endIndex);
                }
                query = query.replaceAll("\\r|\\n", "");
                if (!query.isEmpty()) {
                    queries.add(query.trim());
                }
                startIndex = endIndex + 1;
            } while (endIndex != -1);
            System.out.println("Запросы загружены из файла \\resources\\" + RESOURCE_QUERIES_FILE + ": " + queries);
        });
        return queries;
    }

    private void saveQueryResult(String query, String result, String cause) {
        Connection conn = dataSource.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(INSERT_TO_MIGRATION_TABLE_QUERY)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, query);
            ps.setString(3, result);
            ps.setString(4, cause);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ApplicationInitializationException("Не удалось добавить данные в таблицу " + MIGRATION_TABLE_NAME);
        }
    }

    private void printQueryResults(Statement statement) {
        try (ResultSet rs = statement.executeQuery("SELECT * FROM migration_history")) {
            System.out.println("Migration history:");
            while (rs.next()) {
                int id = rs.getInt("id");
                String query = rs.getString("query");
                String status = rs.getString("status");
                String createdDate = rs.getString("created_date");
                String cause = rs.getString("cause");
                System.out.println("id=" + id + " createdDate=" + createdDate + " status=" + status + " cause=" + cause + " query=" + query);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
