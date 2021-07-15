package jplag;

/** The tokenlist */ // TODO PB: The name 'Structure' is very generic and should be changed to something more descriptive.
public class Structure implements TokenConstants {
    public Token[] tokens = new Token[0];
    Table table = null;
    int hash_length = -1;

    private int numberOfTokens;
    // tokens最大数量
    public Structure() {
        tokens = new Token[400];
        numberOfTokens = 0;
    }

    public final int size() {
        return numberOfTokens;
    }
    //token数量超过400，将加倍最大数量，如果还是超过，则新容量为已有数量
    private final void ensureCapacity(int minCapacity) {
        int oldCapacity = tokens.length;
        if (minCapacity > oldCapacity) {
            Token[] oldTokens = tokens;
            int newCapacity = (2 * oldCapacity);
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            tokens = new Token[newCapacity];
            System.arraycopy(oldTokens, 0, tokens, 0, numberOfTokens);
        }
    }

    // 添加token至tokens列表中  
    public final void addToken(Token token) {
        ensureCapacity(numberOfTokens + 1);
        //下方代码意义不明...
        // if (numberOfTokens > 0 && tokens[numberOfTokens - 1].file.equals(token.file))
        //     token.file = tokens[numberOfTokens - 1].file; // To save memory ...

        // 每个代码文件最后一个token将满足该条件
        if ((numberOfTokens > 0) && (token.getLine() < tokens[numberOfTokens - 1].getLine()) && (token.file.equals(tokens[numberOfTokens - 1].file)))
            token.setLine(tokens[numberOfTokens - 1].getLine());
        // just to make sure

        tokens[numberOfTokens++] = token;
    }

    // 以下代码并没有在版本(3.0.0)中使用
    // @Override
    // public final String toString() {
    //     StringBuffer buffer = new StringBuffer();

    //     try {
    //         for (int i = 0; i < numberOfTokens; i++) {
    //             buffer.append(i);
    //             buffer.append("\t");
    //             buffer.append(tokens[i].toString());
    //             if (i < numberOfTokens - 1) {
    //                 buffer.append("\n");
    //             }
    //         }
    //     } catch (OutOfMemoryError e) {
    //         return "Tokenlist to large for output: " + (numberOfTokens) + " Tokens";
    //     }
    //     return buffer.toString();
    // }
}
