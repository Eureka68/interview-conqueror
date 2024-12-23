package com.wuxiaowei.interviewconqueror.job.once;


import cn.hutool.core.collection.CollUtil;
import com.wuxiaowei.interviewconqueror.esdao.QuestionEsDao;
import com.wuxiaowei.interviewconqueror.model.dto.question.QuestionEsDTO;
import com.wuxiaowei.interviewconqueror.model.entity.Question;
import com.wuxiaowei.interviewconqueror.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

// 注释掉取消任务
@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {

    @Resource
    QuestionService questionService;

    @Resource
    QuestionEsDao questionEsDao;

    @Override
    public void run(String... args) throws Exception {

        // 查询全量的question
        List<Question> list = questionService.list();
        if(CollUtil.isEmpty(list)){
            return;
        }

        // 转为Es实体类
        List<QuestionEsDTO> questionEsDTOList = list.stream().
                map(QuestionEsDTO::objToDto).
                collect(Collectors.toList());

        // 分页批量插入到Es
        final int pageSize = 500;
        int total = questionEsDTOList.size();
        log.info("FullSyncQuestionToEs start, total {}", total);
        for(int i = 0; i < total; i += pageSize){
            int end = Math.min(i + pageSize, total);
            log.info("syn from {}, end {}", i, end);
            questionEsDao.saveAll(questionEsDTOList.subList(i, end));
        }
        log.info("FullSyncQuestionToEs end, total {}", total);
    }
}
