package jplag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import jplag.options.Verbosity;

/**
 * Represents a single submission. A submission can contain multiple files.
 * 表示单个提交。一个提交可以包含多个文件。
 */
public class Submission implements Comparable<Submission> {

    /**
     * Name that uniquely identifies this submission. Will most commonly be the directory or file name.
     * 唯一标识此提交的名称。最常见的是目录或文件名。
     */
    public String name;

    private File submissionFile;

    /**
     * List of files this submission consists of.
     * 此提交包含的文件列表。
     */
    public List<File> files;

    /**
     * List of tokens that have been parsed from the files this submission consists of.
     * 从该提交所包含的文件中解析出来的令牌列表。
     */
    public Structure tokenList;

    /**
     * True, if at least one error occurred while parsing this submission; false otherwise.
     * True，如果解析此提交时至少发生了一个错误;否则false。
     */
    public boolean hasErrors = false;

    private final JPlag program;
    //有参构造
    public Submission(String name, File submissionFile, JPlag program) {
        this.name = name;
        this.submissionFile = submissionFile;
        this.program = program;
        this.files = parseFilesRecursively(submissionFile);
    }

    /**
     * Recursively scan the given directory for nested files. Excluded files and files with an invalid suffix are ignored.
     * 递归扫描给定目录中嵌套的文件，排除的文件和带有无效后缀的文件将被忽略。
     * <p>
     * If the given file is not a directory, the input will be returned as a singleton list.
     * 如果给定的文件不是一个目录，输入将作为一个单例列表返回
     * @param file - File to start the scan from.
     * @return a list of nested files.
     */

    //文件以list集合存储，此方法用来判断输入文件的类型，判断是否是要排除的文件类型，如果是要排除的文件类型，则返回一个空列表。
    private List<File> parseFilesRecursively(File file) {
        if (program.isFileExcluded(file)) {
            return Collections.emptyList();
        }
        //这一步判断输入的文件符合文件类型（就是可以检测那几种语言文件）而且是带有效后缀的文件
        //则作为一个单例列表返回（这里给定的不是一个目录，而是文件）
        if (file.isFile() && program.hasValidSuffix(file)) {
            return Collections.singletonList(file);
        }

        //把输入文件存放在字符串数组中
        String[] nestedFileNames = file.list();
        //如果该数组为空，则返回空集合列表
        if (nestedFileNames == null) {
            return Collections.emptyList();
        }

        List<File> files = new ArrayList<>();

        //循环遍历nestedFileNames数组，递归调用parseFilesRecursively，
        //把不符合的文件排除，符合的文件类型保存在files集合列表中，返回有效的文件
        for (String fileName : nestedFileNames) {
            files.addAll(parseFilesRecursively(new File(file, fileName)));
        }

        return files;
    }
    //返回保存token数组的长度（size()方法在jplag.frontend-utils模块中，下表从0开始）
    public int getNumberOfTokens() {
        if (tokenList == null) {
            return 0;
        }

        return tokenList.size();
    }

    //对提交的文件和当前文件进行比较，返回一个整数，判断当前文件在集合中的位置是在另一个文件之前、之后还是与其位置相同。
    @Override
    public int compareTo(Submission other) {
        return name.compareTo(other.name);
    }

    //重写toString方法来输出文件名
    @Override
    public String toString() {
        return name;
    }

    /**
     * Map all files of this submission to their path relative to the submission directory.
     * 将此提交的所有文件映射到它们相对于提交目录的路径。
     * <p>
     * This method is required to stay compatible with `program.language.parse(...)` as it requires the given file paths to
     * be relative to the submission directory.
     * 此方法需要与' program.language.parse(…)'保持兼容，因为它要求给定的文件路径相对于提交目录。
     * <p>
     * In a future update, `program.language.parse(...)` should probably just take a list of files.
     * 在以后的更新中，' program.language.parse(…)'可能只需要一个文件列表。
     * @param baseFile - File to base all relative file paths on.文件以所有相对文件路径为基础。
     * @param files - List of files to map.文件列表
     * @return an array of file paths relative to the submission directory.相对于提交目录的文件路径数组。
     */
    //得到文件的路径保存在字符串数组中
    private String[] getRelativeFilePaths(File baseFile, List<File> files) {
        Path baseFilePath = baseFile.toPath();

        return files.stream().map(File::toPath).map(baseFilePath::relativize).map(Path::toString).toArray(String[]::new);
    }

    /* parse all the files... 解析所有文件...*/
    public boolean parse() {
        if (program.getOptions().getVerbosity() != Verbosity.PARSER) {
            //文件为空或者集合列表files中的文件个数为空，则打印输出错误信息+文件名
            if (files == null || files.size() == 0) {
                program.print("ERROR: nothing to parse for submission \"" + name + "\"\n", null);
                return false;
            }
        }
        //保存提交的文件和files数组中的文件路径到 relativeFilePaths数组中
        String[] relativeFilePaths = getRelativeFilePaths(submissionFile, files);
        //解析目录中的集合文件生成token，保存在 tokenList中
        tokenList = this.program.getLanguage().parse(submissionFile, relativeFilePaths);
        //判断文件语言是否符合，在判断生成的token列表的长度，若小于3，则打印输出 Submission name is too short!
        if (!program.getLanguage().errors()) {
            if (tokenList.size() < 3) {
                program.print("Submission \"" + name + "\" is too short!\n", null);
                tokenList = null; //在赋空值给tokenList
                hasErrors = true; // invalidate submission  文件提交无效
                return false;
            }
            return true;
        }

        tokenList = null;
        hasErrors = true; // invalidate submission
        //如果为true(默认是false)，执行copySubmission()放法，此方法将无法解析提交文件将存储在单独的目录中，
        if (program.getOptions().isDebugParser()) {
            copySubmission();
        }
        return false;
    }

    /**
     * Used by the "Report" class. All source files are returned as an array of an array of strings.
     * 由“Report”类使用。所有源文件都以字符串数组的数组的形式返回。
     */
    public String[][] readFiles(String[] files) throws jplag.ExitException {
        String[][] result = new String[files.length][];
        String help;
        Vector<String> text = new Vector<>();
        //循环遍历文件长度
        for (int i = 0; i < files.length; i++) {
            text.removeAllElements(); //从这个text中删除所有组件并将其大小设置为零。

            try {
                /* file encoding = "UTF-8" */
                FileInputStream fileInputStream = new FileInputStream(new File(submissionFile, files[i]));
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
                BufferedReader in = new BufferedReader(inputStreamReader);
                //逐行扫描文件，若不为空则添加元素到help，把特殊符号用字符代替
                while ((help = in.readLine()) != null) {
                    help = help.replaceAll("&", "&amp;");
                    help = help.replaceAll("<", "&lt;");
                    help = help.replaceAll(">", "&gt;");
                    help = help.replaceAll("\"", "&quot;");
                    text.addElement(help);
                }

                in.close();
                inputStreamReader.close();
                fileInputStream.close();
            } catch (FileNotFoundException e) {
                System.out.println("File not found: " + ((new File(submissionFile, files[i])).toString()));
            } catch (IOException e) {
                throw new jplag.ExitException("I/O exception!");
            }

            result[i] = new String[text.size()];
            text.copyInto(result[i]);
        }

        return result;
    }

    /**
     * Used by the "Report" class. All source files are returned as an array of an array of chars.
     * 由“Report”类使用。所有源文件都以字符数组的数组的形式返回。
     * 仅在writeIndexedSubmission中使用，usesIndex=true，即to be used with the Character front end
     */
    public char[][] readFilesChar(String[] files) throws jplag.ExitException {
        char[][] result = new char[files.length][];

        for (int i = 0; i < files.length; i++) {
            // If the token path is absolute, ignore the provided directory
            // 如果标记路径是绝对路径，请忽略所提供的目录
            File file = new File(files[i]);
            if (!file.isAbsolute()) {
                file = new File(submissionFile, files[i]);
            }
            
            try {
                int size = (int) file.length();
                char[] buffer = new char[size];

                FileReader reader = new FileReader(file);

                if (size != reader.read(buffer)) {
                    //从文件中读取的文件大小不正确，但可以继续。。。
                    System.out.println("Not right size read from the file, " + "but I will still continue...");
                }

                result[i] = buffer;
                reader.close();
            } catch (FileNotFoundException e) {
                // TODO PB: Should an ExitException be thrown here?
                System.out.println("File not found: " + file.getPath());
            } catch (IOException e) {
                throw new jplag.ExitException("I/O exception reading file \"" + file.getPath() + "\"!", e);
            }
        }

        return result;
    }

    /*
     * This method is used to copy files that can not be parsed to a special folder: jplag/errors/java old_java scheme cpp
     * /001/(...files...) /002/(...files...)
     * 此方法用于将不能解析的文件复制到一个特殊文件夹:jplag/errors/java old_java scheme cpp
     * / 001 /(文件…)/ 002 /文件(……)
     */
    private void copySubmission() {
        File errorDir = null;
        DecimalFormat format = new DecimalFormat("0000");

        try {
            URL url = Submission.class.getResource("errors");
            errorDir = new File(url.getFile());
        } catch (NullPointerException e) {
            return;
        }

        errorDir = new File(errorDir, this.program.getLanguage().getShortName());

        if (!errorDir.exists()) {
            errorDir.mkdir();
        }

        int i = 0;
        File destDir;

        while ((destDir = new File(errorDir, format.format(i))).exists()) {
            i++;
        }

        destDir.mkdir();

        for (i = 0; i < files.size(); i++) {
            copyFile(new File(files.get(i).getAbsolutePath()), new File(destDir, files.get(i).getName()));
        }
    }

    /* Physical copy. :-) 复制文件 */
    private void copyFile(File in, File out) {
        byte[] buffer = new byte[10000];
        try {
            FileInputStream dis = new FileInputStream(in);
            FileOutputStream dos = new FileOutputStream(out);
            int count;
            do {
                count = dis.read(buffer);
                if (count != -1) {
                    dos.write(buffer, 0, count);
                }
            } while (count != -1);
            dis.close();
            dos.close();
        } catch (IOException e) {
            program.print("Error copying file: " + e.toString() + "\n", null);
        }
    }
}
