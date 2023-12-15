package io.flowcore.api;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A simple pagination wrapper that does not depend on Spring Data.
 *
 * @param <T> the type of elements in this page
 */
public final class Page<T> {

    private final List<T> content;
    private final int pageNumber;
    private final int pageSize;
    private final long totalElements;
    private final int totalPages;

    /**
     * Constructs a new page.
     *
     * @param content       the list of elements on this page
     * @param pageNumber    zero-based page index
     * @param pageSize      the maximum number of elements per page
     * @param totalElements the total number of elements across all pages
     */
    public Page(List<T> content, int pageNumber, int pageSize, long totalElements) {
        this.content = List.copyOf(content);
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
    }

    /** @return the elements on this page (unmodifiable) */
    public List<T> getContent() { return content; }

    /** @return zero-based page index */
    public int getPageNumber() { return pageNumber; }

    /** @return maximum number of elements per page */
    public int getPageSize() { return pageSize; }

    /** @return total number of elements across all pages */
    public long getTotalElements() { return totalElements; }

    /** @return total number of pages */
    public int getTotalPages() { return totalPages; }

    /** @return {@code true} if this is the first page */
    public boolean isFirst() { return pageNumber == 0; }

    /** @return {@code true} if this is the last page */
    public boolean isLast() { return pageNumber >= totalPages - 1; }

    /** @return {@code true} when the page has no elements */
    public boolean isEmpty() { return content.isEmpty(); }

    /**
     * Maps the content of this page to a new type.
     *
     * @param mapper the mapping function
     * @param <U>    the target type
     * @return a new page with mapped content
     */
    public <U> Page<U> map(Function<T, U> mapper) {
        return new Page<>(
                content.stream().map(mapper).collect(Collectors.toList()),
                pageNumber,
                pageSize,
                totalElements
        );
    }

    @Override
    public String toString() {
        return "Page{pageNumber=" + pageNumber +
                ", pageSize=" + pageSize +
                ", totalElements=" + totalElements +
                ", totalPages=" + totalPages +
                ", contentSize=" + content.size() + '}';
    }
}
