-- phpMyAdmin SQL Dump
-- version 5.2.3
-- https://www.phpmyadmin.net/
--
-- Máy chủ: localhost
-- Thời gian đã tạo: Th1 13, 2026 lúc 03:23 AM
-- Phiên bản máy phục vụ: 10.4.32-MariaDB
-- Phiên bản PHP: 8.0.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Cơ sở dữ liệu: `filecloud_db`
--

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `collections`
--

CREATE TABLE `collections` (
  `id` int(11) NOT NULL,
  `user_id` char(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `slug` varchar(120) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `cover_image_id` char(36) DEFAULT NULL,
  `is_public` tinyint(1) DEFAULT 0,
  `is_system` tinyint(1) DEFAULT 0,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `collection_items`
--

CREATE TABLE `collection_items` (
  `collection_id` int(11) NOT NULL,
  `file_id` char(36) NOT NULL,
  `added_at` datetime DEFAULT current_timestamp(),
  `sort_order` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `content_subjects`
--

CREATE TABLE `content_subjects` (
  `id` int(11) NOT NULL,
  `main_name` varchar(255) NOT NULL,
  `alias_name_1` varchar(255) DEFAULT NULL,
  `alias_name_2` varchar(255) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `storage_path` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `file_blurhash`
--

CREATE TABLE `file_blurhash` (
  `file_id` char(36) NOT NULL,
  `hash_string` varchar(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `file_nodes`
--

CREATE TABLE `file_nodes` (
  `id` char(36) NOT NULL,
  `volume_id` int(11) DEFAULT NULL,
  `parent_id` char(36) DEFAULT NULL,
  `owner_id` char(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `extension` varchar(20) DEFAULT NULL,
  `type` enum('FILE','FOLDER') NOT NULL,
  `size` bigint(20) DEFAULT 0,
  `mime_type` varchar(255) DEFAULT NULL,
  `thumb_strategy` enum('DEFAULT','CUSTOM_UPLOAD','USE_CHILD_FILE','USE_SUBJECT_AVATAR') DEFAULT 'DEFAULT',
  `custom_thumb_path` varchar(255) DEFAULT NULL,
  `thumb_source_file_id` char(36) DEFAULT NULL,
  `thumb_source_subject_id` int(11) DEFAULT NULL,
  `relative_path` varchar(255) DEFAULT NULL,
  `file_hash` varchar(255) DEFAULT NULL,
  `is_hidden` tinyint(1) DEFAULT 0,
  `is_trash` tinyint(1) DEFAULT 0,
  `created_at` datetime DEFAULT current_timestamp(),
  `modified_at` datetime DEFAULT NULL,
  `imported_at` datetime DEFAULT NULL,
  `subject_mapping_id` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `file_subjects`
--

CREATE TABLE `file_subjects` (
  `file_id` char(36) NOT NULL,
  `subject_id` int(11) NOT NULL,
  `is_main_owner` tinyint(1) DEFAULT 0,
  `role_in_content` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `file_tags`
--

CREATE TABLE `file_tags` (
  `id` int(11) NOT NULL,
  `file_id` char(36) NOT NULL,
  `tag_id` int(11) NOT NULL,
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `file_thumbnails`
--

CREATE TABLE `file_thumbnails` (
  `id` bigint(20) NOT NULL,
  `file_id` char(36) NOT NULL,
  `thumb_type` enum('SMALL','MEDIUM','LARGE') NOT NULL,
  `storage_path` varchar(255) NOT NULL,
  `file_size` int(11) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `manga_authors`
--

CREATE TABLE `manga_authors` (
  `id` int(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `biography` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `manga_chapters`
--

CREATE TABLE `manga_chapters` (
  `id` char(36) NOT NULL,
  `manga_id` char(36) NOT NULL,
  `chapter_name` varchar(255) NOT NULL,
  `folder_path` varchar(1000) NOT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `chapter_order` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `manga_pages`
--

CREATE TABLE `manga_pages` (
  `id` char(36) NOT NULL,
  `chapter_id` char(36) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `page_order` int(11) NOT NULL,
  `size` bigint(20) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `manga_series`
--

CREATE TABLE `manga_series` (
  `id` char(36) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `cover_path` varchar(500) DEFAULT NULL,
  `status` varchar(20) DEFAULT 'ONGOING',
  `release_year` varchar(10) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `manga_series_authors`
--

CREATE TABLE `manga_series_authors` (
  `manga_id` char(36) NOT NULL,
  `author_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `manga_tags`
--

CREATE TABLE `manga_tags` (
  `manga_id` char(36) NOT NULL,
  `tag_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `media_metadata`
--

CREATE TABLE `media_metadata` (
  `file_id` char(36) NOT NULL,
  `width` int(11) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `orientation` int(11) DEFAULT NULL,
  `taken_at` datetime DEFAULT NULL,
  `duration_seconds` int(11) DEFAULT NULL,
  `video_codec` varchar(20) DEFAULT NULL,
  `frame_rate` double DEFAULT NULL,
  `gps_lat` double DEFAULT NULL,
  `gps_lng` double DEFAULT NULL,
  `location_name` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chỉ lưu dữ liệu nếu file là Ảnh hoặc Video';

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movies`
--

CREATE TABLE `movies` (
  `id` char(36) NOT NULL,
  `title` varchar(255) NOT NULL COMMENT 'Tên chính của phim',
  `original_title` varchar(255) DEFAULT NULL COMMENT 'Tên gốc (VD: tên tiếng Nhật)',
  `release_year` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `cover_image_url` varchar(500) DEFAULT NULL,
  `rating` float DEFAULT 0 COMMENT 'Đánh giá (0.0 - 10.0)',
  `tmdb_id` varchar(50) DEFAULT NULL,
  `imdb_id` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `thumbnail_path` varchar(500) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movie_alternative_titles`
--

CREATE TABLE `movie_alternative_titles` (
  `id` char(36) NOT NULL,
  `movie_id` char(36) NOT NULL,
  `alt_title` varchar(255) NOT NULL,
  `language_code` varchar(10) DEFAULT NULL COMMENT 'VD: en, jp, vn, viettat'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movie_credits`
--

CREATE TABLE `movie_credits` (
  `movie_id` char(36) NOT NULL,
  `person_id` char(36) NOT NULL,
  `role` varchar(50) NOT NULL COMMENT 'Director, Actor, Writer...',
  `character_name` varchar(255) DEFAULT NULL COMMENT 'Tên nhân vật trong phim'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movie_episodes`
--

CREATE TABLE `movie_episodes` (
  `id` char(36) NOT NULL,
  `movie_id` char(36) NOT NULL,
  `title` varchar(255) DEFAULT NULL COMMENT 'Tên riêng của tập (nếu có)',
  `episode_number` int(11) NOT NULL DEFAULT 1,
  `file_path` varchar(500) NOT NULL COMMENT 'Đường dẫn lưu trữ thực tế',
  `file_size` bigint(20) DEFAULT 0,
  `duration_seconds` int(11) DEFAULT 0,
  `mime_type` varchar(100) DEFAULT 'video/mp4',
  `created_at` datetime DEFAULT current_timestamp(),
  `thumbnail_path` varchar(500) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movie_progress`
--

CREATE TABLE `movie_progress` (
  `id` char(36) NOT NULL,
  `user_id` char(36) NOT NULL COMMENT 'Liên kết bảng Users hiện có',
  `episode_id` char(36) NOT NULL,
  `stopped_at_seconds` int(11) DEFAULT 0 COMMENT 'Vị trí dừng lại (giây)',
  `is_finished` tinyint(1) DEFAULT 0 COMMENT 'Đánh dấu đã xem xong',
  `last_watched_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movie_studios`
--

CREATE TABLE `movie_studios` (
  `movie_id` varchar(36) NOT NULL,
  `studio_id` varchar(36) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movie_subtitles`
--

CREATE TABLE `movie_subtitles` (
  `id` char(36) NOT NULL,
  `episode_id` char(36) NOT NULL,
  `language_code` varchar(10) NOT NULL COMMENT 'vi, en, jp',
  `label` varchar(100) DEFAULT NULL COMMENT 'Hiển thị trên player (VD: Tiếng Việt)',
  `file_path` varchar(500) NOT NULL,
  `format` varchar(10) DEFAULT 'srt' COMMENT 'srt, ass, vtt'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `movie_tags`
--

CREATE TABLE `movie_tags` (
  `movie_id` char(36) NOT NULL,
  `tag_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `persons`
--

CREATE TABLE `persons` (
  `id` char(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `avatar_url` varchar(500) DEFAULT NULL,
  `birth_year` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `roles`
--

CREATE TABLE `roles` (
  `id` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `social_platforms`
--

CREATE TABLE `social_platforms` (
  `id` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `base_url` varchar(255) NOT NULL,
  `icon_url` varchar(255) DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `storage_volumes`
--

CREATE TABLE `storage_volumes` (
  `id` int(11) NOT NULL,
  `label` varchar(255) DEFAULT NULL,
  `mount_point` varchar(255) NOT NULL,
  `uuid` varchar(255) DEFAULT NULL,
  `total_capacity` bigint(20) DEFAULT NULL,
  `available_capacity` bigint(20) DEFAULT NULL,
  `status` enum('ONLINE','OFFLINE','MAINTENANCE') DEFAULT 'ONLINE',
  `last_scanned_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Quản lý các ổ cứng vật lý được gắn vào server';

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `studios`
--

CREATE TABLE `studios` (
  `id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL,
  `slug` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `subject_folder_mappings`
--

CREATE TABLE `subject_folder_mappings` (
  `id` int(11) NOT NULL,
  `subject_id` int(11) NOT NULL,
  `volume_id` int(11) NOT NULL,
  `relative_path` text NOT NULL,
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `subject_social_links`
--

CREATE TABLE `subject_social_links` (
  `id` bigint(20) NOT NULL,
  `subject_id` int(11) NOT NULL,
  `platform_id` int(11) NOT NULL,
  `profile_path` varchar(255) NOT NULL,
  `full_url_override` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `sys_configs`
--

CREATE TABLE `sys_configs` (
  `config_key` varchar(50) NOT NULL,
  `config_value` text DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `data_type` enum('STRING','INT','BOOL','JSON') NOT NULL,
  `is_system` tinyint(1) DEFAULT 0,
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `tags`
--

CREATE TABLE `tags` (
  `id` int(11) NOT NULL,
  `name` varchar(50) NOT NULL,
  `slug` varchar(60) NOT NULL,
  `color_hex` varchar(7) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `users`
--

CREATE TABLE `users` (
  `id` char(36) NOT NULL,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `storage_limit` bigint(20) DEFAULT 5368709120,
  `used_storage` bigint(20) DEFAULT 0,
  `is_active` tinyint(1) DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bảng người dùng đăng nhập hệ thống';

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `user_roles`
--

CREATE TABLE `user_roles` (
  `user_id` char(36) NOT NULL,
  `role_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Chỉ mục cho các bảng đã đổ
--

--
-- Chỉ mục cho bảng `collections`
--
ALTER TABLE `collections`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_col_user` (`user_id`),
  ADD KEY `fk_col_cover` (`cover_image_id`);

--
-- Chỉ mục cho bảng `collection_items`
--
ALTER TABLE `collection_items`
  ADD PRIMARY KEY (`collection_id`,`file_id`),
  ADD KEY `fk_ci_file` (`file_id`);

--
-- Chỉ mục cho bảng `content_subjects`
--
ALTER TABLE `content_subjects`
  ADD PRIMARY KEY (`id`);

--
-- Chỉ mục cho bảng `file_blurhash`
--
ALTER TABLE `file_blurhash`
  ADD PRIMARY KEY (`file_id`);

--
-- Chỉ mục cho bảng `file_nodes`
--
ALTER TABLE `file_nodes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_parent` (`parent_id`),
  ADD KEY `idx_name` (`name`),
  ADD KEY `fk_file_volume` (`volume_id`),
  ADD KEY `fk_file_owner` (`owner_id`),
  ADD KEY `fk_file_thumb_source` (`thumb_source_file_id`),
  ADD KEY `fk_file_thumb_subject` (`thumb_source_subject_id`),
  ADD KEY `fk_file_mapping` (`subject_mapping_id`);

--
-- Chỉ mục cho bảng `file_subjects`
--
ALTER TABLE `file_subjects`
  ADD PRIMARY KEY (`file_id`,`subject_id`),
  ADD KEY `fk_fs_subject` (`subject_id`);

--
-- Chỉ mục cho bảng `file_tags`
--
ALTER TABLE `file_tags`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_ft_file` (`file_id`),
  ADD KEY `fk_ft_tag` (`tag_id`);

--
-- Chỉ mục cho bảng `file_thumbnails`
--
ALTER TABLE `file_thumbnails`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_thumb_file` (`file_id`);

--
-- Chỉ mục cho bảng `manga_authors`
--
ALTER TABLE `manga_authors`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_author_name` (`name`);

--
-- Chỉ mục cho bảng `manga_chapters`
--
ALTER TABLE `manga_chapters`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_chapter_manga` (`manga_id`);

--
-- Chỉ mục cho bảng `manga_pages`
--
ALTER TABLE `manga_pages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_page_chapter_order` (`chapter_id`,`page_order`);

--
-- Chỉ mục cho bảng `manga_series`
--
ALTER TABLE `manga_series`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_manga_title` (`title`);

--
-- Chỉ mục cho bảng `manga_series_authors`
--
ALTER TABLE `manga_series_authors`
  ADD PRIMARY KEY (`manga_id`,`author_id`),
  ADD KEY `fk_msa_author` (`author_id`);

--
-- Chỉ mục cho bảng `manga_tags`
--
ALTER TABLE `manga_tags`
  ADD PRIMARY KEY (`manga_id`,`tag_id`),
  ADD KEY `fk_mt_tag` (`tag_id`);

--
-- Chỉ mục cho bảng `media_metadata`
--
ALTER TABLE `media_metadata`
  ADD PRIMARY KEY (`file_id`);

--
-- Chỉ mục cho bảng `movies`
--
ALTER TABLE `movies`
  ADD PRIMARY KEY (`id`);

--
-- Chỉ mục cho bảng `movie_alternative_titles`
--
ALTER TABLE `movie_alternative_titles`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_mat_movie` (`movie_id`);

--
-- Chỉ mục cho bảng `movie_credits`
--
ALTER TABLE `movie_credits`
  ADD PRIMARY KEY (`movie_id`,`person_id`,`role`),
  ADD KEY `idx_mc_person` (`person_id`);

--
-- Chỉ mục cho bảng `movie_episodes`
--
ALTER TABLE `movie_episodes`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_me_movie` (`movie_id`);

--
-- Chỉ mục cho bảng `movie_progress`
--
ALTER TABLE `movie_progress`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_user_episode` (`user_id`,`episode_id`),
  ADD KEY `fk_mp_episode` (`episode_id`);

--
-- Chỉ mục cho bảng `movie_studios`
--
ALTER TABLE `movie_studios`
  ADD PRIMARY KEY (`movie_id`,`studio_id`),
  ADD KEY `fk_movie_studios_studio` (`studio_id`);

--
-- Chỉ mục cho bảng `movie_subtitles`
--
ALTER TABLE `movie_subtitles`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_ms_episode` (`episode_id`);

--
-- Chỉ mục cho bảng `movie_tags`
--
ALTER TABLE `movie_tags`
  ADD PRIMARY KEY (`movie_id`,`tag_id`),
  ADD KEY `idx_mt_tag` (`tag_id`);

--
-- Chỉ mục cho bảng `persons`
--
ALTER TABLE `persons`
  ADD PRIMARY KEY (`id`);

--
-- Chỉ mục cho bảng `roles`
--
ALTER TABLE `roles`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `name` (`name`);

--
-- Chỉ mục cho bảng `social_platforms`
--
ALTER TABLE `social_platforms`
  ADD PRIMARY KEY (`id`);

--
-- Chỉ mục cho bảng `storage_volumes`
--
ALTER TABLE `storage_volumes`
  ADD PRIMARY KEY (`id`);

--
-- Chỉ mục cho bảng `studios`
--
ALTER TABLE `studios`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_studio_name` (`name`);

--
-- Chỉ mục cho bảng `subject_folder_mappings`
--
ALTER TABLE `subject_folder_mappings`
  ADD PRIMARY KEY (`id`),
  ADD KEY `subject_id` (`subject_id`),
  ADD KEY `volume_id` (`volume_id`);

--
-- Chỉ mục cho bảng `subject_social_links`
--
ALTER TABLE `subject_social_links`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_ssl_subject` (`subject_id`),
  ADD KEY `fk_ssl_platform` (`platform_id`);

--
-- Chỉ mục cho bảng `sys_configs`
--
ALTER TABLE `sys_configs`
  ADD PRIMARY KEY (`config_key`);

--
-- Chỉ mục cho bảng `tags`
--
ALTER TABLE `tags`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `slug` (`slug`);

--
-- Chỉ mục cho bảng `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `username` (`username`),
  ADD UNIQUE KEY `email` (`email`);

--
-- Chỉ mục cho bảng `user_roles`
--
ALTER TABLE `user_roles`
  ADD PRIMARY KEY (`user_id`,`role_id`),
  ADD KEY `fk_ur_role` (`role_id`);

--
-- AUTO_INCREMENT cho các bảng đã đổ
--

--
-- AUTO_INCREMENT cho bảng `collections`
--
ALTER TABLE `collections`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `content_subjects`
--
ALTER TABLE `content_subjects`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `file_tags`
--
ALTER TABLE `file_tags`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `file_thumbnails`
--
ALTER TABLE `file_thumbnails`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `manga_authors`
--
ALTER TABLE `manga_authors`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `roles`
--
ALTER TABLE `roles`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `social_platforms`
--
ALTER TABLE `social_platforms`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `storage_volumes`
--
ALTER TABLE `storage_volumes`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `subject_folder_mappings`
--
ALTER TABLE `subject_folder_mappings`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `subject_social_links`
--
ALTER TABLE `subject_social_links`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT cho bảng `tags`
--
ALTER TABLE `tags`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- Ràng buộc đối với các bảng kết xuất
--

--
-- Ràng buộc cho bảng `collections`
--
ALTER TABLE `collections`
  ADD CONSTRAINT `fk_col_cover` FOREIGN KEY (`cover_image_id`) REFERENCES `file_nodes` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_col_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `collection_items`
--
ALTER TABLE `collection_items`
  ADD CONSTRAINT `fk_ci_col` FOREIGN KEY (`collection_id`) REFERENCES `collections` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_ci_file` FOREIGN KEY (`file_id`) REFERENCES `file_nodes` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `file_blurhash`
--
ALTER TABLE `file_blurhash`
  ADD CONSTRAINT `fk_blur_file` FOREIGN KEY (`file_id`) REFERENCES `file_nodes` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `file_nodes`
--
ALTER TABLE `file_nodes`
  ADD CONSTRAINT `fk_file_mapping` FOREIGN KEY (`subject_mapping_id`) REFERENCES `subject_folder_mappings` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_file_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  ADD CONSTRAINT `fk_file_parent` FOREIGN KEY (`parent_id`) REFERENCES `file_nodes` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_file_thumb_source` FOREIGN KEY (`thumb_source_file_id`) REFERENCES `file_nodes` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_file_thumb_subject` FOREIGN KEY (`thumb_source_subject_id`) REFERENCES `content_subjects` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_file_volume` FOREIGN KEY (`volume_id`) REFERENCES `storage_volumes` (`id`) ON DELETE SET NULL;

--
-- Ràng buộc cho bảng `file_subjects`
--
ALTER TABLE `file_subjects`
  ADD CONSTRAINT `fk_fs_file` FOREIGN KEY (`file_id`) REFERENCES `file_nodes` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_fs_subject` FOREIGN KEY (`subject_id`) REFERENCES `content_subjects` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `file_tags`
--
ALTER TABLE `file_tags`
  ADD CONSTRAINT `fk_ft_file` FOREIGN KEY (`file_id`) REFERENCES `file_nodes` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_ft_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `file_thumbnails`
--
ALTER TABLE `file_thumbnails`
  ADD CONSTRAINT `fk_thumb_file` FOREIGN KEY (`file_id`) REFERENCES `file_nodes` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `manga_chapters`
--
ALTER TABLE `manga_chapters`
  ADD CONSTRAINT `fk_chapter_manga` FOREIGN KEY (`manga_id`) REFERENCES `manga_series` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `manga_pages`
--
ALTER TABLE `manga_pages`
  ADD CONSTRAINT `fk_page_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `manga_chapters` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `manga_series_authors`
--
ALTER TABLE `manga_series_authors`
  ADD CONSTRAINT `fk_msa_author` FOREIGN KEY (`author_id`) REFERENCES `manga_authors` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_msa_manga` FOREIGN KEY (`manga_id`) REFERENCES `manga_series` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `manga_tags`
--
ALTER TABLE `manga_tags`
  ADD CONSTRAINT `fk_mt_manga` FOREIGN KEY (`manga_id`) REFERENCES `manga_series` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_mt_tag` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `media_metadata`
--
ALTER TABLE `media_metadata`
  ADD CONSTRAINT `fk_meta_file` FOREIGN KEY (`file_id`) REFERENCES `file_nodes` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `movie_alternative_titles`
--
ALTER TABLE `movie_alternative_titles`
  ADD CONSTRAINT `fk_mat_movie` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `movie_credits`
--
ALTER TABLE `movie_credits`
  ADD CONSTRAINT `fk_mc_movie` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_mc_person` FOREIGN KEY (`person_id`) REFERENCES `persons` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `movie_episodes`
--
ALTER TABLE `movie_episodes`
  ADD CONSTRAINT `fk_me_movie` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `movie_progress`
--
ALTER TABLE `movie_progress`
  ADD CONSTRAINT `fk_mp_episode` FOREIGN KEY (`episode_id`) REFERENCES `movie_episodes` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_mp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `movie_studios`
--
ALTER TABLE `movie_studios`
  ADD CONSTRAINT `fk_movie_studios_movie` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`),
  ADD CONSTRAINT `fk_movie_studios_studio` FOREIGN KEY (`studio_id`) REFERENCES `studios` (`id`);

--
-- Ràng buộc cho bảng `movie_subtitles`
--
ALTER TABLE `movie_subtitles`
  ADD CONSTRAINT `fk_ms_episode` FOREIGN KEY (`episode_id`) REFERENCES `movie_episodes` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `movie_tags`
--
ALTER TABLE `movie_tags`
  ADD CONSTRAINT `fk_movie_tags_movie_link` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_movie_tags_tag_link` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `subject_folder_mappings`
--
ALTER TABLE `subject_folder_mappings`
  ADD CONSTRAINT `subject_folder_mappings_ibfk_1` FOREIGN KEY (`subject_id`) REFERENCES `content_subjects` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `subject_folder_mappings_ibfk_2` FOREIGN KEY (`volume_id`) REFERENCES `storage_volumes` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `subject_social_links`
--
ALTER TABLE `subject_social_links`
  ADD CONSTRAINT `fk_ssl_platform` FOREIGN KEY (`platform_id`) REFERENCES `social_platforms` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_ssl_subject` FOREIGN KEY (`subject_id`) REFERENCES `content_subjects` (`id`) ON DELETE CASCADE;

--
-- Ràng buộc cho bảng `user_roles`
--
ALTER TABLE `user_roles`
  ADD CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
