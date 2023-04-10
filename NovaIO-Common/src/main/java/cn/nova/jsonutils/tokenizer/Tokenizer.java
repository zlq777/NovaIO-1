package cn.nova.jsonutils.tokenizer;

import java.io.IOException;

public class Tokenizer {

    private CharReader charReader;

    private TokenList tokens;

    /**
     * 通过CharReader流初始化一个TokenList
     *
     * @param charReader json字符流
     * @return TokenList
     * @throws IOException
     */
    public TokenList tokenize(CharReader charReader) throws IOException {
        this.charReader = charReader;
        tokens = new TokenList();
        tokenize();

        return tokens;
    }

    /**
     * 把Json字符串解析生成预定义好的Token序列,为后面JsonParse解析语法做前置处理
     *
     * @throws IOException
     */
    private void tokenize() throws IOException {
        Token token;
        do {
            token = start();
            tokens.add(token);
        } while (token.getTokenType() != TokenType.END_DOCUMENT);
    }

    /**
     * 用于判断各种当前的Token类型,同时传递给相应的判断器,对于合法的最后返回封装好的Token
     * @return 返回封装好了各种类型的Token
     * @throws IOException
     */
    private Token start() throws IOException {
        char ch;
        do {
            if (!charReader.hasMore()) {
                return new Token(TokenType.END_DOCUMENT, null);
            }

            ch = charReader.next();
        } while (isWhiteSpace(ch));

        switch (ch) {
            case '{' -> {
                return new Token(TokenType.BEGIN_OBJECT, String.valueOf(ch));
            }
            case '}' -> {
                return new Token(TokenType.END_OBJECT, String.valueOf(ch));
            }
            case '[' -> {
                return new Token(TokenType.BEGIN_ARRAY, String.valueOf(ch));
            }
            case ']' -> {
                return new Token(TokenType.END_ARRAY, String.valueOf(ch));
            }
            case ',' -> {
                return new Token(TokenType.SEP_COMMA, String.valueOf(ch));
            }
            case ':' -> {
                return new Token(TokenType.SEP_COLON, String.valueOf(ch));
            }
            case 'n' -> {
                return readNull();
            }
            case 't', 'f' -> {
                return readBoolean();
            }
            case '"' -> {
                return readString();
            }
            case '-' -> {
                return readNumber();
            }
        }

        if (isDigit(ch)) {
            return readNumber();
        }

        throw new RuntimeException("Illegal character");
    }

    /**
     * @param ch 读入字符,并判断是否是空白符
     * @return
     */
    private boolean isWhiteSpace(char ch) {
        return (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n');
    }

    /**
     * 为转化为TokenList中的读取字符串方法
     * @return 返回字符串型 Token
     * @throws IOException
     */
    private Token readString() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (; ; ) {
            char ch = charReader.next();
            if (ch == '\\') {
                if (!isEscape()) {
                    throw new RuntimeException("Invalid escape character");
                }
                sb.append('\\');
                ch = charReader.peek();
                sb.append(ch);
                if (ch == 'u') {
                    for (int i = 0; i < 4; i++) {
                        ch = charReader.next();
                        if (isHex(ch)) {
                            sb.append(ch);
                        } else {
                            throw new RuntimeException("Invalid character");
                        }
                    }
                }
            } else if (ch == '"') {
                return new Token(TokenType.STRING, sb.toString());
            } else if (ch == '\r' || ch == '\n') {
                throw new RuntimeException("Invalid character");
            } else {
                sb.append(ch);
            }
        }
    }

    /**
     * @return 是否为各种类型的空格
     * @throws IOException
     */
    private boolean isEscape() throws IOException {
        char ch = charReader.next();
        return (ch == '"' || ch == '\\' || ch == 'u' || ch == 'r'
                || ch == 'n' || ch == 'b' || ch == 't' || ch == 'f');

    }

    /**
     * @param ch 当前token
     * @return 是否为十六进制
     */
    private boolean isHex(char ch) {
        return ((ch >= '0' && ch <= '9') || ('a' <= ch && ch <= 'f')
                || ('A' <= ch && ch <= 'F'));
    }

    /**
     * 处理数字型TokenList
     * @return 返回数字型Token
     * @throws IOException
     */
    private Token readNumber() throws IOException {
        char ch = charReader.peek();
        StringBuilder sb = new StringBuilder();
        if (ch == '-') {
            sb.append(ch);
            ch = charReader.next();
            if (ch == '0') {
                sb.append(ch);
                sb.append(readFracAndExp());
            } else if (isDigitOne2Nine(ch)) {
                do {
                    sb.append(ch);
                    ch = charReader.next();
                } while (isDigit(ch));
                if (ch != (char) -1) {
                    charReader.back();
                    sb.append(readFracAndExp());
                }
            } else {
                throw new RuntimeException("Invalid minus number");
            }
        } else if (ch == '0') {
            sb.append(ch);
            sb.append(readFracAndExp());
        } else {
            do {
                sb.append(ch);
                ch = charReader.next();
            } while (isDigit(ch));
            if (ch != (char) -1) {
                charReader.back();
                sb.append(readFracAndExp());
            }
        }

        return new Token(TokenType.NUMBER, sb.toString());
    }

    /**
     *
     * @param ch 当前token
     * @return 是否为e或者E(科学计数法)
     * @throws IOException
     */
    private boolean isExp(char ch) throws IOException {
        return ch == 'e' || ch == 'E';
    }

    /**
     * @param ch 当前token
     * @return 是否为数字
     */
    private boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    /**
     * @param ch 当前token
     * @return 是否为1~9的数字
     */
    private boolean isDigitOne2Nine(char ch) {
        return ch >= '0' && ch <= '9';
    }

    /**
     * @return 判断是否为合法小数并封装传回字符串对象
     * @throws IOException
     */
    private String readFracAndExp() throws IOException {
        StringBuilder sb = new StringBuilder();
        char ch = charReader.next();
        if (ch == '.') {
            sb.append(ch);
            ch = charReader.next();
            if (!isDigit(ch)) {
                throw new RuntimeException("Invalid frac");
            }
            do {
                sb.append(ch);
                ch = charReader.next();
            } while (isDigit(ch));

            if (isExp(ch)) {
                sb.append(ch);
                sb.append(readExp());
            } else {
                if (ch != (char) -1) {
                    charReader.back();
                }
            }
        } else if (isExp(ch)) {
            sb.append(ch);
            sb.append(readExp());
        } else {
            charReader.back();
        }

        return sb.toString();
    }

    /**
     *
     * @return 读取并判断科学计数法,返回封装好的字符串对象
     * @throws IOException
     */
    private String readExp() throws IOException {
        StringBuilder sb = new StringBuilder();
        char ch = charReader.next();
        if (ch == '+' || ch == '-') {
            sb.append(ch);
            ch = charReader.next();
            if (isDigit(ch)) {
                do {
                    sb.append(ch);
                    ch = charReader.next();
                } while (isDigit(ch));

                if (ch != (char) -1) {    // 读取结束，不用回退
                    charReader.back();
                }
            } else {
                throw new RuntimeException("e or E");
            }
        } else {
            throw new RuntimeException("e or E");
        }

        return sb.toString();
    }

    /**
     * @return 返回true或false型Token
     * @throws IOException
     */
    private Token readBoolean() throws IOException {
        if (charReader.peek() == 't') {
            if (!(charReader.next() == 'r' && charReader.next() == 'u' && charReader.next() == 'e')) {
                throw new RuntimeException("Invalid json string");
            }

            return new Token(TokenType.BOOLEAN, "true");
        } else {
            if (!(charReader.next() == 'a' && charReader.next() == 'l'
                    && charReader.next() == 's' && charReader.next() == 'e')) {
                throw new RuntimeException("Invalid json string");
            }

            return new Token(TokenType.BOOLEAN, "false");
        }
    }

    /**
     * 当遇到字母 n 就要判断是否为 null
     *
     * @return 是否为 null
     * @throws IOException
     */
    private Token readNull() throws IOException {
        if (!(charReader.next() == 'u' && charReader.next() == 'l' && charReader.next() == 'l')) {
            throw new RuntimeException("Invalid json string");
        }

        return new Token(TokenType.NULL, "null");
    }
}
