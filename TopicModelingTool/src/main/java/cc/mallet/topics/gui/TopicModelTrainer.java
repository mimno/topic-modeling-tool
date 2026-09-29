package cc.mallet.topics.gui;

import cc.mallet.topics.ParallelTopicModel;

import java.io.IOException;
import java.util.function.IntConsumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Configures and runs Mallet's {@link ParallelTopicModel} on a {@link Corpus}. */
public final class TopicModelTrainer {

    /** Mallet logs "<120> LL/token: -8.1" every ten iterations. */
    private static final Pattern ITERATION = Pattern.compile("^<(\\d+)>");

    private TopicModelTrainer() {}

    /**
     * Train a topic model. {@code progress} receives the current iteration
     * number (every ten iterations) and may be null.
     */
    public static ParallelTopicModel train(Corpus corpus, TrainingOptions options, IntConsumer progress)
            throws IOException {
        ParallelTopicModel model =
                new ParallelTopicModel(options.numTopics, options.alphaSum, options.beta);
        model.setNumIterations(options.numIterations);
        model.setNumThreads(options.numThreads);
        model.setOptimizeInterval(options.optimizeInterval);
        model.setBurninPeriod(Math.min(200, options.numIterations / 2));
        model.setRandomSeed(options.randomSeed);
        model.setTopicDisplay(options.showTopicsInterval, 8);
        model.addInstances(corpus.getInstances());

        Handler progressHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                Matcher m = ITERATION.matcher(String.valueOf(record.getMessage()));
                if (m.find()) {
                    progress.accept(Integer.parseInt(m.group(1)));
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };

        if (progress != null) {
            ParallelTopicModel.logger.addHandler(progressHandler);
        }
        try {
            model.estimate();
        } finally {
            ParallelTopicModel.logger.removeHandler(progressHandler);
        }
        return model;
    }
}
