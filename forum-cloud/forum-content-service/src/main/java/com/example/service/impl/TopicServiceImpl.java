package com.example.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.api.dto.NotificationCreateRequest;
import com.example.api.dto.ProfileDTO;
import com.example.api.user.UserClient;
import com.example.common.observability.BusinessMetrics;
import com.example.entity.dto.Interact;
import com.example.entity.dto.Topic;
import com.example.entity.dto.TopicComment;
import com.example.entity.dto.TopicType;
import com.example.entity.vo.request.AddCommentVO;
import com.example.entity.vo.request.TopicCreateVO;
import com.example.entity.vo.request.TopicUpdateVO;
import com.example.entity.vo.response.CommentVO;
import com.example.entity.vo.response.TopicDetailVO;
import com.example.entity.vo.response.TopicPreviewVO;
import com.example.entity.vo.response.TopicTopVO;
import com.example.mapper.TopicCommentMapper;
import com.example.mapper.TopicMapper;
import com.example.mapper.TopicTypeMapper;
import com.example.service.TopicService;
import com.example.utils.CacheUtils;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.example.utils.Const.FORUM_TOPIC_CREATE_COUNT;

@Slf4j
@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements TopicService {

    @Resource
    TopicTypeMapper mapper;
    @Resource
    FlowUtils utils;
    @Resource
    CacheUtils cacheUtils;
    @Resource
    UserClient userClient;
    @Resource
    StringRedisTemplate template;
    @Resource
    TopicCommentMapper topicCommentMapper;
    @Resource
    AmqpTemplate amqpTemplate;
    @Resource
    BusinessMetrics metrics;

    private  Set<Integer> types =null;
    @PostConstruct
    private void initTypes(){
        types= this.listTypes()
                .stream()
                .map(TopicType::getId)
                .collect(Collectors.toSet());
    }


    @Override
    public List<TopicType> listTypes() {
        return mapper.selectList(null);
    }

    @Override
    public String createTopic(int uid, TopicCreateVO vo) {
        if (!textLimitCheck(vo.getContent(),10000)) {
            metrics.increment("forum.topic.created", "type", String.valueOf(vo.getType()), "result", "failure");
            return "文章内容太多！";
        }
        if (!types.contains(vo.getType())) {
            metrics.increment("forum.topic.created", "type", String.valueOf(vo.getType()), "result", "failure");
            return "文章类型非法!";
        }
        String key  = FORUM_TOPIC_CREATE_COUNT+uid;
        if (!utils.limitPeriodCounterCheck(key,3,3600)) {
            metrics.increment("forum.topic.created", "type", String.valueOf(vo.getType()), "result", "failure");
            return "发文频繁！稍后再试";
        }
        Topic topic = new Topic();
        BeanUtils.copyProperties(vo,topic);
        topic.setContent(vo.getContent().toJSONString());
        topic.setUid(uid);
        topic.setTime(new Date());
        if (this.save(topic)) {
            cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
            metrics.increment("forum.topic.created", "type", String.valueOf(vo.getType()), "result", "success");
            return null;
        }
        else {
            metrics.increment("forum.topic.created", "type", String.valueOf(vo.getType()), "result", "failure");
            return "内部错误！请联系管理员";
        }
    }

    @Override
    public List<TopicPreviewVO> listTopicByPage(int pageNumber, int type) {
        String key = Const.FORUM_TOPIC_PREVIEW_CACHE + pageNumber + type;
        List<TopicPreviewVO> list = cacheUtils.takeListFromCache(key,TopicPreviewVO.class);
        if(list  != null) {
            metrics.increment("forum.topic.cache", "scope", "list", "result", "hit");
            metrics.increment("forum.topic.listed", "type", String.valueOf(type), "result", "success");
            return list;
        }
        metrics.increment("forum.topic.cache", "scope", "list", "result", "miss");
        Page<Topic> page = Page.of(pageNumber,10);
        if (type == 0)
            baseMapper.selectPage(page,Wrappers.<Topic>query().orderByDesc("time"));
        else
            baseMapper.selectPage(page,Wrappers.<Topic>query().eq("type",type).orderByDesc("time"));
        List<Topic> topics = page.getRecords();
        if (topics.isEmpty()) {
            metrics.increment("forum.topic.listed", "type", String.valueOf(type), "result", "empty");
            return null;
        }
        Map<Integer, ProfileDTO> profiles = fetchProfiles(
                topics.stream().map(Topic::getUid).toList());
        list = topics.stream()
                .map(topic -> resolveTOPreview(topic, profiles.get(topic.getUid())))
                .toList();
        cacheUtils.saveListToCache(key,list,60);
        metrics.increment("forum.topic.listed", "type", String.valueOf(type), "result", "success");
        return list;
    }

    @Override
    public List<TopicTopVO> listTopTopics() {
        List<Topic> topics = baseMapper.selectList(Wrappers.<Topic>query()
                .select("id","title","time")
                .eq("top",1));
        return topics.stream().map(topic -> {
            TopicTopVO vo = new TopicTopVO();
            BeanUtils.copyProperties(topic,vo);
            return vo;
        }).toList();
    }

    @Override
    public TopicDetailVO getTopic(int tid,int uid) {
        TopicDetailVO vo = new TopicDetailVO();
        Topic topic = baseMapper.selectById(tid);
        if (topic == null) {
            metrics.increment("forum.topic.viewed", "result", "not_found");
            return null;
        }
        metrics.increment("forum.topic.viewed", "result", "success", "type", String.valueOf(topic.getType()));
        BeanUtils.copyProperties(topic,vo);
        TopicDetailVO.Interact interact = new TopicDetailVO.Interact(
                hasInteract(tid,uid,"like"),
                hasInteract(tid,uid,"collect")
        );
        vo.setInteract(interact);
        vo.setUser(toTopicUser(fetchProfile(topic.getUid()), topic.getUid()));
        vo.setComments(topicCommentMapper.selectCount(Wrappers.<TopicComment>query().eq("tid",tid)));
        return vo;
    }

    @Override
    public void interact(Interact interact, boolean state) {
        String type = interact.getType();
        synchronized (type.intern()) {
            template.opsForHash().put(type, interact.toKey(), Boolean.toString(state));
            this.saveInteractSchedule(type);
            metrics.increment("forum.interaction", "type", type, "action", state ? "add" : "remove");
        }
    }

    @Override
    public List<TopicPreviewVO> listTopicCollects(int uid) {
        return baseMapper.collectTopics(uid)
                .stream()
                .map(topic -> {
                    TopicPreviewVO vo = new TopicPreviewVO();
                    BeanUtils.copyProperties(topic,vo);
                    return vo;
                })
                .toList();
    }

    @Override
    public String updateTopic(int uid, TopicUpdateVO vo) {
        if (!textLimitCheck(vo.getContent(),10000)) {
            metrics.increment("forum.topic.updated", "result", "failure");
            return "文章内容太多！";
        }
        if (!types.contains(vo.getType())) {
            metrics.increment("forum.topic.updated", "result", "failure");
            return "文章类型非法!";
        }
        baseMapper.update(null,Wrappers.<Topic>update()
                .eq("uid",uid)
                .eq("id",vo.getId())
                .set("title",vo.getTitle())
                .set("content",vo.getContent().toString())
                .set("type",vo.getType())
        );
        cacheUtils.deleteCachePattern(Const.FORUM_TOPIC_PREVIEW_CACHE + "*");
        metrics.increment("forum.topic.updated", "result", "success");
        return null;
    }

    @Override
    public String createComment(int uid, AddCommentVO vo) {
        if (!textLimitCheck(JSONObject.parseObject(vo.getContent()),1000)) {
            metrics.increment("forum.comment.created", "result", "failure");
            return "评论内容太多！";
        }
        String key  =Const.FORUM_TOPIC_COMMENT_COUNTER+uid;
        if (!utils.limitPeriodCounterCheck(key,5,60)) {
            metrics.increment("forum.comment.created", "result", "failure");
            return "评论发表频繁！稍后再试";
        }
        TopicComment comment = new TopicComment();
        comment.setUid(uid);
        BeanUtils.copyProperties(vo,comment);
        comment.setTime(new Date());
        topicCommentMapper.insert(comment);
        Topic topic = baseMapper.selectById(vo.getTid());
        ProfileDTO actor = fetchProfile(uid);
        String actorName = actor == null ? "用户" + uid : actor.username();
        if (vo.getQuote() > 0){
            TopicComment comm = topicCommentMapper.selectById(vo.getQuote());
            if (comm != null && !Objects.equals(uid, comm.getUid())){
                notify(comm.getUid(),
                        "新的帖子评论回复",
                        actorName + "回复了你的评论",
                        "success","/index/topic-detail/"+comm.getTid());
            }
        }else if (topic != null && !Objects.equals(uid, topic.getUid())) {
            notify(topic.getUid(),
                    "您有新的帖子回复",
                    actorName+" 回复了你发表主题: "+topic.getTitle()+"，快去看看吧！",
                    "success", "/index/topic-detail/"+topic.getId());
        }
        metrics.increment("forum.comment.created", "result", "success");
        return null;
    }

    @Override
    public List<CommentVO> comments(int tid, int pageNumber) {
        Page<TopicComment> page = Page.of(pageNumber,10);
        topicCommentMapper.selectPage(page,Wrappers.<TopicComment>query().eq("tid",tid));
        List<TopicComment> records = page.getRecords();
        Map<Integer, ProfileDTO> profiles = fetchProfiles(
                records.stream().map(TopicComment::getUid).toList());
        return records.stream().map(dto->{
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(dto,vo);
            if (dto.getQuote()>0){
                TopicComment comment = topicCommentMapper.selectOne(
                        Wrappers.<TopicComment>query().eq("id", dto.getQuote()).orderByAsc("time"));
                if (comment!=null){
                    JSONObject object = JSONObject.parseObject(comment.getContent());
                    StringBuilder builder = new StringBuilder();
                    this.shortContent(object.getJSONArray("ops"),builder,(ignore)->{});
                    vo.setQuote(builder.toString());
                }else {
                    vo.setQuote("此评论已被删除");
                }

            }
            vo.setUser(toCommentUser(profiles.get(dto.getUid()), dto.getUid()));
            return vo;
        }).toList();
    }

    @Override
    public void deleteComment(int id, int uid) {
        topicCommentMapper.delete(Wrappers.<TopicComment>query().eq("id",id).eq("uid",uid));
        metrics.increment("forum.comment.deleted", "result", "success");
    }

    private boolean hasInteract(int tid,int uid,String type){
        String key = tid + ":" + uid;
        if (template.opsForHash().hasKey(type,key))
            return Boolean.parseBoolean(template.opsForHash().entries(type).get(key).toString());
        return baseMapper.userInteractCount(tid, uid, type)>0;
    }

    private final Map<String, Boolean> state = new HashMap<>();
    ScheduledExecutorService service = Executors.newScheduledThreadPool(2);
    private void saveInteractSchedule(String type) {
        if(!state.getOrDefault(type, false)) {
            state.put(type, true);
            service.schedule(() -> {
                this.saveInteract(type);
                state.put(type, false);
            }, 3, TimeUnit.SECONDS);
        }
    }

    private void saveInteract(String type) {
        synchronized (type.intern()) {
            List<Interact> check = new LinkedList<>();
            List<Interact> uncheck = new LinkedList<>();
            template.opsForHash().entries(type).forEach((k, v) -> {
                if(Boolean.parseBoolean(v.toString()))
                    check.add(Interact.parseInteract(k.toString(), type));
                else
                    uncheck.add(Interact.parseInteract(k.toString(), type));
            });
            if(!check.isEmpty())
                baseMapper.addInteract(check, type);
            if(!uncheck.isEmpty())
                baseMapper.deleteInteract(uncheck, type);
            template.delete(type);
        }
    }


    private void notify(int uid, String title, String content, String type, String url) {
        try {
            amqpTemplate.convertAndSend("notification",
                    new NotificationCreateRequest(UUID.randomUUID().toString(),
                            uid, title, content, type, url));
            metrics.increment("forum.mq.publish", "event", "notification", "result", "success");
        } catch (Exception e) {
            metrics.increment("forum.mq.publish", "event", "notification", "result", "failure");
            log.warn("发送通知事件失败: uid={}", uid, e);
        }
    }

    private ProfileDTO fetchProfile(int uid) {
        try {
            return userClient.profile(uid);
        } catch (Exception e) {
            log.warn("获取用户资料失败: uid={}", uid, e);
            return null;
        }
    }

    private Map<Integer, ProfileDTO> fetchProfiles(Collection<Integer> uids) {
        List<Integer> ids = uids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return userClient.profiles(ids).stream()
                    .filter(profile -> profile.id() != null)
                    .collect(Collectors.toMap(ProfileDTO::id, profile -> profile, (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量获取用户资料失败: uids={}", ids, e);
            return Map.of();
        }
    }

    private TopicDetailVO.User toTopicUser(ProfileDTO profile, int uid) {
        TopicDetailVO.User user = new TopicDetailVO.User();
        user.setId(profile == null ? uid : profile.id());
        if (profile != null) {
            user.setUsername(profile.username());
            user.setAvatar(profile.avatar());
            user.setDesc(profile.desc());
            user.setGender(profile.gender());
            user.setQq(profile.qq());
            user.setWx(profile.wx());
            user.setPhone(profile.phone());
            user.setEmail(profile.email());
        }
        return user;
    }

    private CommentVO.User toCommentUser(ProfileDTO profile, int uid) {
        CommentVO.User user = new CommentVO.User();
        user.setId(profile == null ? uid : profile.id());
        if (profile != null) {
            user.setUsername(profile.username());
            user.setAvatar(profile.avatar());
            user.setGender(profile.gender());
            user.setQq(profile.qq());
            user.setWx(profile.wx());
            user.setPhone(profile.phone());
            user.setEmail(profile.email());
        }
        return user;
    }

    private TopicPreviewVO resolveTOPreview(Topic topic, ProfileDTO profile) {
        TopicPreviewVO vo = new TopicPreviewVO();
        if (profile != null) {
            vo.setUsername(profile.username());
            vo.setAvatar(profile.avatar());
        }
        BeanUtils.copyProperties(topic,vo);
        vo.setLike(baseMapper.interactCount(topic.getId(),"like"));
        vo.setCollect(baseMapper.interactCount(topic.getId(),"collect"));
        List<String> images = new ArrayList<>();
        StringBuilder preViewText = new  StringBuilder();
        JSONArray ops =JSONObject.parseObject(topic.getContent()).getJSONArray("ops");
        this.shortContent(ops,preViewText,obj -> images.add(obj.toString()));
        vo.setText(preViewText.length() > 300 ? preViewText.substring(0,300) : preViewText.toString());
        vo.setImages(images);
        return vo;
    }

    private void shortContent(JSONArray ops, StringBuilder preViewText, Consumer<Object> imageHandler){
        for (Object op : ops) {
            Object insert = JSONObject.from(op).get("insert");
            if (insert instanceof String text){
                if (preViewText.length() >= 300) continue;
                preViewText.append(text);
            }else if (insert instanceof Map<?,?> map){
                Optional.ofNullable(map.get("image"))
                        .ifPresent(imageHandler);
            }
        }
    }

    private boolean textLimitCheck(JSONObject object,int max){
        if (object == null) return false;
        long length = 0;
        for (Object op : object.getJSONArray("ops")) {
            length +=JSONObject.from(op).getString("insert").length();
            if(length>max)return false;
        }
        return true;
    }
}
