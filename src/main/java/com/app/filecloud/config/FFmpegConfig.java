package com.app.filecloud.config;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FFmpegConfig {

    // Nếu đã cài vào PATH thì chỉ cần để tên file
    // Nếu chưa, hãy điền đường dẫn tuyệt đối (VD: "C:/ffmpeg/bin/ffmpeg.exe")
    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ffprobe.path:ffprobe}")
    private String ffprobePath;

    @Bean
    public FFmpeg ffmpeg() throws IOException {
        return new FFmpeg(ffmpegPath);
    }

    @Bean
    public FFprobe ffprobe() throws IOException {
        return new FFprobe(ffprobePath);
    }
}