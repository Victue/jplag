package jplag.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.time.LocalTime;

import jplag.GreedyStringTiling;
import jplag.JPlagComparison;
import jplag.JPlagResult;
import jplag.Submission;
import jplag.options.JPlagOptions;

public class NormalComparisonStrategy extends AbstractComparisonStrategy {

    public NormalComparisonStrategy(JPlagOptions options, GreedyStringTiling greedyStringTiling) {
        super(options, greedyStringTiling);
    }

    @Override
    public JPlagResult compareSubmissions(Vector<Submission> submissions, Submission baseCodeSubmission) {
        // 考虑基础代码
        if (baseCodeSubmission != null) {
            compareSubmissionsToBaseCode(submissions, baseCodeSubmission);
        }

        LocalTime StartTime = LocalTime.now();
        long timeBeforeStartInMillis = System.currentTimeMillis();
        int i, j, numberOfSubmissions = submissions.size();
        Submission first, second;
        List<JPlagComparison> comparisons = new ArrayList<>();
        JPlagComparison comparison;
        // 调用GST进行相似度比较，在命令行输出结果
        for (i = 0; i < (numberOfSubmissions - 1); i++) {
            first = submissions.elementAt(i);
            if (first.tokenList == null) {
                continue;
            }
            for (j = (i + 1); j < numberOfSubmissions; j++) {
                second = submissions.elementAt(j);
                if (second.tokenList == null) {
                    continue;
                }
                comparison = greedyStringTiling.compare(first, second);

                // TODO SH: Why does this differ from the results shown in the result web page?
                System.out.println("Comparing " + first.name + "-" + second.name + ": " + comparison.percent());
                // 考虑基础代码和相似度阈值
                if (baseCodeSubmission != null) {
                    comparison.bcMatchesA = baseCodeMatches.get(comparison.firstSubmission.name);
                    comparison.bcMatchesB = baseCodeMatches.get(comparison.secondSubmission.name);
                }
                if (isAboveSimilarityThreshold(comparison)) {
                    comparisons.add(comparison);
                }
            }
        }
        
        LocalTime currentTime = LocalTime.now();
        // 运行时间
        long durationInMillis = System.currentTimeMillis() - timeBeforeStartInMillis;
        System.out.println(" -开始时间:" + StartTime + " -结束时间:" + currentTime);
        return new JPlagResult(comparisons, durationInMillis, numberOfSubmissions, options);
    }

}
