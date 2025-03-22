package com.wuxiaowei.interviewconqueror.model.dto.question;

import com.wuxiaowei.interviewconqueror.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 查询题目请求
 *
  
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 推荐答案
     */
    private String answer;

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 题库 id
     */
    private Long questionBankId;

    private static final long serialVersionUID = 1L;
}