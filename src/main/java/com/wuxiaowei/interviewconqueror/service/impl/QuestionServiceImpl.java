package com.wuxiaowei.interviewconqueror.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuxiaowei.interviewconqueror.common.ErrorCode;
import com.wuxiaowei.interviewconqueror.constant.CommonConstant;
import com.wuxiaowei.interviewconqueror.exception.BusinessException;
import com.wuxiaowei.interviewconqueror.exception.ThrowUtils;
import com.wuxiaowei.interviewconqueror.mapper.QuestionMapper;
import com.wuxiaowei.interviewconqueror.model.dto.question.QuestionEsDTO;
import com.wuxiaowei.interviewconqueror.model.dto.question.QuestionQueryRequest;
import com.wuxiaowei.interviewconqueror.model.entity.Question;
import com.wuxiaowei.interviewconqueror.model.entity.QuestionBankQuestion;
import com.wuxiaowei.interviewconqueror.model.entity.User;
import com.wuxiaowei.interviewconqueror.model.vo.QuestionVO;
import com.wuxiaowei.interviewconqueror.model.vo.UserVO;
import com.wuxiaowei.interviewconqueror.service.QuestionBankQuestionService;
import com.wuxiaowei.interviewconqueror.service.QuestionService;
import com.wuxiaowei.interviewconqueror.service.UserService;
import com.wuxiaowei.interviewconqueror.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    /**
     * 校验数据
     *
     * @param question
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        ThrowUtils.throwIf(question == null, ErrorCode.PARAMS_ERROR);
        // todo 从对象中取值
        String title = question.getTitle();
        // 创建数据时，参数不能为空
        if (add) {
            // todo 补充校验规则
            ThrowUtils.throwIf(StringUtils.isBlank(title), ErrorCode.PARAMS_ERROR);
        }
        // 修改数据时，有参数则校验
        // todo 补充校验规则
        if (StringUtils.isNotBlank(title)) {
            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionQueryRequest.getId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String answer = questionQueryRequest.getAnswer();
        String searchText = questionQueryRequest.getSearchText();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();
        List<String> tagList = questionQueryRequest.getTagList();
        Long userId = questionQueryRequest.getUserId();
        // todo 补充需要的查询条件
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("title", searchText).
                    or().like("content", searchText).
                    or().like("answer", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 精确查询
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        // 对象转封装类
        QuestionVO questionVO = QuestionVO.objToVo(question);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUser(userVO);

        return questionVO;
    }

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollUtil.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            return QuestionVO.objToVo(question);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    /**
     * 分页获取题目
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> listQuestionPage(QuestionQueryRequest questionQueryRequest) {

        long current = questionQueryRequest.getCurrent();
        int pageSize = questionQueryRequest.getPageSize();

        // 1. 获取queryWrapper
        QueryWrapper<Question> queryWrapper = this.getQueryWrapper(questionQueryRequest);
        // 2. 判断是否要根据题库id查询
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        if(questionBankId != null){
            // 3. 查询所关联的题目id
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .select(QuestionBankQuestion::getQuestionId)
                    .eq(QuestionBankQuestion::getQuestionBankId, questionBankId);
            List<QuestionBankQuestion> list = questionBankQuestionService.list(lambdaQueryWrapper);
            if(CollUtil.isNotEmpty(list)){
                Set<Long> questionId = list.stream().map(QuestionBankQuestion::getQuestionId).collect(Collectors.toSet());
                queryWrapper.in("id", questionId);
            }
        }
        // 3. 根据标签查询题目id
        if(CollUtil.isNotEmpty(questionQueryRequest.getTagList())){
            List<String> tagList = questionQueryRequest.getTagList();
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        return this.page(new Page<>(current, pageSize), queryWrapper);
    }

    /**
     * 从es查询题目
     * @param questionQueryRequest
     * @return
     */
    @Override
    public Page<Question> searchFromEs(QuestionQueryRequest questionQueryRequest) {
        // 获取参数
        Long id = questionQueryRequest.getId();
        String searchText = questionQueryRequest.getSearchText();
        List<String> tags = questionQueryRequest.getTagList();
        Long questionBankId = questionQueryRequest.getQuestionBankId();
        Long userId = questionQueryRequest.getUserId();
        // 注意，ES 的起始页为 0
        int current = questionQueryRequest.getCurrent() - 1;
        int pageSize = questionQueryRequest.getPageSize();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        // 构造查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 过滤
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
        if (id != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("id", id));
        }
        if (userId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", userId));
        }
        if (questionBankId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("questionBankId", questionBankId));
        }
        // 必须包含所有标签
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
            }
        }
        // 按关键词检索
        if (StringUtils.isNotBlank(searchText)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("answer", searchText));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 排序
        SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
        if (StringUtils.isNotBlank(sortField)) {
            sortBuilder = SortBuilders.fieldSort(sortField);
            sortBuilder.order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.ASC : SortOrder.DESC);
        }
        // 分页
        PageRequest pageRequest = PageRequest.of(current, pageSize);
        // 构造查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(pageRequest)
                .withSorts(sortBuilder)
                .build();
        SearchHits<QuestionEsDTO> searchHits = elasticsearchRestTemplate.search(searchQuery, QuestionEsDTO.class);
        // 复用 MySQL 的分页对象，封装返回结果
        Page<Question> page = new Page<>();
        page.setTotal(searchHits.getTotalHits());
        List<Question> resourceList = new ArrayList<>();
        if (searchHits.hasSearchHits()) {
            List<SearchHit<QuestionEsDTO>> searchHitList = searchHits.getSearchHits();
            for (SearchHit<QuestionEsDTO> questionEsDTOSearchHit : searchHitList) {
                resourceList.add(QuestionEsDTO.dtoToObj(questionEsDTOSearchHit.getContent()));
            }
        }
        page.setRecords(resourceList);
        return page;
    }

    /**
     * 批量删除题目
     * @param questionIdList
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteQuestions(List<Long> questionIdList) {
        if (CollUtil.isEmpty(questionIdList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "要删除的题目列表为空");
        }

        // 删除题目
        boolean result = this.removeBatchByIds(questionIdList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题库失败");

        // 移除题目题库关系
        List<Long> validQuestionIdList = questionBankQuestionService.listObjs(Wrappers.lambdaQuery(QuestionBankQuestion.class)
                        .select(QuestionBankQuestion::getQuestionId)
                        .in(QuestionBankQuestion::getQuestionId, questionIdList)
                , obj -> (Long) obj);
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                .in(QuestionBankQuestion::getQuestionId, validQuestionIdList);
        result = questionBankQuestionService.remove(lambdaQueryWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除题目题库关联失败");
    }
}
