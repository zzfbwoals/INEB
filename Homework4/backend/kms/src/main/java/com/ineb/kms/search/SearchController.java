package com.ineb.kms.search;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.search.dto.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 통합 검색 — q 필수, type ALL|KEY|USER|NOTICE|AUDIT (기본 ALL). 조회 전용 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(@RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "ALL") String type) {
        return ApiResponse.ok(searchService.search(q, type));
    }
}
