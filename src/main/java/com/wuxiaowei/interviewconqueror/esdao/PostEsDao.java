package com.wuxiaowei.interviewconqueror.esdao;

import com.wuxiaowei.interviewconqueror.model.dto.post.PostEsDTO;
import java.util.List;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 帖子 ES 操作
 *
 * @author <a href="https://github.com/liwuxiaowei">程序员鱼皮</a>
 * @from <a href="https://wuxiaowei.icu">编程导航知识星球</a>
 */
public interface PostEsDao extends ElasticsearchRepository<PostEsDTO, Long> {

    List<PostEsDTO> findByUserId(Long userId);
}