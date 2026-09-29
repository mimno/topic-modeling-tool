package cc.mallet.topics.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * Writes a small static website for browsing a trained model: an index of
 * topics, a page per topic (top words, related topics, top documents) and a
 * page per document (metadata, topic mix, excerpt). Open index.html in any
 * browser; nothing is loaded from the internet.
 */
public final class HtmlReport {

    public static final String DIR = "output_html";
    public static final String INDEX = "index.html";

    static final int DOCS_PER_TOPIC_PAGE = 100;
    static final int RELATED_TOPICS = 5;
    static final double MIN_DOC_TOPIC_SHARE = 0.01;

    private final Path dir;
    private final Corpus corpus;
    private final TopicModelResults results;
    private final TrainingOptions options;
    private final String title;

    private HtmlReport(Path dir, Corpus corpus, TopicModelResults results, TrainingOptions options) {
        this.dir = dir;
        this.corpus = corpus;
        this.results = results;
        this.options = options;
        this.title = options.inputDir.getFileName() + " — " + results.numTopics + " topics";
    }

    /** Write the report and return the path of its index page. */
    public static Path write(Path outputDir, Corpus corpus, TopicModelResults results,
                             TrainingOptions options) throws IOException {
        Path dir = outputDir.resolve(DIR);
        Files.createDirectories(dir.resolve("topics"));
        Files.createDirectories(dir.resolve("docs"));
        HtmlReport report = new HtmlReport(dir, corpus, results, options);
        report.writeStylesheet();
        report.writeIndex();
        report.writeDocumentIndex();
        for (int t = 0; t < results.numTopics; t++) {
            report.writeTopic(t);
        }
        for (Corpus.Document doc : corpus.getDocuments()) {
            report.writeDocument(doc);
        }
        return dir.resolve(INDEX);
    }

    private void writeStylesheet() throws IOException {
        try (InputStream css = HtmlReport.class.getResourceAsStream("/css/report.css")) {
            Files.copy(css, dir.resolve("style.css"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeIndex() throws IOException {
        StringBuilder html = new StringBuilder();
        header(html, "Topics", "", "topics");
        html.append("<p class=\"summary\">")
            .append(corpus.size()).append(" documents from <code>")
            .append(escape(options.inputDir.toString())).append("</code>, ")
            .append(results.numTopics).append(" topics, ")
            .append(options.numIterations).append(" iterations.</p>\n");
        html.append("<table class=\"topics\">\n<thead><tr><th>Topic</th><th>Share of corpus</th>")
            .append("<th>Top words</th></tr></thead>\n<tbody>\n");
        double maxShare = 0;
        for (int t = 0; t < results.numTopics; t++) {
            maxShare = Math.max(maxShare, results.topicShare(t));
        }
        for (int t = 0; t < results.numTopics; t++) {
            html.append("<tr><td><a href=\"topics/topic-").append(t).append(".html\">")
                .append(t).append("</a></td><td>")
                .append(bar(results.topicShare(t), maxShare, percent(results.topicShare(t))))
                .append("</td><td><a href=\"topics/topic-").append(t).append(".html\">")
                .append(escape(results.topicLabel(t, options.numTopWords)))
                .append("</a></td></tr>\n");
        }
        html.append("</tbody></table>\n");
        footer(html);
        Files.writeString(dir.resolve(INDEX), html, StandardCharsets.UTF_8);

        // Earlier versions called the index page all_topics.html.
        Files.writeString(dir.resolve("all_topics.html"),
                "<!doctype html><meta charset=\"utf-8\"><meta http-equiv=\"refresh\" content=\"0; url=index.html\">"
                + "<a href=\"index.html\">Topics</a>\n", StandardCharsets.UTF_8);
    }

    private void writeDocumentIndex() throws IOException {
        StringBuilder html = new StringBuilder();
        header(html, "Documents", "", "documents");
        html.append("<table class=\"docs\">\n<thead><tr><th>Document</th><th>Words</th>")
            .append("<th>Main topic</th></tr></thead>\n<tbody>\n");
        for (Corpus.Document doc : corpus.getDocuments()) {
            int top = results.topicsByProportion(doc.id()).get(0);
            html.append("<tr><td><a href=\"docs/doc-").append(doc.id()).append(".html\">")
                .append(escape(doc.name())).append("</a></td><td class=\"num\">")
                .append(results.docLengths[doc.id()]).append("</td><td>")
                .append(topicLink("topics/", top)).append("</td></tr>\n");
        }
        html.append("</tbody></table>\n");
        footer(html);
        Files.writeString(dir.resolve("documents.html"), html, StandardCharsets.UTF_8);
    }

    private void writeTopic(int topic) throws IOException {
        StringBuilder html = new StringBuilder();
        header(html, "Topic " + topic, "../", "topics");
        html.append("<p class=\"summary\">").append(percent(results.topicShare(topic)))
            .append(" of all words in the corpus.</p>\n");

        html.append("<h2>Top words</h2>\n<ol class=\"words\">\n");
        List<TopicModelResults.WordCount> words = results.topWords.get(topic);
        int maxCount = words.isEmpty() ? 1 : words.get(0).count();
        for (TopicModelResults.WordCount wc : words) {
            html.append("<li>").append(bar(wc.count(), maxCount, escape(wc.word())))
                .append("<span class=\"count\">").append(wc.count()).append("</span></li>\n");
        }
        html.append("</ol>\n");

        List<TopicModelResults.TopicLink> related = results.relatedTopics(topic, RELATED_TOPICS);
        if (!related.isEmpty()) {
            html.append("<h2>Often appears with</h2>\n<ul class=\"related\">\n");
            for (TopicModelResults.TopicLink link : related) {
                html.append("<li>").append(topicLink("", link.topic())).append("</li>\n");
            }
            html.append("</ul>\n");
        }

        html.append("<h2>Top documents</h2>\n<p class=\"help\">Ranked by the number of words ")
            .append("in the document assigned to this topic.</p>\n")
            .append("<table class=\"docs\">\n<thead><tr><th>#</th><th>Document</th><th>Words in topic</th>")
            .append("<th>Share of document</th></tr></thead>\n<tbody>\n");
        int rank = 1;
        for (TopicModelResults.DocumentScore score : results.topDocuments(topic, DOCS_PER_TOPIC_PAGE)) {
            Corpus.Document doc = corpus.getDocuments().get(score.doc());
            html.append("<tr><td class=\"num\">").append(rank++).append("</td><td><a href=\"../docs/doc-")
                .append(doc.id()).append(".html\">").append(escape(doc.name())).append("</a></td><td class=\"num\">")
                .append(score.count()).append("</td><td>")
                .append(bar(score.proportion(), 1.0, percent(score.proportion())))
                .append("</td></tr>\n");
        }
        html.append("</tbody></table>\n");
        footer(html);
        Files.writeString(dir.resolve("topics/topic-" + topic + ".html"), html, StandardCharsets.UTF_8);
    }

    private void writeDocument(Corpus.Document doc) throws IOException {
        StringBuilder html = new StringBuilder();
        header(html, doc.name(), "../", "documents");

        List<String> metaHeader = corpus.getMetadataHeader();
        if (!doc.metadata().isEmpty()) {
            html.append("<table class=\"metadata\">\n");
            for (int i = 0; i < doc.metadata().size(); i++) {
                String key = i < metaHeader.size() ? metaHeader.get(i) : "";
                html.append("<tr><th>").append(escape(key)).append("</th><td>")
                    .append(escape(doc.metadata().get(i))).append("</td></tr>\n");
            }
            html.append("</table>\n");
        }

        html.append("<h2>Topics in this document</h2>\n<table class=\"doc-topics\">\n<tbody>\n");
        double[] proportions = results.docTopicProportions[doc.id()];
        for (int t : results.topicsByProportion(doc.id())) {
            if (proportions[t] < MIN_DOC_TOPIC_SHARE) {
                break;
            }
            html.append("<tr><td>").append(bar(proportions[t], 1.0, percent(proportions[t])))
                .append("</td><td>").append(topicLink("../topics/", t)).append("</td></tr>\n");
        }
        html.append("</tbody></table>\n");

        html.append("<h2>Text</h2>\n<p class=\"help\">From <code>")
            .append(escape(doc.source().toString())).append("</code></p>\n<pre class=\"excerpt\">")
            .append(escape(doc.excerpt()));
        if (doc.excerpt().length() >= Corpus.EXCERPT_LENGTH) {
            html.append(" …");
        }
        html.append("</pre>\n");
        footer(html);
        Files.writeString(dir.resolve("docs/doc-" + doc.id() + ".html"), html, StandardCharsets.UTF_8);
    }

    private String topicLink(String prefix, int topic) {
        return "<a href=\"" + prefix + "topic-" + topic + ".html\"><span class=\"topic-id\">" + topic
                + "</span> " + escape(results.topicLabel(topic, 6)) + "</a>";
    }

    private void header(StringBuilder html, String pageTitle, String root, String section) {
        html.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
            .append("<title>").append(escape(pageTitle)).append(" · ").append(escape(title)).append("</title>\n")
            .append("<link rel=\"stylesheet\" href=\"").append(root).append("style.css\">\n</head>\n<body>\n")
            .append("<header><div class=\"brand\">").append(escape(title)).append("</div><nav>")
            .append("<a href=\"").append(root).append("index.html\"")
            .append(section.equals("topics") ? " class=\"current\"" : "").append(">Topics</a>")
            .append("<a href=\"").append(root).append("documents.html\"")
            .append(section.equals("documents") ? " class=\"current\"" : "").append(">Documents</a>")
            .append("</nav></header>\n<main>\n<h1>").append(escape(pageTitle)).append("</h1>\n");
    }

    private static void footer(StringBuilder html) {
        html.append("</main>\n<footer>Made with the Topic Modeling Tool and MALLET.</footer>\n</body>\n</html>\n");
    }

    private static String bar(double value, double max, String label) {
        double width = max <= 0 ? 0 : Math.min(100.0, 100.0 * value / max);
        return String.format(Locale.ROOT,
                "<span class=\"meter\"><span class=\"bar\"><span class=\"fill\" style=\"width:%.1f%%\"></span></span>"
                + "<span class=\"bar-label\">%s</span></span>", width, label);
    }

    private static String percent(double p) {
        return String.format(Locale.ROOT, "%.1f%%", 100 * p);
    }

    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
