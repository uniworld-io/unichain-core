package org.unichain.core.db.common;

import java.util.List;

public class DataPage<V> {
    public int pageIndex;
    public int pageSize;
    public int total;
    public List<V> content;

    public DataPage(int pageIndex, int pageSize, int total, List<V> content) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.total = total;
        this.content = content;
    }
}
