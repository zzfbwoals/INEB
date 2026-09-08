package com.ineb.kms.repository;

import com.ineb.kms.domain.NoticeFile;
import com.ineb.kms.notice.dto.NoticeFileMeta;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 첨부파일 저장소. enc_data(암호문, 최대 수십 MB)는 다운로드(findById)에서만 읽고,
 * 목록·상세·삭제는 메타 프로젝션과 JPQL 로 처리한다 (@Basic(LAZY)는 바이트코드 인핸스먼트 없이는 무시되므로 쓰지 않는다).
 */
public interface NoticeFileRepository extends JpaRepository<NoticeFile, Long> {

    String META = "select new com.ineb.kms.notice.dto.NoticeFileMeta("
            + "f.id, f.notice.id, f.originalName, f.contentType, f.fileSize, f.encVer, f.createdAt) from NoticeFile f";

    @Query(META + " where f.notice.id = :noticeId order by f.id")
    List<NoticeFileMeta> findMetaByNoticeId(@Param("noticeId") Long noticeId);

    @Query(META + " where f.id = :id")
    Optional<NoticeFileMeta> findMetaById(@Param("id") Long id);

    /** 페이지 내 공지들의 첨부 개수 — [noticeId, count] 행 목록 */
    @Query("select f.notice.id, count(f) from NoticeFile f where f.notice.id in :ids group by f.notice.id")
    List<Object[]> countByNoticeIds(@Param("ids") Collection<Long> ids);

    long countByNotice_Id(Long noticeId);

    @Modifying
    @Query("delete from NoticeFile f where f.notice.id = :noticeId")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);

    @Modifying
    @Query("delete from NoticeFile f where f.id = :id")
    int deleteByIdJpql(@Param("id") Long id);
}
