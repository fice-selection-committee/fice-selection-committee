package edu.kpi.fice.perf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PdfGenerationBenchmark
 * =======================
 * Measures the end-to-end time for:
 *   1. Thymeleaf template rendering → HTML string
 *   2. openhtmltopdf rendering       → PDF byte[]
 *
 * Context:
 *   PdfGeneratorService.generatePdf() is called for every enrollment contract
 *   and personal-file generation. During order signing (Scenario C) up to 50
 *   PDFs may be requested in a short burst.
 *
 * Benchmarks:
 *   - thymeleafRenderOnly  — measures template engine cost (no PDF)
 *   - pdfFromHtml          — measures openhtmltopdf cost given pre-rendered HTML
 *   - fullPipeline         — Thymeleaf → HTML → PDF (matches real service path)
 *
 * Expected baseline (JVM warmed up, typical contract template ~3 KB HTML):
 *   - thymeleafRenderOnly: < 2 ms per operation
 *   - pdfFromHtml:         < 150 ms per operation (CPU-bound font loading)
 *   - fullPipeline:        < 200 ms per operation
 *
 * If fullPipeline exceeds 200 ms, consider:
 *   - Async PDF generation off the HTTP thread
 *   - Pre-warming the Thymeleaf template cache
 *   - Caching font objects across requests
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xmx512m", "-XX:+UseG1GC"})
public class PdfGenerationBenchmark {

    private TemplateEngine templateEngine;
    private Map<String, Object> templateVariables;
    private String preRenderedHtml;

    // A realistic contract HTML template (simplified from the actual Thymeleaf template)
    private static final String CONTRACT_TEMPLATE = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="UTF-8"/><title>Contract</title>
        <style>
          body { font-family: Arial, sans-serif; margin: 40px; font-size: 12pt; }
          .header { text-align: center; font-size: 14pt; font-weight: bold; }
          .section { margin-top: 20px; }
          table { width: 100%; border-collapse: collapse; }
          td, th { border: 1px solid #000; padding: 5px; }
        </style>
        </head>
        <body>
          <div class="header">ДОГОВІР № [[${contractNumber}]]</div>
          <div class="section">
            <p>Студент: <b>[[${lastName}]] [[${firstName}]] [[${middleName}]]</b></p>
            <p>Спеціальність: [[${specialty}]]</p>
            <p>Форма навчання: [[${educationForm}]]</p>
            <p>Рік вступу: [[${year}]]</p>
          </div>
          <div class="section">
            <table>
              <tr><th>Показник</th><th>Значення</th></tr>
              <tr><td>Тип договору</td><td>[[${contractType}]]</td></tr>
              <tr><td>Дата реєстрації</td><td>[[${registrationDate}]]</td></tr>
              <tr><td>Факультет</td><td>[[${faculty}]]</td></tr>
            </table>
          </div>
          <div class="section" style="margin-top: 60px;">
            <p>Підпис студента: ___________________</p>
            <p>Підпис ректора: ___________________</p>
          </div>
        </body>
        </html>
        """;

    @Setup(Level.Trial)
    public void setup() {
        // Configure Thymeleaf with inline string mode (mirrors ThymeleafConfig in documents-service)
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true); // mirrors production cache setting

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        templateVariables = new HashMap<>();
        templateVariables.put("contractNumber", "26б-0042");
        templateVariables.put("lastName",        "Шевченко");
        templateVariables.put("firstName",       "Тарас");
        templateVariables.put("middleName",      "Григорович");
        templateVariables.put("specialty",       "Комп'ютерна інженерія");
        templateVariables.put("educationForm",   "Денна");
        templateVariables.put("year",            "2026");
        templateVariables.put("contractType",    "Бюджет");
        templateVariables.put("registrationDate","24.03.2026");
        templateVariables.put("faculty",         "ФІОТ");

        // Pre-render HTML for the pdfFromHtml benchmark
        Context ctx = new Context();
        ctx.setVariables(templateVariables);
        preRenderedHtml = templateEngine.process(CONTRACT_TEMPLATE, ctx);
    }

    /**
     * Measures Thymeleaf template processing time only.
     * No PDF rendering — isolates the template-engine cost.
     */
    @Benchmark
    public void thymeleafRenderOnly(Blackhole bh) {
        Context ctx = new Context();
        ctx.setVariables(templateVariables);
        String html = templateEngine.process(CONTRACT_TEMPLATE, ctx);
        bh.consume(html);
    }

    /**
     * Measures openhtmltopdf rendering given pre-rendered HTML.
     * Isolates the PDF engine cost from Thymeleaf.
     */
    @Benchmark
    public void pdfFromHtml(Blackhole bh) throws Exception {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(preRenderedHtml, "/");
            builder.toStream(os);
            builder.run();
            bh.consume(os.toByteArray());
        }
    }

    /**
     * Full pipeline: Thymeleaf render → openhtmltopdf PDF.
     * Mirrors PdfGeneratorService.generatePdf() exactly.
     */
    @Benchmark
    public void fullPipeline(Blackhole bh) throws Exception {
        Context ctx = new Context();
        ctx.setVariables(templateVariables);
        String html = templateEngine.process(CONTRACT_TEMPLATE, ctx);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, "/");
            builder.toStream(os);
            builder.run();
            bh.consume(os.toByteArray());
        }
    }
}
