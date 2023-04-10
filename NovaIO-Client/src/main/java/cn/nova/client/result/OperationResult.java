package cn.nova.client.result;

/**
 * {@link OperationResult}封装了简单的boolean类型的响应结果
 *
 * @author RealDragonking
 */
public class OperationResult {

    private final boolean isSuccess;

    public OperationResult(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    /**
     * @return 本次操作是否成功
     */
    public boolean isSuccess() {
        return this.isSuccess;
    }

}
