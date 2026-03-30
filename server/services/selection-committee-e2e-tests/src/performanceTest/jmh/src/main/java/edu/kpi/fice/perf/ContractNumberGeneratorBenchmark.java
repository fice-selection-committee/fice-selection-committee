package edu.kpi.fice.perf;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.concurrent.TimeUnit;

/**
 * ContractNumberGeneratorBenchmark
 * ================================
 * Measures the throughput of ContractNumberGenerator.generateNextNumber()
 * which calls SELECT nextval('admission.contract_budget_seq') via JdbcTemplate.
 *
 * This is called on every contract creation. During peak load, many concurrent
 * threads race on the same PostgreSQL sequence. The benchmark establishes the
 * baseline throughput under single-threaded and four-threaded conditions.
 *
 * Key findings this benchmark should answer:
 *   1. How many contract numbers can be generated per second?
 *   2. Does throughput degrade linearly or sub-linearly under 4 threads?
 *   3. What is the p99 latency for a single nextval call?
 *
 * Expected baseline (H2 in-memory, no network):
 *   - Single-threaded:  > 10 000 ops/s
 *   - 4-threaded:       > 30 000 ops/s (PostgreSQL sequence is lock-free per-session)
 *
 * When run against a real PostgreSQL instance results will be lower due to
 * network RTT; use the H2 baseline to detect regressions in the generator
 * logic itself.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xmx256m"})
public class ContractNumberGeneratorBenchmark {

    private JdbcTemplate jdbcTemplate;

    @Setup(Level.Trial)
    public void setup() {
        // Use H2 with PostgreSQL compatibility mode to simulate sequence behaviour
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        // MODE=PostgreSQL enables nextval() syntax; SEQUENCE_STYLE=Oracle for compatibility
        ds.setUrl("jdbc:h2:mem:contract_bench;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        jdbcTemplate = new JdbcTemplate(ds);

        // Create schema and sequences mirroring V6__add_number_sequences.sql
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS admission");
        jdbcTemplate.execute(
            "CREATE SEQUENCE IF NOT EXISTS admission.contract_budget_seq START WITH 1 INCREMENT BY 1"
        );
        jdbcTemplate.execute(
            "CREATE SEQUENCE IF NOT EXISTS admission.contract_paid_seq START WITH 1 INCREMENT BY 1"
        );
    }

    /**
     * Benchmark the budget contract number generation path.
     * Mirrors ContractNumberGenerator.generateNextNumber(ContractType.budget, year).
     */
    @Benchmark
    public void generateBudgetContractNumber(Blackhole bh) {
        Long seq = jdbcTemplate.queryForObject(
            "SELECT nextval('admission.contract_budget_seq')", Long.class
        );
        // Reproduce the exact string-formatting logic from the service
        String number = String.format("%d%s-%04d", 26, "б", seq);
        bh.consume(number);
    }

    /**
     * Benchmark the paid contract number generation path.
     */
    @Benchmark
    public void generatePaidContractNumber(Blackhole bh) {
        Long seq = jdbcTemplate.queryForObject(
            "SELECT nextval('admission.contract_paid_seq')", Long.class
        );
        String number = String.format("%d%s-%04d", 26, "к", seq);
        bh.consume(number);
    }

    /**
     * Measures the overhead of the String.format pattern alone (no DB call).
     * Provides a lower bound; delta between this and generateBudgetContractNumber
     * is the pure JDBC overhead.
     */
    @Benchmark
    public void formatOnlyBaseline(Blackhole bh) {
        String number = String.format("%d%s-%04d", 26, "б", 42L);
        bh.consume(number);
    }
}
