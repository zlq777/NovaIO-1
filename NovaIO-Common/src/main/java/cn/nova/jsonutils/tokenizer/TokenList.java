package cn.nova.jsonutils.tokenizer;

import java.util.ArrayList;
import java.util.List;

public class TokenList {

    private final List<Token> tokens;
    private int pos = 0;

    public TokenList() {
        this.tokens = new ArrayList<>();
    }

    /**
     * 往TokenList末尾添加一个Token
     * @param token
     */
    public void add(Token token) {
        tokens.add(token);
    }

    /**
     * 返回栈顶的Token
     * @return Token
     */
    public Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    /**
     * 返回栈顶前一个的Token
     * @return Token
     */
    public Token peekPrevious() {
        return pos - 1 < 0 ? null : tokens.get(pos - 2);
    }

    /**
     * 返回下一个Token
     * @return Token
     */
    public Token next() {
        return tokens.get(pos++);
    }

    /**
     *判断是否还有tokens
     * @return boolean
     */
    public boolean hasMore() {
        return pos < tokens.size();
    }

}
