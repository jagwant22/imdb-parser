package uk.ac.cam.cl.databases.moviedb.internal;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static java.sql.Types.*;

/**
 * To start the command-line sql tool, create a file called <tt>relational-db/sqltool.rc</tt>
 * with the following contents:
 * <pre>urlid moviedb
 * url jdbc:hsqldb:file:relational-db/moviedb;shutdown=true
 * username SA
 * password</pre>
 *
 * Then run the tool as follows:
 * <pre>java -cp target/moviedb-exercises-0.0.1-document-jar.jar \
 *   org.hsqldb.cmdline.SqlTool --rcFile=relational-db/sqltool.rc --autoCommit moviedb</pre>
 *
 * A limitation of this tool is that "arrow-up" history and readline editing is
 * not available. This makes experimentation a bit painful...<p>
 *
 * Alternatively, use the Swing GUI:
 * <pre>java -cp target/moviedb-exercises-0.0.1-document-jar.jar \
 *   org.hsqldb.util.DatabaseManagerSwing --url jdbc:hsqldb:file:relational-db/moviedb;shutdown=true</pre>
 *
 * Note: create tables using "CREATE CACHED TABLE" DDL, as the default creates an in-memory
 * table whose contents are recorded in a SQL script file.<p>
 */
public class ImportRelational {
    private final Connection hsql, pg;
    private final String moviesTable, peopleTable;

    public ImportRelational(Connection hsql, Connection pg, boolean onlyTopTitles) {
        this.hsql = hsql;
        this.pg = pg;
        if (onlyTopTitles) {
            this.moviesTable = "movies_doc_small";
            this.peopleTable = "people_doc_small";
        } else {
            this.moviesTable = "movies_doc";
            this.peopleTable = "people_doc";
        }
    }

    public void run() throws SQLException {
        copyMovies();
        copyPeople();
        copyCredits();
        copyCertificates();
        copyColorInfo();
        copyGenres();
        copyKeywords();
        copyLanguages();
        copyLocations();
        copyReleaseDates();
        copyRunningTimes();

        System.out.println("Compacting the database...");
        execHSQL("SHUTDOWN COMPACT");
    }

    private void copyMovies() throws SQLException {
        System.out.println("Copying movies...");
        execHSQL("CREATE CACHED TABLE movies (" +
            "id integer PRIMARY KEY, " +
            "title varchar(255) NOT NULL, " +
            "year integer)");
        execHSQL("CREATE INDEX movies_title ON movies (title)");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO movies (id, title, year) values(?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id', properties->>'title', properties->>'year' FROM " + moviesTable);
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, INTEGER);
            }
        }
    }

    private void copyPeople() throws SQLException {
        System.out.println("Copying people...");
        execHSQL("CREATE CACHED TABLE people (" +
            "id integer PRIMARY KEY, " +
            "name varchar(255) NOT NULL, " +
            "gender varchar(10))");
        execHSQL("CREATE INDEX people_name ON people (name)");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO people (id, name, gender) values(?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id', properties->>'name', properties->>'gender' FROM " + peopleTable);
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, VARCHAR);
            }
        }
    }

    private void copyCredits() throws SQLException {
        System.out.println("Copying credits...");
        execHSQL("CREATE CACHED TABLE credits (" +
            "person_id integer NOT NULL REFERENCES people (id), " +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "type varchar(20) NOT NULL, " +
            "note varchar(255), " +
            "character varchar(255), " +
            "position integer, " +
            "line_order integer, " +
            "group_order integer, " +
            "subgroup_order integer, " +
            "UNIQUE (person_id, movie_id, type))");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO credits (person_id, movie_id, type, note, character, position, line_order, group_order, subgroup_order) values(?, ?, ?, ?, ?, ?, ?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT person_id, movie_id, type, " +
                "credits.properties->>'note', " +
                "credits.properties->>'character', " +
                "credits.properties->>'position', " +
                "credits.properties->>'line_order', " +
                "credits.properties->>'group_order', " +
                "credits.properties->>'subgroup_order' " +
                "FROM credits JOIN " + moviesTable + " ON (" + moviesTable + ".properties->>'id')::integer = movie_id " +
                "WHERE type <> 'miscellaneous'");
            while (results.next()) {
                copyRow(results, insert, INTEGER, INTEGER, VARCHAR, VARCHAR, VARCHAR, INTEGER, INTEGER, INTEGER, INTEGER);
            }
        }
    }

    private void copyCertificates() throws SQLException {
        System.out.println("Copying certificates...");
        execHSQL("CREATE CACHED TABLE certificates (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "country varchar(20) NOT NULL, " +
            "certificate varchar(20) NOT NULL, " +
            "note varchar(255))");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO certificates (movie_id, country, certificate, note) values(?, ?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id', cert->>'country', cert->>'certificate', cert->>'note' " +
                "FROM " + moviesTable + ", jsonb_array_elements(properties->'certificates') AS cert");
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, VARCHAR, VARCHAR);
            }
        }
    }

    private void copyColorInfo() throws SQLException {
        System.out.println("Copying color_info...");
        execHSQL("CREATE CACHED TABLE color_info (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "value varchar(20) NOT NULL, " +
            "note varchar(255))");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO color_info (movie_id, value, note) values(?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id', color->>'color_info', color->>'note' " +
                "FROM " + moviesTable + ", jsonb_array_elements(properties->'color_info') AS color");
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, VARCHAR);
            }
        }
    }

    private void copyGenres() throws SQLException {
        System.out.println("Copying genres...");
        execHSQL("CREATE CACHED TABLE genres (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "genre varchar(25) NOT NULL)");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO genres (movie_id, genre) values(?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id' AS movie_id, " +
                "jsonb_array_elements_text(properties->'genres') FROM " + moviesTable);
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR);
            }
        }
    }

    private void copyKeywords() throws SQLException {
        System.out.println("Copying keywords...");
        execHSQL("CREATE CACHED TABLE keywords (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "keyword varchar(127) NOT NULL)");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO keywords (movie_id, keyword) values(?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id' AS movie_id, " +
                "jsonb_array_elements_text(properties->'keywords') FROM " + moviesTable);
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR);
            }
        }
    }

    private void copyLanguages() throws SQLException {
        System.out.println("Copying languages...");
        execHSQL("CREATE CACHED TABLE languages (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "language varchar(35) NOT NULL, " +
            "note varchar(255))");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO languages (movie_id, language, note) values(?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id', language->>'language', language->>'note' " +
                "FROM " + moviesTable + ", jsonb_array_elements(properties->'languages') AS language");
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, VARCHAR);
            }
        }
    }

    private void copyLocations() throws SQLException {
        System.out.println("Copying locations...");
        execHSQL("CREATE CACHED TABLE locations (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "location varchar(255) NOT NULL, " +
            "note varchar(511))");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO locations (movie_id, location, note) values(?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id', location->>'location', location->>'note' " +
                "FROM " + moviesTable + ", jsonb_array_elements(properties->'locations') AS location");
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, VARCHAR);
            }
        }
    }

    private void copyReleaseDates() throws SQLException {
        System.out.println("Copying release_dates...");
        execHSQL("CREATE CACHED TABLE release_dates (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "country varchar(40) NOT NULL, " +
            "release_date varchar(10) NOT NULL, " +
            "note varchar(255))");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO release_dates (movie_id, country, release_date, note) values(?, ?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT movie_id, country, date->>'release_date', date->>'note' FROM (" +
                "SELECT movie_id, country, jsonb_array_elements(dates) AS date FROM (" +
                "SELECT properties->>'id' AS movie_id, " +
                "by_country.key AS country, by_country.value AS dates " +
                "FROM " + moviesTable + ", jsonb_each(properties->'release_dates') AS by_country" +
                ") reldates) reldates2");
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, VARCHAR, VARCHAR);
            }
        }
    }

    private void copyRunningTimes() throws SQLException {
        System.out.println("Copying running_times...");
        execHSQL("CREATE CACHED TABLE running_times (" +
            "movie_id integer NOT NULL REFERENCES movies (id), " +
            "running_time varchar(40) NOT NULL, " +
            "note varchar(255))");

        try (PreparedStatement insert = hsql.prepareStatement("INSERT INTO running_times (movie_id, running_time, note) values(?, ?, ?)");
                Statement select = pg.createStatement()) {
            ResultSet results = select.executeQuery(
                "SELECT properties->>'id', rtime->>'running_time', rtime->>'note' " +
                "FROM " + moviesTable + ", jsonb_array_elements(properties->'running_times') AS rtime");
            while (results.next()) {
                copyRow(results, insert, INTEGER, VARCHAR, VARCHAR);
            }
        }
    }

    private void copyRow(ResultSet results, PreparedStatement insert, int... types) throws SQLException {
        int paramIndex = 0;
        for (int type : types) {
            paramIndex++;

            switch (type) {
                case INTEGER:
                    insert.setInt(paramIndex, results.getInt(paramIndex));
                    if (results.wasNull()) insert.setNull(paramIndex, type);
                    break;

                case VARCHAR:
                    insert.setString(paramIndex, results.getString(paramIndex));
                    if (results.wasNull()) insert.setNull(paramIndex, type);
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported SQL type: " + type);
            }
        }
        insert.execute();
    }

    private void execHSQL(String sql) throws SQLException {
        try (Statement statement = hsql.createStatement()) {
            statement.executeQuery(sql);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[0].equals("--large") || args[0].equals("--small"))) {
            System.err.println("Usage: ImportRelational [--large|--small] output-dir");
            System.exit(1);
        }
        File dbPath = new File(args[1], "moviedb");
        Class.forName("org.hsqldb.jdbc.JDBCDriver");
        Class.forName("org.postgresql.Driver");
        // add ;ifexists=true to prevent auto-creation
        try (Connection hsql = DriverManager.getConnection("jdbc:hsqldb:file:" + dbPath + ";shutdown=true");
                Connection pg = DriverManager.getConnection("jdbc:postgresql:")) {
            new ImportRelational(hsql, pg, args[0].equals("--small")).run();
        }
    }
}
