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

    /**
     * 조회 시작 위치. page 는 1-based 이므로 첫 페이지(page=1)의 offset 은 0 이다.
     *
     * <p>page 가 1 미만(0·음수)이면 첫 페이지로 정규화해 offset 을 0 으로 클램프한다.
     * setPage 가 임의 값을 받기 때문에( {@code @RequestParam(defaultValue="0")} 등 ) 정규화 없이
     * {@code (page-1)*size} 를 그대로 쓰면 음수 offset 이 나와 호출부의 subList 등에서 터진다(B5 회귀 차단).
     */
    public int getOffset() {
        int normalizedPage = Math.max(1, page);
        return (normalizedPage - 1) * size;
    }
}
