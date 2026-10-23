package com.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.api.dto.AvatarBindDTO;
import com.example.api.dto.AvatarBindRequest;
import com.example.api.user.UserClient;
import com.example.common.observability.BusinessMetrics;
import com.example.entity.dto.StoreImage;
import com.example.mapper.ImageStoreMapper;
import com.example.service.ImageService;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import io.minio.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class ImageServiceImpl extends ServiceImpl<ImageStoreMapper, StoreImage> implements ImageService {

    @Resource
    MinioClient client;
    @Resource
    FlowUtils flowUtils;
    @Resource
    UserClient userClient;
    @Resource
    BusinessMetrics metrics;

    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
    @Override
    public String uploadAvatar(MultipartFile file, int id) throws IOException {
        var sample = metrics.start();
        String imageName = UUID.randomUUID().toString().replace("-","");
        imageName = "/avatar/"+imageName;
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket("study")
                .stream(file.getInputStream(),file.getSize(),-1)
                .object(imageName)
                .build();
        try {
            client.putObject(args);
            AvatarBindDTO bind = userClient.bindAvatar(id, new AvatarBindRequest(imageName));
            this.deleteOldAvatar(bind == null ? null : bind.oldAvatar());
            metrics.increment("forum.media.upload", "kind", "avatar", "result", "success");
            metrics.record("forum.media.upload.bytes", file.getSize(), "kind", "avatar");
            return imageName;
        }catch (Exception e){
            metrics.increment("forum.media.upload", "kind", "avatar", "result", "failure");
            log.error("头像上传/绑定失败，尝试清理新文件: {}", e.getMessage(), e);
            try {
                client.removeObject(RemoveObjectArgs.builder()
                        .bucket("study")
                        .object(imageName)
                        .build());
            } catch (Exception cleanup) {
                log.warn("清理未绑定头像失败: {}", imageName, cleanup);
            }
            return null;
        } finally {
            metrics.stop(sample, "forum.media.upload.duration", "kind", "avatar");
        }
    }

    @Override
    public String uploadImage(MultipartFile file, int id) throws IOException {
        String key = Const.FORUM_IMAGE_COUNT + id;
        if (!flowUtils.limitPeriodCounterCheck(key,20,3600)) {
            metrics.increment("forum.media.upload", "kind", "cache", "result", "rate_limited");
            return null;
        }
        var sample = metrics.start();
        String imageName = UUID.randomUUID().toString().replace("-","");
        Date date=new Date();
        imageName = "/cache/"+ format.format(date)+"/"+imageName;
        PutObjectArgs args = PutObjectArgs.builder()
                .bucket("study")
                .stream(file.getInputStream(),file.getSize(),-1)
                .object(imageName)
                .build();
        try {
            client.putObject(args);
            if (this.save(new StoreImage(id,imageName,date))){
                metrics.increment("forum.media.upload", "kind", "cache", "result", "success");
                metrics.record("forum.media.upload.bytes", file.getSize(), "kind", "cache");
                return imageName;
            }else {
                metrics.increment("forum.media.upload", "kind", "cache", "result", "failure");
                return null;
            }
        }catch (Exception e){
            metrics.increment("forum.media.upload", "kind", "cache", "result", "failure");
            log.error("图片上传出现问题："+e.getMessage());
            return null;
        } finally {
            metrics.stop(sample, "forum.media.upload.duration", "kind", "cache");
        }
    }

    @Override
    public void fetchImageFromMinio(OutputStream stream, String image) throws Exception{
        var sample = metrics.start();
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket("study")
                    .object(image)
                    .build();
            GetObjectResponse resp = client.getObject(args);
            IOUtils.copy(resp,stream);
            metrics.increment("forum.media.fetch", "result", "success");
        } catch (Exception e) {
            metrics.increment("forum.media.fetch", "result", "failure");
            throw e;
        } finally {
            metrics.stop(sample, "forum.media.fetch.duration");
        }
    }

    public void deleteOldAvatar(String avatar) throws Exception {
        if (avatar == null|| avatar.isEmpty()) return;
        RemoveObjectArgs remove = RemoveObjectArgs.builder()
                .bucket("study")
                .object(avatar)
                .build();
        client.removeObject(remove);
    }
}
