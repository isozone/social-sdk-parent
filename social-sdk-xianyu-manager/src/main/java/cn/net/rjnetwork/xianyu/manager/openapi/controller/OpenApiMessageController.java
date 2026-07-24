package cn.net.rjnetwork.xianyu.manager.openapi.controller;

import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiResponse;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiContext;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiException;
import cn.net.rjnetwork.xianyu.manager.openapi.common.OpenApiErrorCode;
import cn.net.rjnetwork.xianyu.manager.openapi.dto.OpenApiMessageVO;
import cn.net.rjnetwork.xianyu.manager.openapi.model.OpenApp;
import cn.net.rjnetwork.xianyu.manager.openapi.service.OpenAppService;
import cn.net.rjnetwork.xianyu.manager.message.mapper.MessageMapper;
import cn.net.rjnetwork.xianyu.manager.message.model.XianyuMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 消息域对外接口：列表按绑定白名单过滤，详情做账号作用域校验。
 */
@RestController
@RequestMapping("/openapi/v1/messages")
public class OpenApiMessageController {

    private final MessageMapper messageMapper;
    private final OpenAppService openAppService;

    public OpenApiMessageController(MessageMapper messageMapper, OpenAppService openAppService) {
        this.messageMapper = messageMapper;
        this.openAppService = openAppService;
    }

    @GetMapping
    public OpenApiResponse<List<OpenApiMessageVO>> list(@RequestParam(required = false) Long accountId) {
        OpenApp app = OpenApiContext.getOpenApp();
        openAppService.assertAccountAccessible(app, accountId);

        // 修复 Bug：原实现 messageMapper.selectList(new LambdaQueryWrapper<>()) 会把整张 xianyu_message
        // 表加载到内存再在 Java 层 filter，消息量大时 OOM；且 assertAccountAccessible 只校验传入的 accountId，
        // 若调用方不传 accountId 则返回所有账号消息（越权）。
        // 修复：把过滤条件下推到 SQL —— bound 为空返回空（应用未绑定任何账号），否则用 IN 限定账号范围，
        // accountId 非空时再 AND 一次。
        Set<Long> bound = openAppService.getBoundAccountIds(app);
        if (bound.isEmpty()) {
            return OpenApiResponse.ok(List.of());
        }
        LambdaQueryWrapper<XianyuMessage> wrapper = new LambdaQueryWrapper<XianyuMessage>()
                .in(XianyuMessage::getAccountId, bound)
                .orderByDesc(XianyuMessage::getMessageTime);
        if (accountId != null) {
            if (!bound.contains(accountId)) {
                return OpenApiResponse.ok(List.of());
            }
            wrapper.eq(XianyuMessage::getAccountId, accountId);
        }
        // 限制最多返回 200 条，防止极端数据量拖垮接口
        wrapper.last("LIMIT 200");
        List<OpenApiMessageVO> result = messageMapper.selectList(wrapper).stream()
                .map(this::toVo)
                .toList();
        return OpenApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public OpenApiResponse<OpenApiMessageVO> get(@PathVariable Long id) {
        OpenApp app = OpenApiContext.getOpenApp();
        XianyuMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new OpenApiException(OpenApiErrorCode.NOT_FOUND, "消息不存在");
        }
        openAppService.assertAccountAccessible(app, message.getAccountId());
        return OpenApiResponse.ok(toVo(message));
    }

    private OpenApiMessageVO toVo(XianyuMessage m) {
        OpenApiMessageVO vo = new OpenApiMessageVO();
        vo.setId(m.getId());
        vo.setAccountId(m.getAccountId());
        vo.setSessionId(m.getSessionId());
        vo.setMsgId(m.getMsgId());
        vo.setSenderId(m.getSenderId());
        vo.setSenderName(m.getSenderName());
        vo.setContent(m.getContent());
        vo.setMsgType(m.getMsgType());
        vo.setDirection(m.getDirection());
        vo.setAutoReply(m.getAutoReply());
        vo.setMessageTime(m.getMessageTime());
        vo.setCreatedAt(m.getCreatedAt());
        vo.setUpdatedAt(m.getUpdatedAt());
        return vo;
    }
}
