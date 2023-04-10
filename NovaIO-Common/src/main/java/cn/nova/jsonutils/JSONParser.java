package cn.nova.jsonutils;


import cn.nova.jsonutils.parser.Parser;
import cn.nova.jsonutils.tokenizer.CharReader;
import cn.nova.jsonutils.tokenizer.TokenList;
import cn.nova.jsonutils.tokenizer.Tokenizer;

import java.io.IOException;
import java.io.Reader;

/**
 * 手写Json解析器,只保留了最原始解析功能
 */
public class JSONParser {

    private final Tokenizer tokenizer = new Tokenizer();

    private final Parser parser = new Parser();

    /**
     * 创建Json解释器构造方法
     * @param json 需要Reader流对象及其派生来传递Json流
     * @return 返回一个Object对象
     * @throws IOException
     */
    public Object fromJSON(Reader json) throws IOException {
        CharReader charReader = new CharReader(json);
        TokenList tokens = tokenizer.tokenize(charReader);
        return parser.parse(tokens);
    }
    public <T> T fromJSON(Reader json, Class<T> type) throws IOException {
        CharReader charReader = new CharReader(json);
        TokenList tokens = tokenizer.tokenize(charReader);
        Object obj = parser.parse(tokens);
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }else{
            try{
                return type.cast(obj);
            }catch (ClassCastException e){
                throw new IllegalArgumentException("无法将对象 " + obj + " 转换为类型 " + type.getName());
            }
        }
    }
}
