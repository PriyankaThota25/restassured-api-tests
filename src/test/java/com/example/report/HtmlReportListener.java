package com.example.report;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects JUnit results and writes a single self-contained HTML file to
 * target/custom-report/index.html when the run ends. Picked up automatically through
 * META-INF/services, so no test code needs to change.
 */
public class HtmlReportListener implements TestExecutionListener {

    private static final int MAX_BODY_CHARS = 3000;

    private record Result(String className, String name, String status, long millis,
                          String error, List<String> tags, List<ApiCallRecorder.Call> calls) {
    }

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private final List<Result> results = new CopyOnWriteArrayList<>();
    private long runStart;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        runStart = System.currentTimeMillis();
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (id.isTest()) {
            startTimes.put(id.getUniqueId(), System.currentTimeMillis());
            ApiCallRecorder.startTest(id.getUniqueId());
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (id.isTest()) {
            results.add(new Result(className(id), id.getDisplayName(), "SKIPPED", 0, reason, tags(id), List.of()));
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (!id.isTest()) {
            return;
        }
        ApiCallRecorder.endTest();
        long millis = System.currentTimeMillis() - startTimes.getOrDefault(id.getUniqueId(), System.currentTimeMillis());
        String status = switch (result.getStatus()) {
            case SUCCESSFUL -> "PASSED";
            case FAILED -> "FAILED";
            case ABORTED -> "SKIPPED";
        };
        String error = result.getThrowable().map(HtmlReportListener::stackTrace).orElse("");
        results.add(new Result(className(id), id.getDisplayName(), status, millis, error, tags(id),
                ApiCallRecorder.callsFor(id.getUniqueId())));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            Path out = Path.of("target", "custom-report", "index.html");
            Files.createDirectories(out.getParent());
            Files.writeString(out, render(System.currentTimeMillis() - runStart));
            System.out.println("Custom HTML report: " + out.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Could not write HTML report: " + e);
        }
    }

    private String render(long totalMillis) {
        long passed = results.stream().filter(r -> r.status().equals("PASSED")).count();
        long failed = results.stream().filter(r -> r.status().equals("FAILED")).count();
        long skipped = results.stream().filter(r -> r.status().equals("SKIPPED")).count();

        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>API Test Report</title><style>").append(CSS).append("</style></head><body>");
        h.append("<header><h1>API Test Report</h1><p>")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")))
                .append(" &middot; ").append(totalMillis).append(" ms</p></header><main>");
        h.append("<section class=\"cards\">")
                .append(card("Total", results.size(), "total"))
                .append(card("Passed", passed, "passed"))
                .append(card("Failed", failed, "failed"))
                .append(card("Skipped", skipped, "skipped"))
                .append("</section>");

        Map<String, List<Result>> byClass = new java.util.TreeMap<>();
        results.forEach(r -> byClass.computeIfAbsent(r.className(), k -> new java.util.ArrayList<>()).add(r));
        byClass.forEach((cls, list) -> {
            h.append("<h2>").append(esc(cls)).append("</h2>");
            list.stream().sorted(java.util.Comparator.comparing(Result::name)).forEach(r -> h.append(row(r)));
        });
        return h.append("</main></body></html>").toString();
    }

    private static String card(String label, long value, String cls) {
        return "<div class=\"card " + cls + "\"><b>" + value + "</b><span>" + label + "</span></div>";
    }

    private static String row(Result r) {
        StringBuilder h = new StringBuilder();
        h.append("<details class=\"test ").append(r.status().toLowerCase()).append("\"")
                .append(r.status().equals("FAILED") ? " open" : "").append("><summary>")
                .append("<span class=\"badge\">").append(r.status()).append("</span>")
                .append("<span class=\"name\">").append(esc(r.name())).append("</span>");
        r.tags().forEach(t -> h.append("<span class=\"tag\">").append(esc(t)).append("</span>"));
        h.append("<span class=\"time\">").append(r.millis()).append(" ms</span></summary>");
        if (!r.error().isEmpty()) {
            h.append("<pre class=\"error\">").append(esc(r.error())).append("</pre>");
        }
        for (ApiCallRecorder.Call c : r.calls()) {
            h.append("<div class=\"call\"><div class=\"req\"><b>").append(esc(c.method())).append("</b> ")
                    .append(esc(c.url())).append(" <span class=\"http s").append(c.status() / 100).append("xx\">")
                    .append(c.status()).append("</span> <span class=\"time\">").append(c.millis()).append(" ms</span></div>");
            if (!c.requestBody().isBlank()) {
                h.append("<div class=\"label\">Request body</div><pre>").append(esc(cut(c.requestBody()))).append("</pre>");
            }
            h.append("<div class=\"label\">Response body</div><pre>").append(esc(cut(c.responseBody()))).append("</pre></div>");
        }
        if (r.calls().isEmpty() && r.error().isEmpty()) {
            h.append("<p class=\"none\">No HTTP calls recorded for this test.</p>");
        }
        return h.append("</details>").toString();
    }

    private static List<String> tags(TestIdentifier id) {
        return id.getTags().stream().map(t -> t.getName()).sorted().toList();
    }

    private static String className(TestIdentifier id) {
        return id.getSource()
                .filter(MethodSource.class::isInstance)
                .map(s -> ((MethodSource) s).getClassName())
                .orElse("Unknown");
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String cut(String s) {
        return s.length() > MAX_BODY_CHARS ? s.substring(0, MAX_BODY_CHARS) + "\n... (truncated)" : s;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CSS = """
            :root{--bg:#f6f7f9;--fg:#1c2330;--muted:#6b7280;--card:#fff;--line:#e3e6eb;--pass:#1a7f4b;--fail:#c62828;--skip:#8a6d00}
            @media(prefers-color-scheme:dark){:root{--bg:#14171c;--fg:#e6e9ef;--muted:#9aa3b2;--card:#1d222a;--line:#2d3440;--pass:#4cc38a;--fail:#ff7b72;--skip:#e3b341}}
            *{box-sizing:border-box}body{margin:0;font:15px/1.5 system-ui,sans-serif;background:var(--bg);color:var(--fg)}
            header{padding:24px 16px 0;max-width:1000px;margin:0 auto}h1{margin:0;font-size:24px}header p{margin:4px 0 0;color:var(--muted)}
            main{max-width:1000px;margin:0 auto;padding:16px}h2{font-size:15px;margin:28px 0 8px;color:var(--muted);font-weight:600}
            .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:12px}
            .card{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:14px;display:flex;flex-direction:column}
            .card b{font-size:28px}.card span{color:var(--muted)}.card.passed b{color:var(--pass)}.card.failed b{color:var(--fail)}.card.skipped b{color:var(--skip)}
            .test{background:var(--card);border:1px solid var(--line);border-left-width:4px;border-radius:6px;margin:6px 0;padding:0 12px}
            .test.passed{border-left-color:var(--pass)}.test.failed{border-left-color:var(--fail)}.test.skipped{border-left-color:var(--skip)}
            summary{cursor:pointer;display:flex;gap:12px;align-items:center;padding:10px 0}.name{flex:1;word-break:break-word}
            .badge{font-size:11px;font-weight:700;letter-spacing:.04em;min-width:64px}.passed .badge{color:var(--pass)}.failed .badge{color:var(--fail)}.skipped .badge{color:var(--skip)}
            .tag{font-size:11px;padding:1px 8px;border-radius:10px;border:1px solid var(--line);color:var(--muted)}
            .time{color:var(--muted);font-size:13px;white-space:nowrap}
            .call{border-top:1px solid var(--line);padding:10px 0}.req{word-break:break-all}.label{font-size:12px;color:var(--muted);margin-top:8px}
            .http{font-weight:700;padding:0 6px;border-radius:4px;border:1px solid var(--line)}.http.s2xx{color:var(--pass)}.http.s4xx,.http.s5xx{color:var(--fail)}
            pre{background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:10px;overflow:auto;max-height:260px;font-size:12.5px;margin:4px 0}
            pre.error{border-color:var(--fail)}.none{color:var(--muted);margin:0 0 10px}
            """;
}
