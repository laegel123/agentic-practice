package com.legacy.shop.core.web;

/**
 * 페이징 요청. page 는 1부터 시작한다.
 */
public class PageRequestDto {

    private int page = 1;  // 1-based
    private int size = 20;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /** 조회 시작 위치. */
    public int getOffset() {
        return page * size;
    }
}
