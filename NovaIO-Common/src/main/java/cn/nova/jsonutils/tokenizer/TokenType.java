package cn.nova.jsonutils.tokenizer;

public enum TokenType {
    /**
     * 使用二进制位表示Json文件中所有类型的token
     */
    BEGIN_OBJECT(1),
    END_OBJECT(2),
    BEGIN_ARRAY(4),
    END_ARRAY(8),
    NULL(16),
    NUMBER(32),
    STRING(64),
    BOOLEAN(128),
    SEP_COLON(256),
    SEP_COMMA(512),
    END_DOCUMENT(1024);
    private final int code;

    TokenType(int code) {
        this.code = code;
    }

    /**
     * 返回当前token的类型
     * @return int(获得的Token种类)
     */
    public int getTokenCode() {
        return code;
    }
}
