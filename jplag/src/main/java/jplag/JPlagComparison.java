package jplag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * This method represents the whole result of a comparison between two
 * submissions.
 */
public class JPlagComparison implements Comparator<JPlagComparison> {

    public Submission firstSubmission;
    public Submission secondSubmission;

    public JPlagBaseCodeComparison bcMatchesA = null;
    public JPlagBaseCodeComparison bcMatchesB = null;

    public List<Match> matches = new ArrayList<>();

    public JPlagComparison(Submission firstSubmission, Submission secondSubmission) {
        this.firstSubmission = firstSubmission;
        this.secondSubmission = secondSubmission;
    }

    // length为Token数量
    public final void addMatch(int startA, int startB, int length) {
        for (Match match : matches) {
            if (match.overlap(startA, startB, length)) {
                return;
            }
        }

        matches.add(new Match(startA, startB, length));
        System.out.println("已添加matches, startA,startB " + startA + "---" + startB);
    }

    /*
     * s==0 uses the start indexes of subA as key for the sorting algorithm.
     * Otherwise the start indexes of subB are used.
     * 该方法仅在writeIndexedSubmission中使用，sort操作
     */
    public final int[] sort_permutation(int s) { // bubblesort!!!
        int size = matches.size();
        int[] perm = new int[size];
        int i, j, tmp;

        // initialize permutation array
        for (i = 0; i < size; i++) {
            perm[i] = i;
        }

        if (s == 0) { // submission A
            for (i = 1; i < size; i++) {
                for (j = 0; j < (size - i); j++) {
                    if (matches.get(perm[j]).startA > matches.get(perm[j + 1]).startA) {
                        tmp = perm[j];
                        perm[j] = perm[j + 1];
                        perm[j + 1] = tmp;
                    }
                }
            }
        } else { // submission B
            for (i = 1; i < size; i++) {
                for (j = 0; j < (size - i); j++) {
                    if (matches.get(perm[j]).startB > matches.get(perm[j + 1]).startB) {
                        tmp = perm[j];
                        perm[j] = perm[j + 1];
                        perm[j + 1] = tmp;
                    }
                }
            }
        }
        return perm;
    }

    /*
     * A few methods to calculate some statistical data
     */

    /**
     * Get the total number of matched tokens for this comparison. 本次对比中tokens总数
     */
    public final int getNumberOfMatchedTokens() {
        int numberOfMatchedTokens = 0;

        for (Match match : matches) {
            numberOfMatchedTokens += match.length;
        }

        return numberOfMatchedTokens;
    }

    // 本次对比中最大的token数，用于颜色更改
    private int biggestMatch() {
        int erg = 0;

        for (Match match : matches) {
            if (match.length > erg) {
                erg = match.length;
            }
        }

        return erg;
    }

    // 88.135590 --> 88.100000
    public final float roundedPercent() {
        float percent = percent();
        return ((int) (percent * 10)) / (float) 10;
    }

    // 以下代码计算相似度，avg, self, min, max
    // 大量重复，建议改写为方法传参
    public final float percent() {
        float sa, sb;
        if (bcMatchesB != null && bcMatchesA != null) {
            sa = firstSubmission.getNumberOfTokens() - firstSubmission.files.size()
                    - bcMatchesA.getNumberOfMatchedTokens();
            sb = secondSubmission.getNumberOfTokens() - secondSubmission.files.size()
                    - bcMatchesB.getNumberOfMatchedTokens();
        } else {
            sa = firstSubmission.getNumberOfTokens() - firstSubmission.files.size();
            sb = secondSubmission.getNumberOfTokens() - secondSubmission.files.size();
        }
        return (200 * (float) getNumberOfMatchedTokens()) / (sa + sb);
    }

    // self
    public final float percentA() {
        int divisor;
        if (bcMatchesA != null) {
            divisor = firstSubmission.getNumberOfTokens() - firstSubmission.files.size()
                    - bcMatchesA.getNumberOfMatchedTokens();
        } else {
            divisor = firstSubmission.getNumberOfTokens() - firstSubmission.files.size();
        }
        return (divisor == 0 ? 0f : (getNumberOfMatchedTokens() * 100 / (float) divisor));
    }

    // self
    public final float percentB() {
        int divisor;
        if (bcMatchesB != null) {
            divisor = secondSubmission.getNumberOfTokens() - secondSubmission.files.size()
                    - bcMatchesB.getNumberOfMatchedTokens();
        } else {
            divisor = secondSubmission.getNumberOfTokens() - secondSubmission.files.size();
        }
        return (divisor == 0 ? 0f : (getNumberOfMatchedTokens() * 100 / (float) divisor));
    }

    // 相似度 Max
    public final float percentMaxAB() {
        float a = percentA();
        float b = percentB();
        if (a > b) {
            return a;
        } else {
            return b;
        }
    }

    // 相似度 Min
    public final float percentMinAB() {
        float a = percentA();
        float b = percentB();
        if (a < b) {
            return a;
        } else {
            return b;
        }
    }

    // basecode占比
    private final float percentBasecodeA() {
        float sa = firstSubmission.getNumberOfTokens() - firstSubmission.files.size();
        return bcMatchesA.getNumberOfMatchedTokens() * 100 / sa;
    }

    private final float percentBasecodeB() {
        float sb = secondSubmission.getNumberOfTokens() - secondSubmission.files.size();
        return bcMatchesB.getNumberOfMatchedTokens() * 100 / sb;
    }

    public final float roundedPercentBasecodeA() {
        float percent = percentBasecodeA();
        return ((int) (percent * 10)) / (float) 10;
    }

    public final float roundedPercentBasecodeB() {
        float percent = percentBasecodeB();
        return ((int) (percent * 10)) / (float) 10;
    }

    /**
     * This method returns all the files which contributed to a match. Parameter: j
     * == 0 submission A, j != 0 submission B.
     */
    public final String[] files(int j) {
        if (matches.size() == 0) {
            return new String[] {};
        }

        Token[] tokens = (j == 0 ? firstSubmission : secondSubmission).tokenList.tokens;
        int i, h, starti, starth, count = 1;

        o1: for (i = 1; i < matches.size(); i++) {
            starti = (j == 0 ? matches.get(i).startA : matches.get(i).startB);
            for (h = 0; h < i; h++) {
                starth = (j == 0 ? matches.get(h).startA : matches.get(h).startB);
                if (tokens[starti].file.equals(tokens[starth].file)) {
                    continue o1;
                }
            }
            count++;
        }

        String[] res = new String[count];
        res[0] = tokens[(j == 0 ? matches.get(0).startA : matches.get(0).startB)].file;
        count = 1;

        o2: for (i = 1; i < matches.size(); i++) {
            starti = (j == 0 ? matches.get(i).startA : matches.get(i).startB);
            for (h = 0; h < i; h++) {
                starth = (j == 0 ? matches.get(h).startA : matches.get(h).startB);
                if (tokens[starti].file.equals(tokens[starth].file)) {
                    continue o2;
                }
            }
            res[count++] = tokens[starti].file;
        }

        /*
         * sort by file name. (so that equally named files are displayed approximately
         * side by side.)
         */
        Arrays.sort(res);

        return res;
    }

    /**
     * The bigger a match (length) is relatively to the biggest match the redder is
     * the color returned by this method.
     */
    public String color(int length) {
        int color = 255 * length / biggestMatch();
        String help = (color < 16 ? "0" : "") + Integer.toHexString(color);
        return "#" + help + "0000";
    }

    @Override
    public int compare(JPlagComparison o1, JPlagComparison o2) {
        float p1 = o1.percent();
        float p2 = o2.percent();
        if (p1 == p2) {
            return 0;
        }
        if (p1 > p2) {
            return -1;
        } else {
            return 1;
        }
    }

    // 判断相似度是否相等
    // 未引用
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JPlagComparison)) {
            return false;
        }
        return (compare(this, (JPlagComparison) obj) == 0);
    }

    // 未引用
    @Override
    public String toString() {
        return firstSubmission.name + " <-> " + secondSubmission.name;
    }

}
