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

    /** 조회 시작 위치. page 는 1-based 이므로 첫 페이지(page=1)의 offset 은 0 이다. */
    public int getOffset() {
        return (page - 1) * size;
    }
}
