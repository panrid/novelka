package space.panrid.novelka.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Generates jOOQ classes from the real schema: starts a throwaway PostgreSQL,
 * applies the Flyway migrations and reads the result. Keeps generated code
 * identical to what production gets from the same migrations.
 *
 * <p>Arguments: migrations directory, output directory, target package.
 */
public final class JooqGenerator {

    static final String POSTGRES_IMAGE = "postgres:17";

    private JooqGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected: <migrations dir> <output dir> <package>");
        }
        Path migrations = Path.of(args[0]);
        Path output = Path.of(args[1]);
        String targetPackage = args[2];

        try (PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE)) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("filesystem:" + migrations.toAbsolutePath())
                    .load()
                    .migrate();

            deleteRecursively(output);
            GenerationTool.generate(new Configuration()
                    .withJdbc(new Jdbc()
                            .withDriver("org.postgresql.Driver")
                            .withUrl(postgres.getJdbcUrl())
                            .withUser(postgres.getUsername())
                            .withPassword(postgres.getPassword()))
                    .withGenerator(new Generator()
                            .withDatabase(new Database()
                                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                                    .withInputSchema("public")
                                    .withOutputSchemaToDefault(true)
                                    .withExcludes("flyway_schema_history"))
                            .withGenerate(new Generate()
                                    .withRecords(true)
                                    .withPojos(false)
                                    .withJavaTimeTypes(true)
                                    .withGeneratedAnnotation(false))
                            .withTarget(new Target()
                                    .withPackageName(targetPackage)
                                    .withDirectory(output.toAbsolutePath().toString()))));
        }
    }

    private static void deleteRecursively(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
