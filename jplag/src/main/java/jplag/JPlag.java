package jplag;

import static jplag.options.Verbosity.LONG;
import static jplag.options.Verbosity.PARSER;
import static jplag.options.Verbosity.QUIET;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;
import java.util.stream.Collectors;

import jplag.options.JPlagOptions;
import jplag.options.LanguageOption;
import jplag.strategy.ComparisonMode;
import jplag.strategy.ComparisonStrategy;
import jplag.strategy.NormalComparisonStrategy;

/**
 * This class coordinates the whole program flow.
 */
public class JPlag implements ProgramI {

    // INPUT:
    private Submission baseCodeSubmission = null;
    private HashSet<String> excludedFileNames = null; // Set of file names to be excluded in comparison.
    private Language language;

    // CORE COMPONENTS:
    private ComparisonStrategy comparisonStrategy;
    private GreedyStringTiling gSTiling = new GreedyStringTiling(this); // Contains the comparison logic.
    private final JPlagOptions options;

    // ERROR REPORTING:
    private String currentSubmissionName = "<Unknown submission>"; // TODO PB: This should be moved to
                                                                   // parseSubmissions(...)
    private int errors = 0;
    private Vector<String> errorVector = new Vector<>(); // Vector of errors that occurred during the execution of the
                                                         // program.

    /**
     * Creates and initializes a JPlag instance, parameterized by a set of options.
     * 
     * @param options determines the parameterization.
     * @throws ExitException if the initialization fails.
     */
    public JPlag(JPlagOptions options) throws ExitException {
        this.options = options;
        initializeLanguage();
        initializeComparisonStrategy();
        checkBaseCodeOption();
    }

    /**
     * Main procedure, executes the comparison of source code submissions.
     * 
     * @return the results of the comparison, specifically the submissions whose
     *         similarity exceeds a set threshold.
     * @throws ExitException if the JPlag exits preemptively.
     */
    public JPlagResult run() throws ExitException {
        // 1. Preparation:
        File rootDir = new File(options.getRootDirName());
        if (!rootDir.exists()) {
            throw new ExitException("Root directory " + options.getRootDirName() + " does not exist!");
        }
        if (!rootDir.isDirectory()) {
            throw new ExitException(options.getRootDirName() + " is not a directory!");
        }
        readExclusionFile(); // This file contains all files names which are excluded

        // 2. Parse and validate submissions:
        Vector<Submission> submissions = findSubmissions(rootDir);
        parseAllSubmissions(submissions, baseCodeSubmission);
        submissions = filterValidSubmissions(submissions);
        if (submissions.size() < 2) {
            printErrors();
            throw new ExitException(
                    "Not enough valid submissions! (found " + submissions.size() + " valid submissions)",
                    ExitException.NOT_ENOUGH_SUBMISSIONS_ERROR);
        }

        // 3. Compare valid submissions:
        errorVector = null; // errorVector is not needed anymore
        System.gc();
        JPlagResult result = comparisonStrategy.compareSubmissions(submissions, baseCodeSubmission);
        return result;
    }

    @Override
    public void addError(String errorMessage) {
        errorVector.add("[" + currentSubmissionName + "]\n" + errorMessage);
        print(errorMessage, null);
    }

    /**
     * @return the configured language in which the submissions are written.
     */
    public Language getLanguage() {
        return language;
    }

    /**
     * @return the program options which allow to configure JPlag.
     */
    protected JPlagOptions getOptions() {
        return this.options; // TS: Should not be accessible, as options should be set before passing them to
                             // this class.
    }

    /**
     * Checks if a file has a valid suffix for the current language.
     * 
     * @param file is the file to check.
     * @return true if the file suffix matches the language.
     */
    public boolean hasValidSuffix(File file) {
        String[] validSuffixes = options.getFileSuffixes();

        // This is the case if either the language frontends or the CLI did not set the
        // valid suffixes array in options
        if (validSuffixes == null || validSuffixes.length == 0) {
            return true;
        }
        return Arrays.stream(validSuffixes).anyMatch(suffix -> file.getName().endsWith(suffix));
    }

    /**
     * Checks if a file is excluded or not.
     */
    public boolean isFileExcluded(File file) {
        if (excludedFileNames == null) {
            return false;
        }
        return excludedFileNames.stream().anyMatch(excludedName -> file.getName().endsWith(excludedName));
    }

    // 输出内容
    @Override
    public void print(String message, String longMessage) {
        if (options.getVerbosity() == PARSER) {
            if (longMessage != null) {
                System.out.println(longMessage);
            } else if (message != null) {
                System.out.println(message);
            }
        }
        if (options.getVerbosity() == QUIET) {
            return;
        }
        try {
            if (message != null) {
                System.out.print(message);
            }

            if (longMessage != null) {
                if (options.getVerbosity() == LONG) {
                    System.out.print(longMessage);
                }
            }
        } catch (Throwable e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * This method checks whether the base code directory value is valid.
     */
    private void checkBaseCodeOption() throws ExitException {
        if (!this.options.hasBaseCode()) {
            return;
        }

        String baseCodePath = this.options.getRootDirName() + File.separator + this.options.getBaseCodeSubmissionName();

        if (!(new File(this.options.getRootDirName())).exists()) {
            throw new ExitException("Root directory \"" + this.options.getRootDirName() + "\" doesn't exist!",
                    ExitException.BAD_PARAMETER);
        }

        File f = new File(baseCodePath);

        if (!f.exists()) {
            // Base code dir doesn't exist
            throw new ExitException("Basecode directory \"" + baseCodePath + "\" doesn't exist!",
                    ExitException.BAD_PARAMETER);
        }

        if (this.options.getSubdirectoryName() != null && this.options.getSubdirectoryName().length() != 0) {
            f = new File(baseCodePath, this.options.getSubdirectoryName());

            if (!f.exists()) {
                throw new ExitException("Basecode directory doesn't contain" + " the subdirectory \""
                        + this.options.getSubdirectoryName() + "\"!", ExitException.BAD_PARAMETER);
            }
        }

        System.out.println("Basecode directory \"" + baseCodePath + "\" will be used");
    }

    private Vector<Submission> filterValidSubmissions(Vector<Submission> submissions) {
        return submissions.stream().filter(submission -> !submission.hasErrors)
                .collect(Collectors.toCollection(Vector::new));
    }

    /**
     * Find all submissions in the given root directory.
     */
    private Vector<Submission> findSubmissions(File rootDir) throws ExitException {
        String[] fileNamesInRootDir = getSortedFileNamesInRootDir(rootDir);
        return mapFileNamesInRootDirToSubmissions(fileNamesInRootDir, rootDir);
    }

    private String[] getSortedFileNamesInRootDir(File rootDir) throws ExitException {
        String[] fileNamesInRootDir;

        try {
            fileNamesInRootDir = rootDir.list();
        } catch (SecurityException e) {
            throw new ExitException("Cannot list files of the root directory! " + e.getMessage());
        }

        if (fileNamesInRootDir == null) {
            throw new ExitException("Cannot list files of the root directory! "
                    + "Make sure the specified root directory is in fact a directory.");
        }

        Arrays.sort(fileNamesInRootDir);

        return fileNamesInRootDir;
    }

    private void initializeComparisonStrategy() throws ExitException {
        ComparisonMode mode = options.getComparisonMode();
        switch (mode) { // TODO TS: Currently only one comparison strategy supported
            case NORMAL:
                this.comparisonStrategy = new NormalComparisonStrategy(options, gSTiling);
                return;
            default:
                throw new ExitException("Illegal comparison mode: " + options.getComparisonMode());
        }
    }

    private void initializeLanguage() throws ExitException {
        LanguageOption languageOption = this.options.getLanguageOption();

        try {
            Constructor<?> constructor = Class.forName(languageOption.getClassPath()).getConstructor(ProgramI.class);
            Object[] constructorParams = { this };

            Language language = (Language) constructor.newInstance(constructorParams);

            this.language = language;
            this.options.setLanguage(language);
        } catch (NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();

            throw new ExitException("Language instantiation failed", ExitException.BAD_LANGUAGE_ERROR);
        }

        this.options.setLanguageDefaults(this.getLanguage());

        System.out.println("Initialized language " + this.getLanguage().name());
    }

    /*
     * 对提交的文件进行验证，文件是否被排除，后缀是否被忽略，子目录是否有效，是否为基础代码.
     * 
     * @return 提交的所有的有效文件
     */
    private Vector<Submission> mapFileNamesInRootDirToSubmissions(String[] fileNames, File rootDir)
            throws ExitException {
        Vector<Submission> submissions = new Vector<>();

        for (String fileName : fileNames) {
            File submissionFile = new File(rootDir, fileName);
            // excluede submission files
            if (isFileExcluded(submissionFile)) {
                System.out.println("Exclude submission: " + submissionFile.getName());
                continue;
            }
            // ignore submission suffix
            if (submissionFile.isFile() && !hasValidSuffix(submissionFile)) {
                System.out.println("Ignore submission with invalid suffix: " + submissionFile.getName());
                continue;
            }
            // 子目录是否有效
            if (submissionFile.isDirectory() && options.getSubdirectoryName() != null) {
                // Use subdirectory instead
                submissionFile = new File(submissionFile, options.getSubdirectoryName());

                if (!submissionFile.exists()) {
                    throw new ExitException(String.format("Submission %s does not contain the given subdirectory '%s'",
                            fileName, options.getSubdirectoryName()));
                }

                if (!submissionFile.isDirectory()) {
                    throw new ExitException(String.format("The given subdirectory '%s' is not a directory!",
                            options.getSubdirectoryName()));
                }
            }

            Submission submission = new Submission(fileName, submissionFile, this);
            // 是否为基础代码文件
            if (options.hasBaseCode() && options.getBaseCodeSubmissionName().equals(fileName)) {
                baseCodeSubmission = submission;
            } else {
                submissions.addElement(submission);
            }
        }

        return submissions;
    }

    /**
     * TODO PB: Find a better way to separate parseSubmissions(...) and
     * parseBaseCodeSubmission(...)
     */
    private void parseAllSubmissions(Vector<Submission> submissions, Submission baseCodeSubmission)
            throws ExitException {
        try {
            parseSubmissions(submissions);
            System.gc();
            parseBaseCodeSubmission(baseCodeSubmission);
        } catch (OutOfMemoryError e) {
            System.gc();

            throw new ExitException("Out of memory during parsing of submission \"" + currentSubmissionName + "\"");
        } catch (Throwable e) {
            e.printStackTrace();

            throw new ExitException(
                    "Unknown exception during parsing of " + "submission \"" + currentSubmissionName + "\"");
        }
    }

    /**
     * Parse the given base code submission.
     */
    private void parseBaseCodeSubmission(Submission subm) throws ExitException {
        if (subm == null) {
            // TODO:
            // options.useBasecode = false;
            return;
        }

        long msec = System.currentTimeMillis();
        print("----- Parsing basecode submission: " + subm.name + "\n", null);

        // lets go:

        if (!subm.parse()) {
            printErrors();
            throw new ExitException("Bad basecode submission");
        }

        if (subm.tokenList != null && subm.getNumberOfTokens() < options.getMinTokenMatch()) {
            throw new ExitException(
                    "Basecode submission contains fewer tokens " + "than minimum match length allows!\n");
        }

        if (options.hasBaseCode()) {
            gSTiling.createHashes(subm.tokenList, options.getMinTokenMatch(), true);
        }

        print("\nBasecode submission parsed!\n", null);

        long time = System.currentTimeMillis() - msec;

        print("\n", "\nTime for parsing Basecode: " + ((time / 3600000 > 0) ? (time / 3600000) + " h " : "")
                + ((time / 60000 > 0) ? ((time / 60000) % 60000) + " min " : "") + (time / 1000 % 60) + " sec\n");
    }

    /**
     * Parse all given submissions.
     */
    private void parseSubmissions(Vector<Submission> submissions) {
        if (submissions == null) {
            System.out.println("Nothing to parse!");
            return;
        }

        int count = 0;

        long msec = System.currentTimeMillis();
        Iterator<Submission> iter = submissions.iterator();

        int invalid = 0;
        while (iter.hasNext()) {
            boolean ok;
            boolean removed = false;
            Submission subm = iter.next();

            print(null, "------ Parsing submission: " + subm.name + "\n");
            currentSubmissionName = subm.name;
            // 对提交的代码进行parse
            if (!(ok = subm.parse())) {
                errors++;
            }
            // parse总数(包括错误的)
            count++;
            // Token数少于指定最小Token数
            if (subm.tokenList != null && subm.getNumberOfTokens() < options.getMinTokenMatch()) {
                print(null, "Submission contains fewer tokens than minimum match " + "length allows!\n");
                subm.tokenList = null;
                invalid++;
                removed = true;
            }

            if (ok && !removed) {
                print(null, "OK\n");
            } else {
                print(null, "ERROR -> Submission removed\n");
            }
        }

        print("\n" + (count - errors - invalid) + " submissions parsed successfully!\n" + errors + " parser error"
                + (errors != 1 ? "s!\n" : "!\n"), null);

        if (invalid != 0) {
            print(null,
                    invalid + ((invalid == 1) ? " submission is not valid because it contains"
                            : " submissions are not valid because they contain")
                            + " fewer tokens\nthan minimum match length allows.\n");
        }

        long time = System.currentTimeMillis() - msec;

        print("\n\n",
                "\nTotal time for parsing: " + ((time / 3600000 > 0) ? (time / 3600000) + " h " : "")
                        + ((time / 60000 > 0) ? ((time / 60000) % 60000) + " min " : "") + (time / 1000 % 60) + " sec\n"
                        + "Time per parsed submission: " + (count > 0 ? (time / count) : "n/a") + " msec\n\n");
    }

    /**
     * Print all errors from the errorVector.
     */
    private void printErrors() {
        StringBuilder errorStr = new StringBuilder();

        for (String str : errorVector) {
            errorStr.append(str);
            errorStr.append('\n');
        }

        System.out.println(errorStr.toString());
    }

    /*
     * If an exclusion file is given, it is read in and all stings are saved in the
     * set "excluded".
     */
    private void readExclusionFile() {
        if (options.getExclusionFileName() == null) {
            return;
        }

        excludedFileNames = new HashSet<>();

        try {
            BufferedReader in = new BufferedReader(new FileReader(options.getExclusionFileName()));
            String line;

            while ((line = in.readLine()) != null) {
                excludedFileNames.add(line.trim());
            }

            in.close();
        } catch (IOException e) {
            System.out.println("Could not read exclusion file: " + options.getExclusionFileName());
        }

        if (options.getVerbosity() == LONG) {
            print(null, "Excluded files:\n");

            for (String excludedFileName : excludedFileNames) {
                print(null, "  " + excludedFileName + "\n");
            }
        }
    }
}
