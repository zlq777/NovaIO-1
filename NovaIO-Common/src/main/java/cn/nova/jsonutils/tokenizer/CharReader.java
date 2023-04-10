package cn.nova.jsonutils.tokenizer;

import java.io.IOException;
import java.io.Reader;

public class CharReader {

    private static final int BUFFER_SIZE = 1024;

    private final Reader reader;

    private final char[] buffer;

    private int pos;

    private int size;

    public CharReader(Reader reader) {
        this.reader = reader;
        buffer = new char[BUFFER_SIZE];
    }

    /**
     * 返回 pos 下标处的字符，并返回
     * @return 栈顶字符
     */
    public char peek() {
        if (pos - 1 >= size) {
            return (char) -1;
        }

        return buffer[Math.max(0, pos - 1)];
    }

    /**
     * 返回 pos 下标处的字符，并将 pos + 1，最后返回字符
     * @return 返回下一个字符
     * @throws IOException
     */
    public char next() throws IOException {
        if (!hasMore()) {
            return (char) -1;
        }

        return buffer[pos++];
    }

    public void back() {
        pos = Math.max(0, --pos);
    }

    /**
     * @return 是否是否还有未读入内容
     * @throws IOException
     */
    public boolean hasMore() throws IOException {
        if (pos < size) {
            return true;
        }

        fillBuffer();
        return pos < size;
    }

    /**
     * 继续往缓冲区Buffer读入数据,如果读完就结束
     * @throws IOException
     */
    void fillBuffer() throws IOException {
        int n = reader.read(buffer);
        if (n == -1) {
            return;
        }

        pos = 0;
        size = n;
    }
}
